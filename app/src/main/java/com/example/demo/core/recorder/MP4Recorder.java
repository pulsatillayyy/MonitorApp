package com.example.demo.core.recorder;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import com.example.demo.core.audio.AudioEngine;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * MP4 录制器实现
 */
public class MP4Recorder implements IRecorderPipeline, AudioEngine.OnAudioDataListener {
    private static final String TAG = "MP4Recorder";
    private static final String VIDEO_MIME = "video/avc";
    private static final String AUDIO_MIME = "audio/mp4a-latm";

    private final Context context;
    private MediaMuxer mMuxer;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private Surface mInputSurface;

    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private long mFirstVideoPts = -1;
    private volatile boolean mMuxerStarted = false;

    private MediaCodec.BufferInfo mVideoBufferInfo;
    private MediaCodec.BufferInfo mAudioBufferInfo;
    
    private volatile boolean isRecording = false;
    private long mStartTimeNano = 0;

    private int mWidth = 1920;
    private int mHeight = 1080;
    
    private Uri mCurrentVideoUri;
    private ParcelFileDescriptor mPfd;

    private Thread mVideoDrainThread;

    public MP4Recorder(Context context, int width, int height) {
        this.context = context;
        this.mWidth = width;
        this.mHeight = height;
    }

    @Override
    public void start(String fileName) throws IOException {
        if (isRecording) return;
        
        // 使用 MediaStore 创建文件，确保相册可见
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CineMonitor");
            values.put(MediaStore.Video.Media.IS_PENDING, 1); // 标记为处理中
        }

        ContentResolver resolver = context.getContentResolver();
        mCurrentVideoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        
        if (mCurrentVideoUri == null) {
            throw new IOException("Failed to create MediaStore entry");
        }

        try {
            mPfd = resolver.openFileDescriptor(mCurrentVideoUri, "w");
            if (mPfd == null) throw new IOException("Failed to open file descriptor");
            
            FileDescriptor fd = mPfd.getFileDescriptor();
            mMuxer = new MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            // 清理
            if (mCurrentVideoUri != null) {
                resolver.delete(mCurrentVideoUri, null, null);
            }
            throw e;
        }

        mVideoBufferInfo = new MediaCodec.BufferInfo();
        mAudioBufferInfo = new MediaCodec.BufferInfo();

        prepareVideoEncoder();
        prepareAudioEncoder();

        mVideoEncoder.start();
        mAudioEncoder.start();
        
        isRecording = true;
        mStartTimeNano = System.nanoTime();
        
        startVideoDrainThread();
    }

    private void prepareVideoEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000); // 6Mbps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME);
        mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mVideoEncoder.createInputSurface();
    }

    private void prepareAudioEncoder() throws IOException {
        MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME, 44100, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME);
        mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    @Override
    public Surface getInputSurface() {
        return mInputSurface;
    }

    private void startVideoDrainThread() {
        mVideoDrainThread = new Thread(() -> {
            while (isRecording) {
                drainVideoEncoder(false);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        mVideoDrainThread.start();
    }

    private void stopVideoDrainThread() {
        if (mVideoDrainThread != null) {
            try {
                mVideoDrainThread.join();
                mVideoDrainThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void drainVideoEncoder(boolean endOfStream) {
        if (!isRecording && !endOfStream) return;
        if (endOfStream) {
            try {
                mVideoEncoder.signalEndOfInputStream();
            } catch (Exception e) {
                // Ignore
            }
        }
        drainEncoder(mVideoEncoder, mVideoBufferInfo, true);
    }

    @Override
    public void feedAudioData(byte[] data, int size) {
        // IRecorderPipeline 接口实现
        onAudioData(data, size);
    }

    @Override
    public void onAudioData(byte[] data, int size) {
        // AudioEngine.OnAudioDataListener 接口实现
        if (!isRecording) return;
        
        int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            inputBuffer.put(data, 0, size);
            
            long pts = (System.nanoTime() - mStartTimeNano) / 1000;
            mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, size, pts, 0);
        }
        
        drainEncoder(mAudioEncoder, mAudioBufferInfo, false);
    }

    private void drainEncoder(MediaCodec encoder, MediaCodec.BufferInfo bufferInfo, boolean isVideo) {
        int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
        while (outputBufferIndex >= 0) {
            Log.d(TAG,"video pts="+bufferInfo.presentationTimeUs + " isVideo =" + isVideo);
            ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
            
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0;
            }

            if (bufferInfo.size != 0) {
                if (isVideo) {
                    if (mFirstVideoPts < 0) {
                        mFirstVideoPts = bufferInfo.presentationTimeUs;
                    }
                    bufferInfo.presentationTimeUs -= mFirstVideoPts;
                }
                if (mMuxerStarted) {
                    outputBuffer.position(bufferInfo.offset);
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    int trackIndex = isVideo ? mVideoTrackIndex : mAudioTrackIndex;
                    mMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                }
            }

            encoder.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
        }

        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = encoder.getOutputFormat();
            if (isVideo) {
                mVideoTrackIndex = mMuxer.addTrack(newFormat);
            } else {
                mAudioTrackIndex = mMuxer.addTrack(newFormat);
            }
            
            if (mVideoTrackIndex != -1 && mAudioTrackIndex != -1 && !mMuxerStarted) {
                mMuxer.start();
                mMuxerStarted = true;
                Log.d(TAG, "MediaMuxer started");
            }
        }
    }

    @Override
    public void stop() {
        if (!isRecording) return;
        isRecording = false;
        
        stopVideoDrainThread();

        try {
            if (mVideoEncoder != null) {
                drainVideoEncoder(true);
                mVideoEncoder.stop();
                mVideoEncoder.release();
                mVideoEncoder = null;
            }
            if (mAudioEncoder != null) {
                mAudioEncoder.stop();
                mAudioEncoder.release();
                mAudioEncoder = null;
            }
            if (mMuxer != null) {
                if (mMuxerStarted) {
                    mMuxer.stop();
                }
                mMuxer.release();
                mMuxer = null;
            }
            
            if (mPfd != null) {
                mPfd.close();
                mPfd = null;
            }
            
            // 更新 MediaStore 状态，标记为完成
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mCurrentVideoUri != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
                context.getContentResolver().update(mCurrentVideoUri, values, null, null);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        mMuxerStarted = false;
        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;
    }

    @Override
    public boolean isRecording() {
        return isRecording;
    }
}

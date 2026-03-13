package com.example.demo.core.recorder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import com.example.demo.core.audio.AudioEngine;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * MP4 录制器实现
 * 1. 实现了 AudioEngine.OnAudioDataListener，接收音频 PCM 数据
 * 2. 实现了 IRecorderPipeline，提供视频 Input Surface
 */
public class MP4Recorder implements IRecorderPipeline, AudioEngine.OnAudioDataListener {
    private static final String TAG = "MP4Recorder";
    private static final String VIDEO_MIME = "video/avc";
    private static final String AUDIO_MIME = "audio/mp4a-latm";

    private MediaMuxer mMuxer;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private Surface mInputSurface;

    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private volatile boolean mMuxerStarted = false;

    private MediaCodec.BufferInfo mVideoBufferInfo;
    private MediaCodec.BufferInfo mAudioBufferInfo;
    
    private volatile boolean isRecording = false;
    private long mStartTimeNano = 0;

    private int mWidth = 1920;
    private int mHeight = 1080;

    public MP4Recorder(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    @Override
    public void start(String outputPath) throws IOException {
        if (isRecording) return;

        mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mVideoBufferInfo = new MediaCodec.BufferInfo();
        mAudioBufferInfo = new MediaCodec.BufferInfo();

        prepareVideoEncoder();
        prepareAudioEncoder();

        mVideoEncoder.start();
        mAudioEncoder.start();
        
        isRecording = true;
        mStartTimeNano = System.nanoTime();
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

    @Override
    public void drainVideoEncoder(boolean endOfStream) {
        if (!isRecording) return;
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
            ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
            
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                bufferInfo.size = 0;
            }

            if (bufferInfo.size != 0) {
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

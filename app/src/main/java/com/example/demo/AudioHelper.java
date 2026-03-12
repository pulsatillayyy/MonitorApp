package com.example.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 音频助手类，使用 AudioRecord API 捕获原始 PCM 音频数据。
 * 在单独的线程中读取音频数据，并使用 MediaCodec 编码为 AAC，最后用 MediaMuxer 封装为 MP4。
 */
public class AudioHelper {
    private static final String TAG = "AudioHelper";
    // 采样率：44100Hz 是目前唯一保证在所有 Android 设备上都有效的采样率
    private static final int SAMPLE_RATE = 44100;
    // 通道配置：单声道
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    // 音频格式：16位 PCM 编码
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    // 编码参数
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int BIT_RATE = 96000; // 96kbps

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private final Context context;
    
    // 编码与封装
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private int mTrackIndex = -1;
    private boolean mMuxerStarted = false;
    private MediaCodec.BufferInfo mBufferInfo;
    private long startTimeNano;
    
    private File currentFile;

    public AudioHelper(Context context) {
        this.context = context;
    }

    /**
     * 开始录音
     */
    public void startRecording() {
        if (isRecording) return;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "未获得录音权限");
            return;
        }

        // 获取最小缓冲区大小，确保缓冲区足够大以避免音频数据丢失
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "无效的音频参数");
            return;
        }

        try {
            // 准备编码器
            prepareEncoder();
            
            // 初始化 AudioRecord 对象
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                isRecording = true;
                startTimeNano = System.nanoTime();
                
                // 启动后台线程读取音频数据
                recordingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        readAudioData(minBufferSize);
                    }
                });
                recordingThread.start();
            } else {
                Log.e(TAG, "AudioRecord 初始化失败");
                stopEncoder();
            }
        } catch (IllegalArgumentException | IOException e) {
            e.printStackTrace();
            stopEncoder();
        }
    }
    
    private void prepareEncoder() throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();
        
        // 创建文件
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Audio_Cine_" + timeStamp + ".mp4";
        // 存放在外部存储的 Music 目录下，或者 App 私有目录
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        currentFile = new File(dir, fileName);
        
        // 配置 Format
        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        
        // 创建编码器
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoder.start();
        
        // 创建封装器
        mMuxer = new MediaMuxer(currentFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mMuxerStarted = false;
        mTrackIndex = -1;
    }

    private void readAudioData(int bufferSize) {
        byte[] data = new byte[bufferSize];
        while (isRecording) {
            // 从音频硬件读取数据到缓冲区
            int result = audioRecord.read(data, 0, bufferSize);
            if (result < 0) {
                Log.e(TAG, "音频读取错误: " + result);
            } else {
                // 编码数据
                encode(data, result);
            }
        }
        // 录音结束，发送 EOS
        encode(null, 0);
    }
    
    private void encode(byte[] pcmData, int length) {
        if (mEncoder == null) return;
        
        // --- 输入阶段 ---
        int inputBufferIndex = mEncoder.dequeueInputBuffer(0);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            
            if (pcmData != null && length > 0) {
                inputBuffer.put(pcmData, 0, length);
                // 计算 PTS: (当前时间 - 开始时间) / 1000 = 微秒
                long pts = (System.nanoTime() - startTimeNano) / 1000;
                mEncoder.queueInputBuffer(inputBufferIndex, 0, length, pts, 0);
            } else {
                // 发送 EOS
                mEncoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        }
        
        // --- 输出阶段 ---
        int outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufferIndex);
            
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                mBufferInfo.size = 0;
            }
            
            if (mBufferInfo.size != 0) {
                if (!mMuxerStarted) {
                    // 理论上应该在 INFO_OUTPUT_FORMAT_CHANGED 处理，但有时会先来数据
                    // 这里为了保险，不过通常 Format Changed 会先触发
                } else {
                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);
                    mMuxer.writeSampleData(mTrackIndex, outputBuffer, mBufferInfo);
                }
            }
            
            mEncoder.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
        }
        
        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (mMuxerStarted) {
                throw new RuntimeException("format changed twice");
            }
            MediaFormat newFormat = mEncoder.getOutputFormat();
            mTrackIndex = mMuxer.addTrack(newFormat);
            mMuxer.start();
            mMuxerStarted = true;
        }
    }
    
    private void stopEncoder() {
        if (mEncoder != null) {
            try {
                mEncoder.stop();
                mEncoder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mEncoder = null;
        }
        if (mMuxer != null) {
            try {
                if (mMuxerStarted) {
                    mMuxer.stop();
                }
                mMuxer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMuxer = null;
        }
        mMuxerStarted = false;
    }

    /**
     * 停止录音并释放资源
     */
    public void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            audioRecord = null;
        }
        if (recordingThread != null) {
            try {
                recordingThread.join(); // 等待录音线程结束
                recordingThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 停止编码器
        stopEncoder();
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public File getLastRecordedFile() {
        return currentFile;
    }
}

package com.example.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

/**
 * 音频助手类，使用 AudioRecord API 捕获原始 PCM 音频数据。
 * 在单独的线程中读取音频数据，以避免阻塞 UI 线程。
 */
public class AudioHelper {
    private static final String TAG = "AudioHelper";
    // 采样率：44100Hz 是目前唯一保证在所有 Android 设备上都有效的采样率
    private static final int SAMPLE_RATE = 44100;
    // 通道配置：单声道
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    // 音频格式：16位 PCM 编码
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private final Context context;

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
            // 初始化 AudioRecord 对象
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                isRecording = true;
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
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void readAudioData(int bufferSize) {
        byte[] data = new byte[bufferSize];
        while (isRecording) {
            // 从音频硬件读取数据到缓冲区
            int result = audioRecord.read(data, 0, bufferSize);
            if (result < 0) {
                Log.e(TAG, "音频读取错误: " + result);
            } else {
                // 在这里处理音频数据（例如，计算音量、FFT 频谱分析等）
                // 目前仅仅是读取并丢弃，作为数据获取的演示
            }
        }
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
    }
}

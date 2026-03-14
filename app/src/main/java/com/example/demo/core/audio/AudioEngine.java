package com.example.demo.core.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频采集引擎
 * 职责：
 * 1. 管理 AudioRecord 生命周期
 * 2. 采集 PCM 数据并通过回调分发
 */
public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private volatile boolean isRecording = false;
    private Thread recordingThread;
    private final Context context;
    
    private List<OnAudioDataListener> mAudioDataListeners = new ArrayList<>();

    public interface OnAudioDataListener {
        void onAudioData(byte[] data, int size);
    }

    public AudioEngine(Context context) {
        this.context = context;
    }
    
    public void addAudioDataListener(OnAudioDataListener listener) {
        if (!mAudioDataListeners.contains(listener)) {
            mAudioDataListeners.add(listener);
        }
    }

    public void removeAudioDataListener(OnAudioDataListener listener) {
        mAudioDataListeners.remove(listener);
    }
    
    // Deprecated: use addAudioDataListener
    public void setAudioDataListener(OnAudioDataListener listener) {
        mAudioDataListeners.clear();
        if (listener != null) {
            mAudioDataListeners.add(listener);
        }
    }

    public void start() {
        if (isRecording) return;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission not granted: RECORD_AUDIO");
            return;
        }

        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid audio parameters");
            return;
        }

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize);
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording();
                isRecording = true;
                recordingThread = new Thread(() -> readAudioData(minBufferSize));
                recordingThread.start();
            } else {
                Log.e(TAG, "AudioRecord initialization failed");
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void readAudioData(int bufferSize) {
        byte[] data = new byte[bufferSize];
        while (isRecording) {
            int result = audioRecord.read(data, 0, bufferSize);
            if (result < 0) {
                Log.e(TAG, "Audio read error: " + result);
            } else {
                for (OnAudioDataListener listener : mAudioDataListeners) {
                    listener.onAudioData(data, result);
                }
            }
        }
    }

    public void stop() {
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
                recordingThread.join();
                recordingThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public boolean isRecording() {
        return isRecording;
    }
}

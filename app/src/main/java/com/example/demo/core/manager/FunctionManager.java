package com.example.demo.core.manager;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.example.demo.core.audio.AudioEngine;
import com.example.demo.core.render.CineRenderer;
import com.example.demo.ui.view.AudioWaveformView;

/**
 * 功能管理器
 * 职责：
 * 1. 管理滤镜状态 (Normal/Mono/LUT)
 * 2. 管理辅助工具 (Histogram, Waveform)
 * 3. 协调 UI 和核心引擎
 */
public class FunctionManager implements AudioEngine.OnAudioDataListener {
    private final Context context;
    private final CineRenderer cineRenderer;
    private final AudioEngine audioEngine;
    private final AudioWaveformView waveformView;

    private boolean isMonoEnabled = false;
    private boolean isWaveformEnabled = false;
    private boolean isHistogramEnabled = false;

    public FunctionManager(Context context, CineRenderer cineRenderer, AudioEngine audioEngine, AudioWaveformView waveformView) {
        this.context = context;
        this.cineRenderer = cineRenderer;
        this.audioEngine = audioEngine;
        this.waveformView = waveformView;
        
        // Register listener for waveform data
        this.audioEngine.addAudioDataListener(this);
    }
    
    /**
     * 切换黑白滤镜
     * 逻辑：如果当前是 Mono，则切回 Normal；否则切为 Mono
     */
    public void toggleMonochrome() {
        if (isMonoEnabled) {
            cineRenderer.setFilter(CineRenderer.FilterType.NORMAL);
            isMonoEnabled = false;
        } else {
            cineRenderer.setFilter(CineRenderer.FilterType.MONOCHROME);
            isMonoEnabled = true;
        }
    }

    /**
     * 切换音频波形图
     */
    public void toggleWaveform() {
        if (isWaveformEnabled) {
            waveformView.setVisibility(View.GONE);
            isWaveformEnabled = false;
        } else {
            waveformView.setVisibility(View.VISIBLE);
            isWaveformEnabled = true;
        }
    }

    /**
     * 切换直方图 (暂未实现具体绘制逻辑，仅控制 UI 状态)
     */
    public void toggleHistogram() {
        isHistogramEnabled = !isHistogramEnabled;
        if (isHistogramEnabled) {
            Toast.makeText(context, "Histogram: ON (Placeholder)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Histogram: OFF", Toast.LENGTH_SHORT).show();
        }
    }
    
    public boolean isMonoEnabled() {
        return isMonoEnabled;
    }

    @Override
    public void onAudioData(byte[] data, int size) {
        if (isWaveformEnabled && waveformView != null) {
            waveformView.updateAudioData(data);
        }
    }
}

package com.example.demo.core.recorder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 录制管线接口
 * 定义录制器的生命周期与数据输入
 */
public interface IRecorderPipeline {
    void start(String outputPath) throws IOException;
    void stop();
    boolean isRecording();
    
    // 视频输入（可能是 Surface 或 buffer）
    Surface getInputSurface();
    
    // 音频输入
    void feedAudioData(byte[] data, int size);
    
    // 视频编码器排空（用于 OpenGL 录制模式）
    void drainVideoEncoder(boolean endOfStream);
}

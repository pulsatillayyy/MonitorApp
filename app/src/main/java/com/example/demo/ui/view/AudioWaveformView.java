package com.example.demo.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Audio Waveform View
 * Draws a simple line representation of audio amplitude over time.
 */
public class AudioWaveformView extends View {
    private Paint mPaint;
    private Path mPath;
    private float[] mPoints;
    private byte[] mRawData;
    
    public AudioWaveformView(Context context) {
        super(context);
        init();
    }

    public AudioWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioWaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setColor(Color.GREEN);
        mPaint.setStrokeWidth(2f);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);
        
        mPath = new Path();
    }

    /**
     * Update the view with new audio PCM data (16-bit mono expected)
     * @param data Raw PCM bytes
     */
    public void updateAudioData(byte[] data) {
        // Only invalidate if we have data and visible
        if (getVisibility() != View.VISIBLE) return;
        
        this.mRawData = data;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (mRawData == null || mRawData.length == 0) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        int centerY = height / 2;

        mPath.reset();
        mPath.moveTo(0, centerY);
        
        // We have 16-bit PCM (2 bytes per sample)
        int totalSamples = mRawData.length / 2;
        if (totalSamples == 0) return;
        
        // Calculate step size to fit all samples into view width
        // Or render a window. Let's fit for now.
        // If buffer is large (e.g. 1024 samples), and width is 1080px, step ~ 1
        float stepX = (float) width / totalSamples;
        
        for (int i = 0; i < totalSamples; i++) {
            int index = i * 2;
            if (index + 1 >= mRawData.length) break;
            
            // Little-endian 16-bit PCM
            short sample = (short) ((mRawData[index] & 0xFF) | (mRawData[index + 1] << 8));
            
            float x = i * stepX;
            // Scale: sample / 32768f * (height / 2)
            // Invert Y because canvas Y grows downwards
            float y = centerY - (sample / 32768f * (height / 2));
            
            mPath.lineTo(x, y);
        }
        
        canvas.drawPath(mPath, mPaint);
    }
}

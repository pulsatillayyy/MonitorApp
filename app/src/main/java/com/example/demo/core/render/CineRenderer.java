package com.example.demo.core.render;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.example.demo.R;
import com.example.demo.core.render.filters.ShaderUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 电影感渲染器
 * 职责：
 * 1. 管理 OES 纹理与 Camera 数据接收
 * 2. 执行 OpenGL 滤镜渲染 (Normal/Monochrome)
 * 3. 负责将渲染结果分发给屏幕
 * (不再负责录制，录制交由 CameraEngine 原生处理)
 */
public class CineRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "CineRenderer";

    private final Context context;
    private final GLSurfaceView glSurfaceView;
    private int textureId = -1;
    private SurfaceTexture surfaceTexture;
    private boolean updateSurface = false;
    
    // 着色器程序
    private int programNormal;
    private int programMono;
    private int currentProgram;

    // 几何数据
    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    
    private int mWidth;
    private int mHeight;

    private static final float[] VERTEX_COORDS = {
            -1.0f, -1.0f, 1.0f, -1.0f,
            -1.0f,  1.0f, 1.0f,  1.0f
    };

    private static final float[] TEXTURE_COORDS = {
            0.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 0.0f
    };

    private OnSurfaceTextureCreatedListener surfaceTextureListener;

    public interface OnSurfaceTextureCreatedListener {
        void onSurfaceTextureCreated(SurfaceTexture surfaceTexture);
    }

    public enum FilterType {
        NORMAL, MONOCHROME, HISTOGRAM, WAVEFORM
    }

    private FilterType currentFilterType = FilterType.NORMAL;

    public CineRenderer(Context context, GLSurfaceView glSurfaceView, OnSurfaceTextureCreatedListener listener) {
        this.context = context;
        this.glSurfaceView = glSurfaceView;
        this.surfaceTextureListener = listener;

        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(VERTEX_COORDS);
        vertexBuffer.position(0);

        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEXTURE_COORDS);
        textureBuffer.position(0);
    }
    
    public void setFilter(FilterType type) {
        this.currentFilterType = type;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(this);

        programNormal = ShaderUtils.createProgram(context, R.raw.vertex_shader, R.raw.fragment_shader_oes);
        programMono = ShaderUtils.createProgram(context, R.raw.vertex_shader, R.raw.fragment_shader_mono);
        currentProgram = programNormal;

        if (surfaceTextureListener != null) {
            surfaceTextureListener.onSurfaceTextureCreated(surfaceTexture);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {
            if (updateSurface) {
                surfaceTexture.updateTexImage();
                updateSurface = false;
            }
        }

        GLES20.glViewport(0, 0, mWidth, mHeight);
        drawFrame();
    }
    
    private void drawFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (currentFilterType == FilterType.MONOCHROME) {
            currentProgram = programMono;
        } else {
            currentProgram = programNormal;
        }

        GLES20.glUseProgram(currentProgram);

        int aPosition = GLES20.glGetAttribLocation(currentProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        int aTexCoord = GLES20.glGetAttribLocation(currentProgram, "aTexCoord");
        GLES20.glEnableVertexAttribArray(aTexCoord);
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        int uTexture = GLES20.glGetUniformLocation(currentProgram, "uTexture");
        GLES20.glUniform1i(uTexture, 0);

        if (currentFilterType == FilterType.MONOCHROME) {
            int uColor = GLES20.glGetUniformLocation(currentProgram, "uColor");
            GLES20.glUniform3f(uColor, 1.0f, 1.0f, 1.0f);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aTexCoord);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (this) {
            updateSurface = true;
        }
        glSurfaceView.requestRender();
    }
}

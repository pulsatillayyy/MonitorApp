package com.example.demo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 负责 OpenGL 渲染的类，实现了 GLSurfaceView.Renderer 接口。
 * 它负责管理 OpenGL 纹理（用于接收相机数据）和执行绘制操作。
 */
public class CameraRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "CameraRenderer";

    private final Context context;
    private final GLSurfaceView glSurfaceView;
    private int textureId = -1; // OpenGL 纹理 ID
    private SurfaceTexture surfaceTexture; // 用于接收相机数据流的 SurfaceTexture
    private boolean updateSurface = false; // 标记是否有新帧需要更新

    // 着色器程序 ID
    private int programNormal; // 普通预览着色器
    private int programMono;   // 单色滤镜着色器
    private int currentProgram; // 当前使用的着色器

    // 几何数据缓冲区
    private FloatBuffer vertexBuffer; // 顶点坐标
    private FloatBuffer textureBuffer; // 纹理坐标

    // 顶点坐标：定义了一个覆盖全屏的矩形（由两个三角形组成）
    private static final float[] VERTEX_COORDS = {
            -1.0f, -1.0f,   // 左下
             1.0f, -1.0f,   // 右下
            -1.0f,  1.0f,   // 左上
             1.0f,  1.0f    // 右上
    };

    // 纹理坐标：定义了如何将纹理映射到顶点上
    // 注意：Camera2 API 的图像方向可能需要调整纹理坐标（旋转/翻转）。
    // 这里使用标准坐标，如果图像倒置或旋转，需要修改此处或使用矩阵变换。
    private static final float[] TEXTURE_COORDS = {
            0.0f, 1.0f,     // 左下
            1.0f, 1.0f,     // 右下
            0.0f, 0.0f,     // 左上
            1.0f, 0.0f      // 右上
    };

    private OnSurfaceTextureCreatedListener surfaceTextureListener;

    // 回调接口，用于通知 Activity SurfaceTexture 已准备好
    public interface OnSurfaceTextureCreatedListener {
        void onSurfaceTextureCreated(SurfaceTexture surfaceTexture);
    }

    // 滤镜类型枚举
    public enum FilterType {
        NORMAL,     // 正常
        MONOCHROME, // 单色
        HISTOGRAM,  // 直方图 (占位符)
        WAVEFORM    // 波形图 (占位符)
    }

    private FilterType currentFilterType = FilterType.NORMAL;

    public CameraRenderer(Context context, GLSurfaceView glSurfaceView, OnSurfaceTextureCreatedListener listener) {
        this.context = context;
        this.glSurfaceView = glSurfaceView;
        this.surfaceTextureListener = listener;

        // 初始化顶点缓冲区
        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(VERTEX_COORDS);
        vertexBuffer.position(0);

        // 初始化纹理缓冲区
        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(TEXTURE_COORDS);
        textureBuffer.position(0);
    }

    public void setFilter(FilterType type) {
        this.currentFilterType = type;
        // 切换操作将在 onDrawFrame 中进行，以确保在 GL 线程执行
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // 创建 OES 外部纹理
        // GL_TEXTURE_EXTERNAL_OES 是专门用于接收相机预览流的纹理目标
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        // 设置纹理过滤参数
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // 使用生成的纹理 ID 创建 SurfaceTexture
        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(this); // 监听新帧到达

        // 加载并编译着色器程序
        programNormal = ShaderManager.createProgram(context, R.raw.vertex_shader, R.raw.fragment_shader_oes);
        programMono = ShaderManager.createProgram(context, R.raw.vertex_shader, R.raw.fragment_shader_mono);

        currentProgram = programNormal;

        // 通知 Activity SurfaceTexture 已就绪，可以开启相机了
        if (surfaceTextureListener != null) {
            surfaceTextureListener.onSurfaceTextureCreated(surfaceTexture);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (this) {
            if (updateSurface) {
                // 更新纹理图像到最新的相机帧
                // 必须在 GL 线程调用
                surfaceTexture.updateTexImage();
                updateSurface = false;
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 根据当前滤镜类型选择着色器程序
        if (currentFilterType == FilterType.MONOCHROME) {
            currentProgram = programMono;
        } else {
            currentProgram = programNormal;
        }

        GLES20.glUseProgram(currentProgram);

        // 绑定顶点坐标
        int aPosition = GLES20.glGetAttribLocation(currentProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // 绑定纹理坐标
        int aTexCoord = GLES20.glGetAttribLocation(currentProgram, "aTexCoord");
        GLES20.glEnableVertexAttribArray(aTexCoord);
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        // 绑定纹理单元
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        int uTexture = GLES20.glGetUniformLocation(currentProgram, "uTexture");
        GLES20.glUniform1i(uTexture, 0);

        // 设置特定着色器的 Uniform 变量
        if (currentFilterType == FilterType.MONOCHROME) {
            int uColor = GLES20.glGetUniformLocation(currentProgram, "uColor");
            // 设置单色滤镜的颜色权重，这里设为 (1,1,1) 即纯灰度
            GLES20.glUniform3f(uColor, 1.0f, 1.0f, 1.0f);
        }

        // 绘制矩形 (两个三角形)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // 禁用属性数组
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aTexCoord);
    }

    /**
     * 当 SurfaceTexture 有新帧可用时调用
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (this) {
            updateSurface = true;
        }
        // 请求 GLSurfaceView 重新渲染
        glSurfaceView.requestRender();
    }
}

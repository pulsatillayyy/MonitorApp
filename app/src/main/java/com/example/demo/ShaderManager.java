package com.example.demo;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * OpenGL 着色器管理工具类。
 * 负责读取 GLSL 源代码，编译顶点和片元着色器，并链接成 OpenGL 程序。
 */
public class ShaderManager {
    private static final String TAG = "ShaderManager";

    /**
     * 从资源文件创建 OpenGL 程序
     * @param context 上下文
     * @param vertexResId 顶点着色器资源 ID
     * @param fragmentResId 片元着色器资源 ID
     * @return 链接成功的程序 ID，如果失败则返回 0
     */
    public static int createProgram(Context context, int vertexResId, int fragmentResId) {
        String vertexSource = readShaderFromResource(context, vertexResId);
        String fragmentSource = readShaderFromResource(context, fragmentResId);

        return createProgram(vertexSource, fragmentSource);
    }

    /**
     * 从字符串源码创建 OpenGL 程序
     */
    public static int createProgram(String vertexSource, String fragmentSource) {
        // 1. 加载并编译顶点着色器
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;

        // 2. 加载并编译片元着色器
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) return 0;

        // 3. 创建 OpenGL 程序对象
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            // 4. 将着色器附加到程序上
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            // 5. 链接程序
            GLES20.glLinkProgram(program);

            // 6. 检查链接状态
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    /**
     * 加载并编译单个着色器
     * @param shaderType GLES20.GL_VERTEX_SHADER 或 GLES20.GL_FRAGMENT_SHADER
     * @param source GLSL 源代码
     */
    private static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);

            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    /**
     * 辅助方法：从 raw 资源读取文本内容
     */
    private static String readShaderFromResource(Context context, int resourceId) {
        StringBuilder body = new StringBuilder();
        try {
            InputStream inputStream = context.getResources().openRawResource(resourceId);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String nextLine;
            while ((nextLine = bufferedReader.readLine()) != null) {
                body.append(nextLine);
                body.append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not read shader resource: " + resourceId, e);
        }
        return body.toString();
    }
}

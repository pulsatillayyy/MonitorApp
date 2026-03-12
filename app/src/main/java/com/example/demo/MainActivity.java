package com.example.demo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.demo.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 主界面 Activity，负责管理生命周期、权限请求和 UI 交互。
 * 实现了 CameraRenderer.OnSurfaceTextureCreatedListener 接口，
 * 当 OpenGL 纹理创建完成后，启动相机预览。
 */
public class MainActivity extends AppCompatActivity implements CameraRenderer.OnSurfaceTextureCreatedListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    // 需要请求的权限：相机和录音
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private GLSurfaceView glSurfaceView;
    private CameraRenderer cameraRenderer;
    private CameraHelper cameraHelper;
    private AudioHelper audioHelper;
    private SurfaceTexture surfaceTexture; // 由 Renderer 创建的 SurfaceTexture，用于接收相机数据
    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        // 初始化 GLSurfaceView
//        glSurfaceView = findViewById(R.id.gl_surface_view);
        glSurfaceView = mBinding.glSurfaceView;
        glSurfaceView.setEGLContextClientVersion(2); // 使用 OpenGL ES 2.0

        // 初始化渲染器并设置给 GLSurfaceView
        cameraRenderer = new CameraRenderer(this, glSurfaceView, this);
        glSurfaceView.setRenderer(cameraRenderer);
        // 设置渲染模式为脏模式（按需渲染），当有新帧时手动请求渲染
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // 初始化相机和音频助手类
        cameraHelper = new CameraHelper(this);
        audioHelper = new AudioHelper(this);

        setupButtons();

        // 检查并请求权限
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private void setupButtons() {
        mBinding.btnHistogram.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 目前切换回普通模式，因为直方图绘制尚未完全实现
                Toast.makeText(MainActivity.this, "直方图模式 (尚未完全实现)", Toast.LENGTH_SHORT).show();
                cameraRenderer.setFilter(CameraRenderer.FilterType.NORMAL);
                glSurfaceView.requestRender();
            }
        });

        mBinding.btnWaveform.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 目前切换回普通模式，因为波形图绘制尚未完全实现
                Toast.makeText(MainActivity.this, "波形图模式 (尚未完全实现)", Toast.LENGTH_SHORT).show();
                cameraRenderer.setFilter(CameraRenderer.FilterType.NORMAL);
                glSurfaceView.requestRender();
            }
        });

        mBinding.btnMonochrome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 切换到单色（黑白）滤镜模式
                Toast.makeText(MainActivity.this, "单色滤镜模式", Toast.LENGTH_SHORT).show();
                cameraRenderer.setFilter(CameraRenderer.FilterType.MONOCHROME);
                glSurfaceView.requestRender();
            }
        });
        
        mBinding.btnRecordAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioHelper.isRecording()) {
                    audioHelper.stopRecording();
                    mBinding.btnRecordAudio.setText("Start Record");
                    File file = audioHelper.getLastRecordedFile();
                    if (file != null) {
                        Toast.makeText(MainActivity.this, "Saved: " + file.getName(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (hasPermissions()) {
                        audioHelper.startRecording();
                        mBinding.btnRecordAudio.setText("Stop Record");
                    } else {
                        Toast.makeText(MainActivity.this, "Need Audio Permission", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        mBinding.btnViewRecords.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRecordListDialog();
            }
        });
    }

    private void showRecordListDialog() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (dir == null || !dir.exists()) {
            Toast.makeText(this, "No records found", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("Audio_Cine_") && name.endsWith(".mp4");
            }
        });

        if (files == null || files.length == 0) {
            Toast.makeText(this, "No records found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Sort by last modified (newest first)
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Long.compare(o2.lastModified(), o1.lastModified());
            }
        });

        final String[] fileNames = new String[files.length];
        final File[] finalFiles = files;
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Recorded Files");
        builder.setItems(fileNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                playAudioFile(finalFiles[which]);
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void playAudioFile(File file) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
            Toast.makeText(this, "Playing: " + file.getName(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Play failed", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasPermissions() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasPermissions()) {
                // 权限已授予，如果 SurfaceTexture 已就绪，则启动相机
                if (surfaceTexture != null) {
                    cameraHelper.startCamera(surfaceTexture);
                }
            } else {
                Toast.makeText(this, "需要相机和录音权限才能运行", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * 当 OpenGL 纹理创建完成后的回调。
     * 必须在主线程启动相机操作。
     */
    @Override
    public void onSurfaceTextureCreated(final SurfaceTexture st) {
        this.surfaceTexture = st;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (hasPermissions()) {
                    cameraHelper.startCamera(surfaceTexture);
                    // 不再自动开始录音，由用户手动控制
                    // audioHelper.startRecording();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause(); // 暂停 GL 渲染线程
        cameraHelper.stopCamera(); // 释放相机资源
        if (audioHelper.isRecording()) {
            audioHelper.stopRecording(); // 停止录音
            mBinding.btnRecordAudio.setText("Start Record");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume(); // 恢复 GL 渲染线程
        if (hasPermissions() && surfaceTexture != null) {
            cameraHelper.startCamera(surfaceTexture); // 重新打开相机
            // 录音需手动开始
        }
    }
}

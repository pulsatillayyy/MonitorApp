package com.example.demo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.demo.core.audio.AudioEngine;
import com.example.demo.core.camera.CameraEngine;
import com.example.demo.core.recorder.IRecorderPipeline;
import com.example.demo.core.recorder.MP4Recorder;
import com.example.demo.core.render.CineRenderer;
import com.example.demo.databinding.ActivityMainBinding;
import com.example.demo.ui.settings.SettingsManager;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/**
 * 主界面 Activity
 * 职责：
 * 1. 组装各个核心模块 (Camera, Audio, Render, Recorder)
 * 2. 处理 UI 事件与生命周期
 * 3. 处理权限
 */
public class MainActivity extends AppCompatActivity implements CineRenderer.OnSurfaceTextureCreatedListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private GLSurfaceView glSurfaceView;
    private CineRenderer cineRenderer;
    private CameraEngine cameraEngine;
    private AudioEngine audioEngine;
    private IRecorderPipeline recorderPipeline;
    private SettingsManager settingsManager;
    
    private SurfaceTexture surfaceTexture; 
    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        settingsManager = new SettingsManager(this);

        glSurfaceView = mBinding.glSurfaceView;
        glSurfaceView.setEGLContextClientVersion(2);

        cineRenderer = new CineRenderer(this, glSurfaceView, this);
        glSurfaceView.setRenderer(cineRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        cameraEngine = new CameraEngine(this);
        audioEngine = new AudioEngine(this);
        
        // 默认初始化录制器
        recorderPipeline = new MP4Recorder(1920, 1080);
        updateRecorderConnections();

        setupButtons();

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }
    
    private void updateRecorderConnections() {
        // 连接音频：AudioEngine -> Recorder
        if (recorderPipeline instanceof AudioEngine.OnAudioDataListener) {
            audioEngine.setAudioDataListener((AudioEngine.OnAudioDataListener) recorderPipeline);
        }
        
        if (settingsManager.isRecordWithLut()) {
            // 带 LUT 录制：Camera -> GL -> Encoder (InputSurface)
            cineRenderer.setRecorder(recorderPipeline);
            cameraEngine.setRecordSurface(null);
        } else {
            // 原始流录制：Camera -> Encoder (InputSurface)
            cineRenderer.setRecorder(null);
            cameraEngine.setRecordSurface(recorderPipeline.getInputSurface());
        }
    }

    private void setupButtons() {
        mBinding.btnHistogram.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Histogram (Coming Soon)", Toast.LENGTH_SHORT).show();
                cineRenderer.setFilter(CineRenderer.FilterType.NORMAL);
                glSurfaceView.requestRender();
            }
        });

        mBinding.btnWaveform.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Waveform (Coming Soon)", Toast.LENGTH_SHORT).show();
                cineRenderer.setFilter(CineRenderer.FilterType.NORMAL);
                glSurfaceView.requestRender();
            }
        });

        mBinding.btnMonochrome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Monochrome Mode", Toast.LENGTH_SHORT).show();
                cineRenderer.setFilter(CineRenderer.FilterType.MONOCHROME);
                glSurfaceView.requestRender();
            }
        });
        
        mBinding.btnRecordVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recorderPipeline.isRecording()) {
                    stopRecording();
                } else {
                    startRecording();
                }
            }
        });
        
        mBinding.btnViewRecords.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRecordListDialog();
            }
        });
        
        mBinding.btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });
    }
    
    private void showSettingsDialog() {
        boolean current = settingsManager.isRecordWithLut();
        String[] items = {"Record with LUT (Filter)"};
        boolean[] checked = {current};
        
        new AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                    settingsManager.setRecordWithLut(isChecked);
                }
            })
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (recorderPipeline.isRecording()) {
                        stopRecording();
                    }
                    
                    recorderPipeline = new MP4Recorder(1920, 1080);
                    updateRecorderConnections();
                    
                    if (surfaceTexture != null) {
                        cameraEngine.stop();
                        cameraEngine.start(surfaceTexture);
                    }
                    
                    Toast.makeText(MainActivity.this, "Settings Applied", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }
    
    private void startRecording() {
        if (!hasPermissions()) {
            Toast.makeText(this, "Need permissions", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String prefix = settingsManager.isRecordWithLut() ? "CineLUT_" : "CineRaw_";
        String fileName = prefix + timeStamp + ".mp4";
        File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        
        try {
            recorderPipeline.start(file.getAbsolutePath());
            
            if (!audioEngine.isRecording()) {
                audioEngine.start();
            }
            
            mBinding.btnRecordVideo.setText("Stop REC");
            Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Start failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopRecording() {
        recorderPipeline.stop();
        mBinding.btnRecordVideo.setText("REC Video");
        Toast.makeText(this, "Recording Saved", Toast.LENGTH_SHORT).show();
    }
    
    private void showRecordListDialog() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (dir == null || !dir.exists()) {
            Toast.makeText(this, "No records found", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return (name.startsWith("CineLUT_") || name.startsWith("CineRaw_")) && name.endsWith(".mp4");
            }
        });

        if (files == null || files.length == 0) {
            Toast.makeText(this, "No records found", Toast.LENGTH_SHORT).show();
            return;
        }

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
        builder.setTitle("Recorded Videos");
        builder.setItems(fileNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                playVideoFile(finalFiles[which]);
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void playVideoFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        intent.setDataAndType(uri, "video/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No video player found", Toast.LENGTH_SHORT).show();
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
                if (surfaceTexture != null) {
                    cameraEngine.start(surfaceTexture);
                    audioEngine.start();
                }
            } else {
                Toast.makeText(this, "Need permissions", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onSurfaceTextureCreated(final SurfaceTexture st) {
        this.surfaceTexture = st;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (hasPermissions()) {
                    cameraEngine.start(surfaceTexture);
                    audioEngine.start();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        cameraEngine.stop();
        audioEngine.stop();
        if (recorderPipeline.isRecording()) {
            stopRecording();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        glSurfaceView.onResume();
        if (hasPermissions() && surfaceTexture != null) {
            cameraEngine.start(surfaceTexture);
            audioEngine.start();
        }
    }
}

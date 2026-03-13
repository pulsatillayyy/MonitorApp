package com.example.demo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.demo.core.audio.AudioEngine;
import com.example.demo.core.camera.CameraEngine;
import com.example.demo.core.recorder.IRecorderPipeline;
import com.example.demo.core.recorder.MP4Recorder;
import com.example.demo.core.render.CineRenderer;
import com.example.demo.databinding.ActivityMainBinding;
import com.example.demo.ui.settings.SettingsManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
        recorderPipeline = new MP4Recorder(this, 1920, 1080);
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
        
        cineRenderer.setFilter(CineRenderer.FilterType.NORMAL); // 确保预览是正常的（可选）
        cameraEngine.setRecordSurface(recorderPipeline.getInputSurface());
    }

    private void setupButtons() {
        mBinding.btnHistogram.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Histogram (Coming Soon)", Toast.LENGTH_SHORT).show();
            cineRenderer.setFilter(CineRenderer.FilterType.NORMAL);
            glSurfaceView.requestRender();
        });

        mBinding.btnWaveform.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Waveform (Coming Soon)", Toast.LENGTH_SHORT).show();
            cineRenderer.setFilter(CineRenderer.FilterType.NORMAL);
            glSurfaceView.requestRender();
        });

        mBinding.btnMonochrome.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Monochrome Mode", Toast.LENGTH_SHORT).show();
            cineRenderer.setFilter(CineRenderer.FilterType.MONOCHROME);
            glSurfaceView.requestRender();
        });

        
        mBinding.btnRecordVideo.setOnClickListener(v -> {
            if (recorderPipeline.isRecording()) {
                stopRecording();
            } else {
                startRecording();
            }
        });
        
        mBinding.btnViewRecords.setOnClickListener(v -> showRecordListDialog());
        
        // 移除 Settings 按钮的监听或使其无效，因为现在没有可配置项了
        mBinding.btnSettings.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Settings are disabled in Raw Recording mode", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void showSettingsDialog() {
        boolean current = settingsManager.isRecordWithLut();
        String[] items = {"Record with LUT (Filter)"};
        boolean[] checked = {current};
        
        new AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> settingsManager.setRecordWithLut(isChecked))
            .setPositiveButton("OK", (dialog, which) -> {
                if (recorderPipeline.isRecording()) {
                    stopRecording();
                }
                
                recorderPipeline = new MP4Recorder(this, 1920, 1080);
                updateRecorderConnections();
                
                if (surfaceTexture != null) {
                    cameraEngine.stop();
                    cameraEngine.start(surfaceTexture);
                }
                
                Toast.makeText(MainActivity.this, "Settings Applied", Toast.LENGTH_SHORT).show();
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
        
        try {
            recorderPipeline.start(fileName);
            
            // 重新配置 CameraEngine 以包含录制 Surface
            cameraEngine.setRecordSurface(recorderPipeline.getInputSurface());
            cameraEngine.restartSession();

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
        // 先断开 CameraEngine 的连接并重启会话，停止向 Surface 发送数据
        cameraEngine.setRecordSurface(null);
        cameraEngine.restartSession();
        
        recorderPipeline.stop();
        mBinding.btnRecordVideo.setText("REC Video");
        Toast.makeText(this, "Recording Saved", Toast.LENGTH_SHORT).show();
    }
    
    private void showRecordListDialog() {
        List<VideoItem> videoList = new ArrayList<>();
        
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[] {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        };
        String selection = MediaStore.Video.Media.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = new String[] { "Cine%" };
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(collection, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    videoList.add(new VideoItem(id, name, contentUri));
                }
            }
        }

        if (videoList.isEmpty()) {
            Toast.makeText(this, "No records found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[videoList.size()];
        for (int i = 0; i < videoList.size(); i++) {
            items[i] = videoList.get(i).name;
        }

        new AlertDialog.Builder(this)
            .setTitle("Recorded Videos")
            .setItems(items, (dialog, which) -> showVideoOptionsDialog(videoList.get(which)))
            .setNegativeButton("Close", null)
            .show();
    }

    private static class VideoItem {
        long id;
        String name;
        Uri uri;
        VideoItem(long id, String name, Uri uri) {
            this.id = id;
            this.name = name;
            this.uri = uri;
        }
    }
    
    private void showVideoOptionsDialog(final VideoItem item) {
        String[] options = {"Play", "Delete"};
        new AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    playVideoUri(item.uri);
                } else {
                    deleteVideo(item);
                }
            })
            .show();
    }

    private void deleteVideo(VideoItem item) {
        try {
            int rows = getContentResolver().delete(item.uri, null, null);
            if (rows > 0) {
                Toast.makeText(this, "Deleted: " + item.name, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error deleting file", Toast.LENGTH_SHORT).show();
        }
    }

    private void playVideoUri(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
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
        runOnUiThread(() -> {
            if (hasPermissions()) {
                cameraEngine.start(surfaceTexture);
                audioEngine.start();
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

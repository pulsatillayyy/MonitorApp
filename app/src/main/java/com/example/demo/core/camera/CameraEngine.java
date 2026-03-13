package com.example.demo.core.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心相机引擎
 * 职责：
 * 1. 管理 Camera2 生命周期 (Open/Close)
 * 2. 管理 CaptureSession (Preview/Record)
 * 3. 支持动态配置输出 Surface (Preview Only vs Preview + Record)
 */
public class CameraEngine {
    private static final String TAG = "CameraEngine";
    private final Context context;
    
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    
    // 输出目标
    private SurfaceTexture previewSurfaceTexture;
    private Surface recordSurface; // 可选：用于直接录制原始流的 Surface

    public CameraEngine(Context context) {
        this.context = context;
    }
    
    /**
     * 设置录制用的 Surface (MediaCodec Input Surface)
     * @param surface 如果为 null，则只进行预览；如果不为 null，则同时输出到该 Surface
     */
    public void setRecordSurface(Surface surface) {
        this.recordSurface = surface;
    }

    public void restartSession() {
        if (cameraDevice != null) {
            createCaptureSession();
        }
    }

    public void start(SurfaceTexture surfaceTexture) {
        this.previewSurfaceTexture = surfaceTexture;
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission not granted: CAMERA");
            return;
        }

        startBackgroundThread();

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // 默认选择第一个相机（通常是后置）
            // 生产环境应根据 LENS_FACING 筛选
            String cameraId = manager.getCameraIdList()[0]; 
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCaptureSession() {
        if (cameraDevice == null || previewSurfaceTexture == null) return;
        
        // Ensure previous session is closed
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }

        try {
            // 设置预览缓冲大小，应根据 StreamConfigurationMap 选择最佳尺寸
            previewSurfaceTexture.setDefaultBufferSize(1920, 1080);
            Surface previewSurface = new Surface(previewSurfaceTexture);
            
            List<Surface> targets = new ArrayList<>();
            targets.add(previewSurface);
            
            // 策略：如果有录制 Surface，则配置为双路输出
            if (recordSurface != null) {
                targets.add(recordSurface);
            }

            // 构建请求：TEMPLATE_RECORD 适用于录像，也兼容预览
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequestBuilder.addTarget(previewSurface);
            
            if (recordSurface != null) {
                captureRequestBuilder.addTarget(recordSurface);
            }

            cameraDevice.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        // 开启连续视频对焦
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Camera configuration failed");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraEngineThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

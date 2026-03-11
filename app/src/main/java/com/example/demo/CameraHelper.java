package com.example.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.Collections;

/**
 * 相机助手类，封装了 Camera2 API 的复杂性。
 * 负责打开相机、配置会话以及将预览数据发送到 SurfaceTexture。
 */
public class CameraHelper {
    private static final String TAG = "CameraHelper";
    private final Context context;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    public CameraHelper(Context context) {
        this.context = context;
    }

    /**
     * 启动相机预览。
     * @param surfaceTexture 从 OpenGL 渲染器传递过来的 SurfaceTexture，用于接收预览帧。
     */
    public void startCamera(SurfaceTexture surfaceTexture) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "未获得相机权限");
            return;
        }

        startBackgroundThread(); // 启动后台线程处理相机操作，避免阻塞主线程

        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            // 获取可用相机列表
            String cameraId = manager.getCameraIdList()[0]; // 默认使用后置摄像头（通常索引为 0）
            
            // 为了简化，我们直接取第一个相机。
            // 在实际应用中，应该检查相机朝向（LENS_FACING_BACK）和功能支持情况。

            // 打开相机
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCameraPreviewSession(surfaceTexture); // 相机打开后，创建预览会话
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

    private void createCameraPreviewSession(SurfaceTexture surfaceTexture) {
        try {
            // 设置 SurfaceTexture 的默认缓冲区大小
            // 理想情况下应该根据相机的支持分辨率进行匹配选择
            // 这里为了演示简单，硬编码为 1920x1080 (HD)
            surfaceTexture.setDefaultBufferSize(1920, 1080);

            // 从 SurfaceTexture 创建 Surface 对象，这是 Camera2 API 需要的目标
            Surface surface = new Surface(surfaceTexture);

            // 创建预览请求构建器
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface); // 将 Surface 添加为预览目标

            // 创建捕获会话
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    captureSession = session;
                    try {
                        // 设置自动对焦模式为连续拍照对焦
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        // 开始重复请求预览帧
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "相机配置失败");
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止相机并释放资源
     */
    public void stopCamera() {
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
        backgroundThread = new HandlerThread("CameraBackground");
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

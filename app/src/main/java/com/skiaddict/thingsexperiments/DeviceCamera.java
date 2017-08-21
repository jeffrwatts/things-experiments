package com.skiaddict.thingsexperiments;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

/**
 * Created by jewatts on 7/27/17.
 */

public class DeviceCamera {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;
    private static final int MAX_IMAGES = 1;

    private static DeviceCamera deviceCameraInstance = null;

    private ImageReader imageReader;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;

    public static DeviceCamera getInstance() {
        if (null == deviceCameraInstance) {
            deviceCameraInstance = new DeviceCamera();
        }
        return deviceCameraInstance;
    }

    public void initializeCamera(Context context,
                                 Handler cameraHandler) {

        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // No permissions.
            Log.d(TAG, "Camera Permissions not granted.  This must be first run.  Reboot the device.");
            return;
        }

        // Discover the camera instance
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        String[] cameraIds = {};
        try {
            cameraIds = cameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
           Log.d(TAG, "getCameraIdList failed: " + e.getLocalizedMessage());
        }

        if (cameraIds.length < 1) {
            Log.d(TAG, "No Cameras returned.");
            return;
        }

        try {
            // For now, just open the first camera.
            cameraManager.openCamera(cameraIds[0], stateCallback, cameraHandler);
        } catch (CameraAccessException e) {
            Log.d(TAG, "openCamera failed: " + e.getLocalizedMessage());
        }
    }

    public void takePicture(Handler cameraHandler,
            ImageReader.OnImageAvailableListener imageAvailableListener) {

        if (cameraDevice == null) {
            Log.w(TAG, "takePicture(). Camera not initialized.");
            return;
        }

        if (null != imageReader) {
            imageReader.close();
        }
        imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, MAX_IMAGES);
        imageReader.setOnImageAvailableListener(imageAvailableListener, cameraHandler);

        try {
            List outputs = Collections.singletonList(imageReader.getSurface());
            cameraDevice.createCaptureSession(outputs, sessionCallback, null);
        } catch (CameraAccessException e) {
            Log.d(TAG, "createCaptureSession failed: " + e.getLocalizedMessage());
        }
    }

    public void shutDown() {
        closeCaptureSession();
        if (cameraDevice != null) {
            cameraDevice.close();
        }
    }

    private void closeCaptureSession() {
        if (cameraCaptureSession != null) {
            try {
                cameraCaptureSession.close();
            } catch (Exception ex) {
                Log.e(TAG, "Could not close capture session", ex);
            }
            cameraCaptureSession = null;
        }
    }

    private void captureImage() {
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            Log.d(TAG, "Capture request created.");

            cameraCaptureSession.capture(captureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            Log.d(TAG, "Capture failed: " + e.getLocalizedMessage());
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            Log.d(TAG, "CameraDevice.StateCallback:onOpened");
            cameraDevice = device;
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
            Log.d(TAG, "CameraDevice.StateCallback:onDisconnected");
            closeCaptureSession();
            cameraDevice.close();
        }
        @Override
        public void onError(@NonNull CameraDevice device, int i) {
            Log.d(TAG, "CameraDevice.StateCallback:onError");
            closeCaptureSession();
            cameraDevice.close();
        }
        @Override
        public void onClosed(@NonNull CameraDevice device) {
            Log.d(TAG, "CameraDevice.StateCallback:onClosed");
            cameraDevice = null;
        }
    };

    private final CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "CameraCaptureSession.StateCallback:onConfigured");

            if (null == cameraDevice) {
                Log.d(TAG, "cameraDevice is null.");
                return;
            }

            cameraCaptureSession = session;
            captureImage();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "CameraCaptureSession.StateCallback:onConfigureFailed");
        }
    };

    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            Log.d(TAG, "CameraCaptureSession.CaptureCallback:onCaptureProgressed");
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Log.d(TAG, "CameraCaptureSession.CaptureCallback:onCaptureCompleted");
            session.close();
            cameraCaptureSession = null;
            Log.d(TAG, "CaptureSession closed");
        }
    };
}

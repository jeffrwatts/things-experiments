package com.skiaddict.thingsexperiments;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

/**
 * Created by jewatts on 7/27/17.
 */

public class DeviceCamera {

    private static final String TAG = MainActivity.class.getSimpleName();

    public enum DeviceStateEvents {
        ON_OPENED,
        ON_CLOSED,
        ON_DISCONNECTED
    }

    public enum CaptureSessionStateEvents {
        ON_CONFIGURED,
        ON_READY,
        ON_ACTIVE,
        ON_CLOSED,
        ON_SURFACE_PREPARED
    }

    public enum CaptureSessionEvents {
        ON_STARTED,
        ON_PROGRESSED,
        ON_COMPLETED,
        ON_SEQUENCE_COMPLETED,
        ON_SEQUENCE_ABORTED
    }

    public static class CaptureSessionData {
        final CaptureSessionEvents event;
        final CameraCaptureSession session;
        final CaptureRequest request;
        final CaptureResult result;

        CaptureSessionData(CaptureSessionEvents event, CameraCaptureSession session, CaptureRequest request, CaptureResult result) {
            this.event = event;
            this.session = session;
            this.request = request;
            this.result = result;
        }
    }

    public static Observable<Pair<DeviceStateEvents, CameraDevice>> openCamera (
            @NonNull final Context context) {
        return Observable.create(new ObservableOnSubscribe<Pair<DeviceStateEvents, CameraDevice>>() {
            @Override
            public void subscribe(final ObservableEmitter<Pair<DeviceStateEvents, CameraDevice>> emitter) throws Exception {
                // First find a camera.
                CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                String[] cameraIdList = cameraManager.getCameraIdList();
                if (null == cameraIdList || cameraIdList.length == 0) {
                    Log.d(TAG, "openCamera: No Cameras Found.");
                    emitter.onError(new Throwable("No Cameras Found."));
                    return;
                }

                if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "openCamera: Missing Manifest.permission.CAMERA.");
                    emitter.onError(new Throwable("Missing Manifest.permission.CAMERA"));
                    return;
                }

                cameraManager.openCamera(cameraIdList[0], new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.d(TAG, "openCamera: onOpened");
                        emitter.onNext(Pair.create(DeviceStateEvents.ON_OPENED, camera));
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.d(TAG, "openCamera: onDisconnected");
                        emitter.onNext(Pair.create(DeviceStateEvents.ON_DISCONNECTED, camera));
                    }

                    @Override
                    public void onClosed(@NonNull CameraDevice camera) {
                        Log.d(TAG, "openCamera: onClosed");
                        emitter.onNext(Pair.create(DeviceStateEvents.ON_CLOSED, camera));
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.d(TAG, "openCamera: onError: " + error);
                        emitter.onError(new Throwable("openCamera Failed with %d" + error));
                    }
                }, null);
            }
         });
    }

    @NonNull
    public static Observable<Pair<CaptureSessionStateEvents, CameraCaptureSession>> createCaptureSession(
            @NonNull final CameraDevice cameraDevice,
            @NonNull final List<Surface> surfaceList ) {
        return Observable.create(new ObservableOnSubscribe<Pair<CaptureSessionStateEvents, CameraCaptureSession>>() {
            @Override
            public void subscribe(final ObservableEmitter<Pair<CaptureSessionStateEvents, CameraCaptureSession>> emitter) throws Exception {
                cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        Log.d(TAG, "createCaptureSession: onConfigured");
                        emitter.onNext(Pair.create(CaptureSessionStateEvents.ON_CONFIGURED, session));
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.d(TAG, "createCaptureSession: onConfigureFailed");
                        emitter.onError(new Throwable("createCaptureSession failed"));
                    }

                    @Override
                    public void onReady(@NonNull CameraCaptureSession session) {
                        Log.d(TAG, "createCaptureSession: onReady");
                        emitter.onNext(Pair.create(CaptureSessionStateEvents.ON_READY, session));
                    }

                    @Override
                    public void onActive(@NonNull CameraCaptureSession session) {
                        Log.d(TAG, "createCaptureSession: onActive");
                        emitter.onNext(Pair.create(CaptureSessionStateEvents.ON_ACTIVE, session));
                    }

                    @Override
                    public void onClosed(@NonNull CameraCaptureSession session) {
                        Log.d(TAG, "createCaptureSession: onClosed");
                        emitter.onNext(Pair.create(CaptureSessionStateEvents.ON_CLOSED, session));
                        emitter.onComplete();
                    }

                    @Override
                    public void onSurfacePrepared(@NonNull CameraCaptureSession session, @NonNull Surface surface) {
                        Log.d(TAG, "createCaptureSession: onSurfacePrepared");
                        emitter.onNext(Pair.create(CaptureSessionStateEvents.ON_SURFACE_PREPARED, session));
                    }
                }, null);
            }
        });
    }

    @NonNull
    private static CameraCaptureSession.CaptureCallback createCaptureCallback(final ObservableEmitter<CaptureSessionData> emitter) {
        return new CameraCaptureSession.CaptureCallback() {

            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            }

            @Override
            public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                if (!emitter.isDisposed()) {
                    emitter.onNext(new CaptureSessionData(CaptureSessionEvents.ON_COMPLETED, session, request, result));
                }
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                if (!emitter.isDisposed()) {
                    emitter.onError(new Throwable("onCaptureFailed"));
                }
            }

            @Override
            public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            }

            @Override
            public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            }
        };
    }

    private static Observable<CaptureSessionData> fromSetRepeatingRequest(@NonNull final CameraCaptureSession captureSession, @NonNull final CaptureRequest request) {
        return Observable.create(new ObservableOnSubscribe<CaptureSessionData>() {
            @Override
            public void subscribe(final ObservableEmitter<CaptureSessionData> emitter) throws Exception {
                captureSession.setRepeatingRequest(request, createCaptureCallback(emitter), null);
            }
        });
    }

    private static Observable<CaptureSessionData> fromCapture(@NonNull final CameraCaptureSession captureSession, @NonNull final CaptureRequest request) {
        return Observable.create(new ObservableOnSubscribe<CaptureSessionData>() {
            @Override
            public void subscribe(ObservableEmitter<CaptureSessionData> emitter) throws Exception {
                captureSession.capture(request, createCaptureCallback(emitter), null);
            }
        });
    }
}

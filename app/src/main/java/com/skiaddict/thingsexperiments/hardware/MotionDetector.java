package com.skiaddict.thingsexperiments.hardware;

import android.util.Log;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

/**
 * Created by jewatts on 9/30/17.
 */

public class MotionDetector implements AutoCloseable {
    private static final String TAG = MotionDetector.class.getSimpleName();

    private Gpio motionDetectionPin;
    private OnMotionDetectedEventListener listener;
    private GpioCallback gpioCallback;

    public interface OnMotionDetectedEventListener {
        void onMotionDetectedEvent(boolean active);
    }

    public MotionDetector(String pin) throws IOException {
        PeripheralManagerService pioService = new PeripheralManagerService();

        motionDetectionPin = pioService.openGpio(pin);
        motionDetectionPin.setDirection(Gpio.DIRECTION_IN);
        motionDetectionPin.setEdgeTriggerType(Gpio.EDGE_BOTH);
        gpioCallback = new MotionDetectorGpioCallback();
        motionDetectionPin.registerGpioCallback(gpioCallback);
    }

    public void setOnMotionDetectedEventListener (OnMotionDetectedEventListener listener) {
        this.listener = listener;
    }

    public void close () throws IOException {

        listener = null;

        if (null != motionDetectionPin) {
            motionDetectionPin.unregisterGpioCallback(gpioCallback);
            try {
                motionDetectionPin.close();
            } finally {
                motionDetectionPin = null;
            }
        }
    }

    private class MotionDetectorGpioCallback extends GpioCallback {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            if (null != listener) {
                boolean active = false;
                try {
                    active = gpio.getValue();
                } catch (IOException e) {
                    Log.e(TAG, "onGpioEdge getValue Exception: " + e.getLocalizedMessage());
                }
                listener.onMotionDetectedEvent(active);
            }
            // Continue listening for more interrupts
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.e(TAG, "onGpioError.  Error = " + error);
        }
    }
}

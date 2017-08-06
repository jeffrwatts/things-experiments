package com.skiaddict.thingsexperiments;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;
import com.google.android.things.contrib.driver.button.Button;

import java.io.IOException;
import java.net.SocketException;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final String BUTTON_GPIO_PIN = "BCM21";
    private final String I2C1_PIN = "I2C1";

    private Button cameraButton;
    private DeviceCamera deviceCamera;
    private SensorManager sensorManager;
    private DynamicSensorCallback dynamicSensorCallback;
    private Bmx280SensorDriver bmx280SensorDriver;
    private TemperatureSensorEventListener temperatureSensorEventListener;
    private PressureSensorEventListener pressureSensorEventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        // Get instance of sensorManager and register a callback.
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        dynamicSensorCallback = new DynamicSensorCallback();
        sensorManager.registerDynamicSensorCallback(dynamicSensorCallback);

        // Set up Temperator and Sensor Driver.
        try {
            bmx280SensorDriver = new Bmx280SensorDriver(I2C1_PIN);
            bmx280SensorDriver.registerTemperatureSensor();
            bmx280SensorDriver.registerPressureSensor();
        } catch (IOException e) {
            Log.d(TAG, "Unable to register Bmx280SensorDriver: " + e.getLocalizedMessage());
        }

        try {
            cameraButton = new Button(BUTTON_GPIO_PIN, Button.LogicState.PRESSED_WHEN_LOW);
            cameraButton.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (true == pressed) {
                        takePicture();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayIpAddress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        // Unregister temperator and pressure sensors.
        if (null != temperatureSensorEventListener) {
            sensorManager.unregisterListener(temperatureSensorEventListener);
            temperatureSensorEventListener = null;
        }

        if (null != pressureSensorEventListener) {
            sensorManager.unregisterListener(pressureSensorEventListener);
            pressureSensorEventListener = null;
        }

        try {
            if (null != bmx280SensorDriver) {
                bmx280SensorDriver.unregisterTemperatureSensor();
                bmx280SensorDriver.unregisterPressureSensor();
                bmx280SensorDriver.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Remove the sensor callback.
        sensorManager.unregisterDynamicSensorCallback(dynamicSensorCallback);

        // Close the button.
        try {
            cameraButton.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        Observable<String> observable = Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                e.onNext("Test 1");
                e.onNext("Test 2");
                e.onNext("Test 3");
                e.onNext("Test 4");
                e.onNext("Test 5");
                e.onComplete();
            }
        });

        observable.subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .subscribeWith(new DisposableObserver<String>() {
                    @Override
                    public void onNext(String value) {
                        Log.d(TAG, "onNext: " + value);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, "onError");
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete");
                    }
                });
    }

    void displayIpAddress () {
        StringBuilder sb = new StringBuilder(getResources().getString(R.string.ip_address));
        try {
            List<String> addresses = NetworkUtil.getIPAddressList();

            if (addresses.isEmpty()) {
                sb.append("No IP Address Found.");
            }

            for (String addressIx : addresses) {
                sb.append(addressIx);
                sb.append("; ");
            }
        } catch (SocketException e) {
            // Display whatever failure happened.
            sb.append("Failed to get IP Address" + e.getLocalizedMessage());
        }

        TextView ipAddressView = (TextView)findViewById(R.id.label_ip_address);
        ipAddressView.setText(sb.toString());
    }


    private class DynamicSensorCallback extends SensorManager.DynamicSensorCallback {

        public DynamicSensorCallback() {
            super();
        }

        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            super.onDynamicSensorConnected(sensor);
            int sensorType = sensor.getType();

            if (sensorType == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                // Temperature Sensor connected.
                Log.d(TAG, "Temperature Sensor Connected: " + sensor.getName());
                if (null != temperatureSensorEventListener) {
                    sensorManager.unregisterListener(temperatureSensorEventListener);
                    temperatureSensorEventListener = null;
                }
                temperatureSensorEventListener = new TemperatureSensorEventListener();
                sensorManager.registerListener(temperatureSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            } else if (sensorType == Sensor.TYPE_PRESSURE) {
                Log.d(TAG, "Pressure Sensor Connected: " + sensor.getName());
                if (null != pressureSensorEventListener) {
                    sensorManager.unregisterListener(pressureSensorEventListener);
                    pressureSensorEventListener = null;
                }
                pressureSensorEventListener = new PressureSensorEventListener();
                sensorManager.registerListener(pressureSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

            }
            else {
                Log.d(TAG, "onDynamicSensorConnected called for sensor = : " + sensor.getName());
            }
        }

        @Override
        public void onDynamicSensorDisconnected(Sensor sensor) {
            super.onDynamicSensorDisconnected(sensor);
            Log.d(TAG, "onDynamicSensorDisconnected called for sensor = : " + sensor.getName());
        }
    }

    private class TemperatureSensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "Temperature onSensorChanged: Temperature = " + event.values[0]);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "Temperature onAccuracyChanged: accuracy - " + accuracy);
        }
    }

    private class PressureSensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "Pressure onSensorChanged: Pressure = " + event.values[0]);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "Pressure onAccuracyChanged: accuracy - " + accuracy);
        }
    }
}

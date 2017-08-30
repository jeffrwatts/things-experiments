package com.skiaddict.thingsexperiments;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.gps.NmeaGpsDriver;
import com.google.android.things.contrib.driver.mma7660fc.Mma7660FcAccelerometerDriver;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // For Button
    private final String BUTTON_GPIO_PIN = "BCM21";

    // For Temperature, Pressure, Accelerometer.
    private final String I2C1_PIN = "I2C1";

    // For GPS
    private final String UART_PIN = "UART0";
    public static final int UART_BAUD = 9600;
    public static final float GPS_ACCURACY = 2.5f; // From GPS datasheet

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private DynamicSensorCallback dynamicSensorCallback;

    private Bmx280SensorDriver bmx280SensorDriver;
    private Mma7660FcAccelerometerDriver accelerometerDriver;
    private NmeaGpsDriver gpsDriver;

    private TemperatureSensorEventListener temperatureSensorEventListener;
    private PressureSensorEventListener pressureSensorEventListener;
    private AccelerometerSensorEventListener accelerometerSensorEventListener;

    private double temperature;
    private TextView temperatureView;

    private double pressure;
    private TextView pressureView;

    private double latitude;
    private TextView latitudeView;

    private double longitude;
    private TextView longitudeView;

    private TextView result1View;
    private TextView result2View;
    private TextView result3View;

    private Button cameraButton;
    private DeviceCamera deviceCamera;
    private ImageView cameraImageView;

    private GpsLocationListener gpsLocationListener;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private ImageClassifier imageClassifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        backgroundThread = new HandlerThread("Background Thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());


        temperatureView = (TextView) findViewById(R.id.label_temperature);
        pressureView = (TextView) findViewById(R.id.label_pressure);
        longitudeView = (TextView) findViewById(R.id.label_longitude);
        latitudeView = (TextView) findViewById(R.id.label_latitude);
        cameraImageView = (ImageView) findViewById(R.id.cameraImage);

        // Get instance of locatinManager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Get instance of sensorManager and register a callback.
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        dynamicSensorCallback = new DynamicSensorCallback();
        sensorManager.registerDynamicSensorCallback(dynamicSensorCallback);

        // Set up Temperator and Sensor Driver.
        try {
            bmx280SensorDriver = new Bmx280SensorDriver(I2C1_PIN);
            bmx280SensorDriver.registerTemperatureSensor();
            bmx280SensorDriver.registerPressureSensor();
            Log.d(TAG, "Registered Bmx280SensorDriver");
        } catch (IOException e) {
            Log.d(TAG, "Unable to register Bmx280SensorDriver: " + e.getLocalizedMessage());
        }

        // Set up Accelerometer Driver
        try {
            accelerometerDriver = new Mma7660FcAccelerometerDriver(I2C1_PIN);
            accelerometerDriver.register();
            Log.d(TAG, "Regisetred Mma7660FcAccelerometerDriver");

        } catch (IOException e) {
            Log.d(TAG, "Unable to register Mma7660FcAccelerometerDriver: " + e.getLocalizedMessage());
        }

        // Set up GPS Driver
        try {
            gpsDriver = new NmeaGpsDriver(this, UART_PIN, UART_BAUD, GPS_ACCURACY, backgroundHandler);
            gpsDriver.register();
            Log.d(TAG, "Regisetred NmeaGpsDriver");
        } catch (IOException e) {
            Log.d(TAG, "Unable to register NmeaGpsDriver: " + e.getLocalizedMessage());
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            gpsLocationListener = new GpsLocationListener();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 10, gpsLocationListener);
        }

        // Set up Camera Device
        deviceCamera = DeviceCamera.getInstance();
        deviceCamera.initializeCamera(this, backgroundHandler);

        // Setup GPIO Button to trigger camera (for now).
        try {
            cameraButton = new Button(BUTTON_GPIO_PIN, Button.LogicState.PRESSED_WHEN_LOW);
            cameraButton.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (true == pressed) {
                        deviceCamera.takePicture(backgroundHandler, imageAvailableListener);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        result1View = (TextView)findViewById(R.id.result1);
        result2View = (TextView)findViewById(R.id.result2);
        result3View = (TextView)findViewById(R.id.result3);

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                imageClassifier = new ImageClassifier(MainActivity.this);
            }
        });
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
        }

        // Unregister accelerometer.
        if (null == accelerometerSensorEventListener) {
            sensorManager.unregisterListener(accelerometerSensorEventListener);
            accelerometerSensorEventListener = null;
        }

        try {
            if (null != accelerometerDriver) {
                accelerometerDriver.unregister();
                accelerometerDriver.close();
            }
        } catch (IOException e) {
        }

        try {
            if (null != gpsDriver) {
                gpsDriver.unregister();
                gpsDriver.close();
            }
        } catch (IOException e) {
        }

        // Remove the sensor callback.
        sensorManager.unregisterDynamicSensorCallback(dynamicSensorCallback);

        // Close the button.
        try {
            cameraButton.close();
        } catch (IOException e) {
        }
    }

    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(final ImageReader reader) {
            Image image = reader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);

            final Bitmap croppedImage = imageClassifier.cropAndRescaleBitmap(bitmapImage);
            final List<ImageClassifier.ClassificationResult> results = imageClassifier.doRecognize(croppedImage);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cameraImageView.setImageBitmap(croppedImage);

                    if (results.size() > 0) {
                        result1View.setText(results.get(0).label + " - Confidence: " + results.get(0).confidence);
                    } else {
                        result1View.setText("No Result.");
                    }

                    if (results.size() > 1) {
                        result2View.setText(results.get(1).label + " - Confidence: " + results.get(1).confidence);
                    } else {
                        result2View.setText("");
                    }

                    if (results.size() > 2) {
                        result3View.setText(results.get(2).label + " - Confidence: " + results.get(2).confidence);
                    } else {
                        result3View.setText("");
                    }

                }
            });
        }
    };


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

            } else if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                Log.d(TAG, "Accelerometer Sensor Connected: " + sensor.getName());
                if (null != accelerometerSensorEventListener) {
                    sensorManager.unregisterListener(accelerometerSensorEventListener);
                    accelerometerSensorEventListener = null;
                }
                accelerometerSensorEventListener = new AccelerometerSensorEventListener();
                sensorManager.registerListener(accelerometerSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

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
            if (event.values.length == 0) {
                return;
            }
            double newTemperature = Math.round((event.values[0] * 1.8 + 32) *10.00) / 10.00;

            if (temperature != newTemperature) {
                temperature = newTemperature;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        temperatureView.setText(getString(R.string.temperature) + " " + String.valueOf(temperature));
                    }
                });
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "Temperature onAccuracyChanged: accuracy - " + accuracy);
        }
    }

    private class PressureSensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length == 0) {
                return;
            }
            double newPressure = Math.round(event.values[0] * 10.0) / 10.0;

            if (pressure != newPressure) {
                pressure = newPressure;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pressureView.setText(getString(R.string.pressure) + " " + String.valueOf(pressure));
                    }
                });
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "Pressure onAccuracyChanged: accuracy - " + accuracy);
        }
    }

    private class AccelerometerSensorEventListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "Accelerometer onSensorChanged: Values = " + event.values[0]);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "Accelerometer onAccuracyChanged: accuracy - " + accuracy);
        }
    }

    private class GpsLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "Location update: " + location);
            longitudeView.setText(getResources().getString(R.string.longitude) + " " + String.valueOf(location.getLongitude()));
            latitudeView.setText(getResources().getString(R.string.latitude) + " " + String.valueOf(location.getLatitude()));
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d(TAG, "onStatusChanged.");
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG, "onProviderEnabled.");
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG, "onProviderDisabled.");
        }
    }
}

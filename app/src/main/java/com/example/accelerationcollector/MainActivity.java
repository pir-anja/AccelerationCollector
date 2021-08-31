package com.example.accelerationcollector;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.sql.SQLOutput;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

//esense library copied from https://github.com/pervasive-systems/eSense-Android-Library
import io.esense.esenselib.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener/*, WearSocket.MessageListener*/ {

    private double range = 0; //ESenseConfig.AccRange
    private boolean recording = false;
    private bodySide side = bodySide.NON_DEFINED;
    private String sideString = "";
    private Socket mSocket;
    private Button leftButton;
    private Button rightButton;

    //smartphone sensor
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    private TextView currentPhoneDataX, currentPhoneDataY, currentPhoneDataZ;

    //phone data
    private float[] accelValues;
    private float[] gyroValues;
    private float[] magneticValues;

    //earable sensor
    private ESenseManager earableManager;
    private TextView currentEarableDataX, currentEarableDataY, currentEarableDataZ; //data in ADC format as read directly from the sensor
    private TextView currentEarableAccelDataX, currentEarableAccelDataY, currentEarableAccelDataZ; //data in m/s^2

    {

        try {
            //insert your laptop IP adress (cmd ipconfig)
            //mSocket = IO.socket("http://100.124.115.57:3000"); //Hdk
            //mSocket = IO.socket("http://192.168.178.63:3000"); //Engen
            mSocket = IO.socket("http://172.17.87.140:3000"); //Bib

        } catch (URISyntaxException e) {
            System.out.println(e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initConnections();

       leftButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                side = bodySide.LEFT;
                sideString = side.toString().toLowerCase();
                leftButton.setEnabled(false);
                rightButton.setEnabled(false);
                leftButton.setBackgroundColor(Color.parseColor("#FF018786"));
                //init eSense sensor, using left eSense #15 with deviceName: "eSense-0830"
                EarableConnectionListener eSenseConnectionListener = new EarableConnectionListener();
                earableManager = new ESenseManager("eSense-0830", MainActivity.this.getApplicationContext(), eSenseConnectionListener);
                earableManager.connect(500000);
                mSocket.emit("phone side connect", sideString);
            }
        });
        rightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                side = bodySide.RIGHT;
                sideString = side.toString().toLowerCase();
                leftButton.setEnabled(false);
                rightButton.setEnabled(false);
                rightButton.setBackgroundColor(Color.parseColor("#FF018786"));
                //init eSense sensor, using left eSense #32 which is called "eSense-0151"
                EarableConnectionListener eSenseConnectionListener = new EarableConnectionListener();
                earableManager = new ESenseManager("eSense-0151", MainActivity.this.getApplicationContext(), eSenseConnectionListener);
                earableManager.connect(500000);
                mSocket.emit("phone side connect", sideString);
            }
        });
    }

    private void initConnections() {
        mSocket.on("chat message", (s) -> {
            System.out.println("chat");
        });

        mSocket.on("start recording", (s) -> {
            System.out.println("start recording");
            recording = true;
        });

        mSocket.on("stop recording", (s) -> {
            System.out.println("stop recording");
            recording = false;
        });
        mSocket.connect();

        mSocket.on("connect", (s) -> connectionEstablished());


        //init smartphone sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // phone accelerometer exists
            System.out.println("Has phone accelerometer"); // phone accelerometer available
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            int accelDelay = accelerometer.getMinDelay();
            System.out.println("accelerometer min delay: " + accelDelay);
            //changed SENSOR_DELAY_NORMAL to _FASTEST in order to get maximum sensor data frequency
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            System.out.println("No phone accelerometer"); // no phone accelerometer available
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
            // phone accelerometer exists
            System.out.println("Has phone gyroscope"); // phone gyro available
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            int gyroDelay = gyroscope.getMinDelay();
            System.out.println("gyro min delay: " + gyroDelay);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            System.out.println("No phone gyroscope"); // no phone accelerometer available
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            // phone accelerometer exists
            System.out.println("Has phone magnetometer"); // phone magnetometer available
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            int magneticDelay = magnetometer.getMinDelay();
            System.out.println("magnetic min delay: " + magneticDelay);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            System.out.println("No phone magnetometer"); // no phone accelerometer available
        }
    }

    private void connectionEstablished() {
        System.out.println("id " + mSocket.id());
        mSocket.emit("phone connect", "init");
    }

    private void initializeViews() {
        currentPhoneDataX = (TextView) findViewById(R.id.text_phoneDataX);
        currentPhoneDataY = (TextView) findViewById(R.id.text_phoneDataY);
        currentPhoneDataZ = (TextView) findViewById(R.id.text_phoneDataZ);

        currentEarableDataX = (TextView) findViewById(R.id.text_eSenseDataX);
        currentEarableDataY = (TextView) findViewById(R.id.text_eSenseDataY);
        currentEarableDataZ = (TextView) findViewById(R.id.text_eSenseDataZ);

        currentEarableAccelDataX = (TextView) findViewById(R.id.text_eSenseAccelDataX);
        currentEarableAccelDataY = (TextView) findViewById(R.id.text_eSenseAccelDataZ);
        currentEarableAccelDataZ = (TextView) findViewById(R.id.text_eSenseAccelDataY);

        leftButton = (Button) findViewById(R.id.button_left);
        rightButton = (Button) findViewById(R.id.button_right);
    }

    //onResume() register the phone accelerometer for listening the events
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST); //comment this line if there is no magnometer on your device
    }

    //onPause() unregister the phone accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    //Called when there is a new phone sensor event (e.g. every time when phone accelerometer data has changed)
    @Override
    public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelValues = event.values;

                long timestamp = event.timestamp;
                String accelX = Float.toString(accelValues[0]);
                String accelY = Float.toString(accelValues[1]);
                String accelZ = Float.toString(accelValues[2]);

                currentPhoneDataX.setText("x: " + accelX);
                currentPhoneDataY.setText("y: " + accelY);
                currentPhoneDataZ.setText("z: " + accelZ);

                //sends only to server if accel data has changed, not when only gyro data has changed
                if (recording) {
                    //timestamp,acc_x,acc_y,acc_z
                    attemptSendPhoneAccel(timestamp + "," + accelX + "," + accelY + "," + accelZ);
                }
            }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroValues = event.values;
            long timestamp = event.timestamp;
            if (recording) {
                attemptSendPhoneGyro(timestamp + "," + gyroValues[0] + "," + gyroValues[1] + "," + gyroValues[2]);
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues = event.values;
            long timestamp = event.timestamp;
            if (recording) {
                attemptSendPhoneMagnetic(timestamp + "," + magneticValues[0] + "," + magneticValues[1] + "," + magneticValues[2]);
            }
        }

    }

    //Called when the accuracy of the registered phone sensor has changed.
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    class EarableConnectionListener implements ESenseConnectionListener {
        private String deviceStatus = "";

        @Override
        public void onDeviceFound(ESenseManager manager) {
            deviceStatus = "device found";
            System.out.println(deviceStatus);
        }

        @Override
        public void onDeviceNotFound(ESenseManager manager) {
            deviceStatus = "device not found";
            System.out.println(deviceStatus);
        }

        @Override
        public void onConnected(ESenseManager manager) {
            deviceStatus = "device connected";
            System.out.println(deviceStatus);
            //you can only listen to sensor data after a device has been connected
            EarableSensorListener eSenseSensorListener = new EarableSensorListener();
            //start listening to earable sensor data
            earableManager.registerSensorListener(eSenseSensorListener, 100);
            mSocket.emit("esense side connect", sideString);

            //set current eSense configuration so that acceleration scale factor (called 'range') should be 8192 LSB/g (default value)
            EarableEventListener eSenseEventListener = new EarableEventListener();
            earableManager.registerEventListener(eSenseEventListener);
            ESenseConfig config = new ESenseConfig();
            earableManager.setSensorConfig(config);
            range = config.getAccSensitivityFactor();
            System.out.println("range: " + range);
            //earableManager.getSensorConfig();
        }

        @Override
        public void onDisconnected(ESenseManager manager) {
            deviceStatus = "device not connected";
            System.out.println(deviceStatus);
        }
    }

    class EarableEventListener implements ESenseEventListener {
        @Override
        public void onBatteryRead(double voltage) {

        }

        @Override
        public void onButtonEventChanged(boolean pressed) {

        }

        @Override
        public void onAdvertisementAndConnectionIntervalRead(int minAdvertisementInterval, int maxAdvertisementInterval, int minConnectionInterval, int maxConnectionInterval) {

        }

        @Override
        public void onDeviceNameRead(String deviceName) {

        }

        @Override
        public void onSensorConfigRead(ESenseConfig config) {
            range  = config.getAccSensitivityFactor();
            System.out.println("range: " + range);

        }

        @Override
        public void onAccelerometerOffsetRead(int offsetX, int offsetY, int offsetZ) {

        }
    }

    class EarableSensorListener implements ESenseSensorListener {
        //private float timestamp;

        //Called when there is a new eSense sensor event (e.g. every time when eSense accelerometer data has changed)
        @Override
        public void onSensorChanged(ESenseEvent evt) {

            short[] accelValues = evt.getAccel();
            short[] gyroValues = evt.getGyro();
            long timestamp = evt.getTimestamp();

            //getting new eSense data is a background task, move updating the UI onto main thread
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //updates the UI:
                    setEarableDataView(accelValues);
                }
            });

            if (recording) {
                //timestamp,acc_x,acc_y,acc_z,gyro_x,_gyro_y,gyro_z
                attemptSendESense(timestamp + "," + accelValues[0] + "," + accelValues[1] + "," + accelValues[2] + "," + gyroValues[0] + "," + gyroValues[1] + "," + gyroValues[2]);
                attemptSendESenseConverted(timestamp + "," + accelerometerDataConversion(accelValues[0]) + "," + accelerometerDataConversion(accelValues[1]) + "," + accelerometerDataConversion(accelValues[2]));
            }
        }
    }

    public void setEarableDataView(short[] values) {
        currentEarableDataX.setText("x: " + Short.toString(values[0]));
        currentEarableDataY.setText("y: " + Short.toString(values[1]));
        currentEarableDataZ.setText("z: " + Short.toString(values[2]));

        //earableManager.sConfig();
        if (range != 0) {
            currentEarableAccelDataX.setText("x: " + Double.toString(accelerometerDataConversion(values[0])));
            currentEarableAccelDataY.setText("y: " + Double.toString(accelerometerDataConversion(values[1])));
            currentEarableAccelDataZ.setText("z: " + Double.toString(accelerometerDataConversion(values[2])));
        }
    }

    /* According to eSense documentation: Float value in m/s^2 = (Acc value / Acc scale factor) * 9.80665
     * Acc value is in ADC format as read directly from the sensor
     * Acc scale factor should be 8192 LSB/g by default
     * (Referred to https://www.esense.io/share/eSense-BLE-Specification.pdf)
     */
    public double accelerometerDataConversion(short accValue) {
        return (accValue / range ) * 9.80665;
    }


    //send acceleration data to socket.IO server
    private void attemptSendPhoneAccel(String msg) {
        mSocket.emit("phone accel data " + sideString, msg);
    }

    private void attemptSendPhoneGyro(String msg) {
        mSocket.emit("phone gyro data " + sideString, msg);
    }

    private void attemptSendPhoneMagnetic(String msg) {
        mSocket.emit("phone magnetic data " + sideString, msg);
    }

    private void attemptSendESense(String msg) {
        mSocket.emit("esense data " + sideString, msg);

    }

    //send eSense accel data in m/s^2 instead of ADC format
    private void attemptSendESenseConverted(String msg) {
        mSocket.emit("esense converted data " + sideString, msg);

    }

    //the socket.IO server can send events too
    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String message;
                    try {
                        message = data.getString("message");
                    } catch (JSONException e) {
                        return;
                    }

                    // add the message to view
                    System.out.println(message);
                }
            });
        }
    };

    //close the socket connection and remove all listeners
    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off("new message", onNewMessage);
    }

}



package com.example.danielmurray.adaptiv;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import java.lang.Math;


public class MyDisplayActivity extends Activity implements SensorEventListener {

    private TextView mTextView;
    private SensorManager mSensorManager;
    private Sensor mAcc;
    private long mLastTimestamp;
    private int mOdr;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);
        mTextView = (TextView) findViewById(R.id.text);
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAcc, SensorManager.SENSOR_DELAY_GAME);
        mLastTimestamp = 0;
        mOdr = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAcc, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("DEBUG", "onSensorChanged");

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];

            if (mOdr == 0) {
                if (mLastTimestamp != 0) {
                    long ns = event.timestamp - mLastTimestamp;
                    // It's necessary to promote ns to double for calc
                    double s = (double)ns / (1000 * 1000 * 1000);
                    mOdr = (int)Math.floor(1 / s);
                    // round to nearest 10
                    mOdr = (int)Math.floor(((mOdr + 5) / 10) * 10);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                           mTextView.setText(String.format("ODR: %d", mOdr));
                        }
                    });
                }
                mLastTimestamp = event.timestamp;
            }

            //Log.d("DEBUG", String.format("Data: %d, %f, %f, %f", event.timestamp, x, y, z));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Log.d("DEBUG", "onAccuracyChanged");
    }
}
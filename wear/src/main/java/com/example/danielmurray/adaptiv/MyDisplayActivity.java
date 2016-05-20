package com.example.danielmurray.adaptiv;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.Math;


public class MyDisplayActivity extends Activity implements SensorEventListener {

    private final String TAG = "AdaptivWear";
    private final String DIR_NAME = "Adaptiv";

    private final int MSG_ACC_READING_START = 0;
    private final int MSG_ACC_READING_STOP = 1;
    private final int MSG_ACC_READING = 2;

    private TextView mTextView;
    private Button mCaptureButton;
    private SensorManager mSensorManager;
    private Sensor mAcc;
    private long mLastTimestamp;
    private int mOdr;
    private File mDir;
    private File mAccFile;
    private BufferedWriter mAccFileWriter;
    private String mAccFileName;
    private boolean mIsCapture;
    private HandlerThread mWorkerThread;
    private Handler mWorkerThreadHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);
        mTextView = (TextView) findViewById(R.id.text);
        mCaptureButton = (Button) findViewById(R.id.capture);
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAcc = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mLastTimestamp = 0;
        mOdr = 0;
        mIsCapture = false;
        mAccFile = null;
        mAccFileName = null;
        mAccFileWriter = null;
        mWorkerThread = new HandlerThread("worker");
        mWorkerThread.start();
        mWorkerThreadHandler = new Handler(mWorkerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_ACC_READING_START:
                        mAccFileName = String.format("acc-reading-%d.csv", System.currentTimeMillis());
                        try {
                            mDir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
                            mDir.mkdirs();
                            mAccFile = new File(mDir, mAccFileName);
                            mAccFile.createNewFile();
                            mAccFileWriter = new BufferedWriter(new FileWriter(mAccFile));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case MSG_ACC_READING_STOP:
                        Log.d(TAG, "READING_STOP");
                        try {
                            mAccFileWriter.close();
                            mAccFileWriter = null;
                            mAccFile = null;
                            mDir = null;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case MSG_ACC_READING:
                        Log.d(TAG, msg.obj.toString());
                        try {
                            if (mAccFileWriter != null) {
                                mAccFileWriter.write(msg.obj.toString());
                                mAccFileWriter.newLine();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }
        };

        mCaptureButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsCapture) {
                    mSensorManager.unregisterListener(MyDisplayActivity.this, mAcc);
                    mCaptureButton.setText("Start Capture");
                    mWorkerThreadHandler.obtainMessage(MSG_ACC_READING_STOP);
                } else {
                    mCaptureButton.setText("Stop Capture");
                    mWorkerThreadHandler.obtainMessage(MSG_ACC_READING_START);
                    mSensorManager.registerListener(MyDisplayActivity.this, mAcc, SensorManager.SENSOR_DELAY_GAME);
                }
                mIsCapture = !mIsCapture;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //mWorkerThreadHandler.obtainMessage(MSG_ACC_READING_START);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //mWorkerThreadHandler.obtainMessage(MSG_ACC_READING_STOP);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];

            if (mOdr == 0) {
                if (mLastTimestamp != 0) {
                    long ns = event.timestamp - mLastTimestamp;
                    // It's necessary to promote ns to double for calc
                    double s = (double) ns / (1000 * 1000 * 1000);
                    mOdr = (int) Math.floor(1 / s);
                    // round to nearest 10
                    mOdr = (int) Math.floor(((mOdr + 5) / 10) * 10);
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
            String line = String.format("%d,%f,%f,%f", event.timestamp, x, y, z);
            mWorkerThreadHandler.obtainMessage(MSG_ACC_READING, line).sendToTarget();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //Log.d("DEBUG", "onAccuracyChanged");
    }
}
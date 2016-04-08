package com.example.danielmurray.adaptiv;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.*;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ListView;
//import android.widget.Button;
import android.view.View;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.os.Handler;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private KeyValueObj xObj = new KeyValueObj("X","0");
    private KeyValueObj yObj = new KeyValueObj("Y","10");
    private KeyValueObj zObj = new KeyValueObj("Z","100");
    private KeyValueObj ourStepObj = new KeyValueObj("Our Steps","10000");
    private KeyValueObj gpsStatusObj = new KeyValueObj("GPS Status","No Connection");

    private ArrayList<KeyValueObj> keyValueObjList = new ArrayList<KeyValueObj>(Arrays.asList(new KeyValueObj[]{
            xObj,
            yObj,
            zObj,
            ourStepObj,
            gpsStatusObj
    }));

    private KeyValueAdapter adapter;

    private MainActivity activity = this;

    private ListView listView;
    private AppCompatButton button;
    private ShareActionProvider mShareActionProvider;

    private Context context;

    private SensorManager sensorManager;
    private LocationManager locationManager;

    private GPSListener gpsListener;

    private FileIO accelFile;
    private FileIO gyroFile;
    private FileIO laFile;
    private FileIO gravFile;
    private FileIO stepFile;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundThreadHandler;
    private Runnable mPlaySoundRunnable;
    private ToneGenerator mToneGen;
    private int mTestBPM = 90;
    private long mStartTime = 0;
    private long mStopTime = 0;
    private String mOutputDir;
    private boolean mCollecting = false;

    static String ACC_FILE_NAME = "accelerometer.csv";
    static String GYRO_FILE_NAME = "accelerometer.csv";
    static String LINEAR_ACC_FILE_NAME = "linear_accelerometer.csv";
    static String GRAVITY_FILE_NAME = "gravity.csv";
    static String STEP_FILE_NAME = "step.csv";

    static boolean ENABLE_ACC = true;
    static boolean ENABLE_GYRO = false;
    static boolean ENABLE_LINEAR_ACC = false;
    static boolean ENABLE_GRAVITY = false;
    static boolean ENABLE_STEP = true;

    private long getCurrentTime() {
        return System.currentTimeMillis();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar setting
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Create another thread for playing bpm sound
        mBackgroundThread = new HandlerThread("background");
        mBackgroundThread.start();
        mBackgroundThreadHandler = new Handler(mBackgroundThread.getLooper());
        mToneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        mPlaySoundRunnable = new Runnable() {
            @Override
            public void run() {
                if (mStartTime == 0)
                    mStartTime = getCurrentTime();
                mToneGen.startTone(ToneGenerator.TONE_PROP_BEEP);
                mBackgroundThreadHandler.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                mToneGen.stopTone();
                            }
                        },
                        100
                );
                mBackgroundThreadHandler.postDelayed(this, (60 * 1000 / mTestBPM));
            }
        };

        // Get an instance of the SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        if (ENABLE_ACC && sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size() != 0) {
            Sensor accel = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
            sensorManager.registerListener(this,accel, SensorManager.SENSOR_DELAY_GAME, 0, mBackgroundThreadHandler);
        }

        if (ENABLE_GYRO && sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).size() != 0) {
            Sensor gyro = sensorManager.getSensorList(Sensor.TYPE_GYROSCOPE).get(0);
            sensorManager.registerListener(this,gyro, SensorManager.SENSOR_DELAY_GAME, 0, mBackgroundThreadHandler);
        }

        if (ENABLE_LINEAR_ACC && sensorManager.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION).size()!=0) {
            Sensor la = sensorManager.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION).get(0);
            sensorManager.registerListener(this, la, SensorManager.SENSOR_DELAY_GAME, 0, mBackgroundThreadHandler);
        }

        if(ENABLE_GRAVITY && sensorManager.getSensorList(Sensor.TYPE_GRAVITY).size()!=0) {
            Sensor grav = sensorManager.getSensorList(Sensor.TYPE_GRAVITY).get(0);
            sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_GAME, 0, mBackgroundThreadHandler);
        }

        if(ENABLE_STEP && sensorManager.getSensorList(Sensor.TYPE_STEP_COUNTER).size()!=0) {
            Sensor step = sensorManager.getSensorList(Sensor.TYPE_STEP_COUNTER).get(0);
            sensorManager.registerListener(this, step, SensorManager.SENSOR_DELAY_GAME, 0, mBackgroundThreadHandler);
        }

        context = getApplicationContext();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        listView = (ListView) findViewById(R.id.data_list);
        button = (AppCompatButton) findViewById(R.id.button);

        adapter = new KeyValueAdapter(this, keyValueObjList);

        listView.setAdapter(adapter);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCollecting) {
                    button.setText("Start new Capture");
                    mBackgroundThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onCaptureStopped();
                        }
                    });
                } else {
                    button.setText("Stop Capture");
                    mBackgroundThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            onCaptureStarted();
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCollecting) {
            button.setText("Start new Capture");
            mBackgroundThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    onCaptureStopped();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];

            xObj.setValue(String.valueOf(x));
            yObj.setValue(String.valueOf(y));
            zObj.setValue(String.valueOf(z));

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.notifyDataSetChanged();
                }
            });

            if(mCollecting){
                String line = String.valueOf(event.timestamp) + ',' + x + ',' + y + ',' + z;
                accelFile.writeLine(line);
            }

        }else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];

            if(mCollecting){
                String line = String.valueOf(event.timestamp) + ',' + x + ',' + y + ',' + z;
                gyroFile.writeLine(line);
            }

        }else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];

            if(mCollecting){
                String line = String.valueOf(event.timestamp) + ',' + x + ',' + y + ',' + z;
                laFile.writeLine(line);
            }

        }else if(event.sensor.getType() == Sensor.TYPE_STEP_COUNTER){
            double steps = event.values[0];

            ourStepObj.setValue(String.valueOf(steps));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adapter.notifyDataSetChanged();
                }
            });

            if(mCollecting){
                String line = String.valueOf(event.timestamp) + ',' + steps;
                stepFile.writeLine(line);
            }
        }else if(event.sensor.getType() == Sensor.TYPE_GRAVITY){
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];

            if(mCollecting){
                String line = String.valueOf(event.timestamp) + ',' + x + ',' + y + ',' + z;
                gravFile.writeLine(line);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

    public void setGPSStatus(String status){
        gpsStatusObj.setValue(status);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem item = menu.findItem(R.id.action_share);
        mShareActionProvider = (ShareActionProvider)MenuItemCompat.getActionProvider(item);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/zip");
        mShareActionProvider.setShareIntent(shareIntent);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d("ASKA", "DEBUG 1");
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.action_share:
                Log.d("ASKA", "DEBUG");
                break;
            default:
                return false;
        }

        return true;
    }

    private void onCaptureStarted() {
        // update BPM from preference
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mTestBPM = Integer.parseInt(p.getString("bpm_list", "90"));

        // Delay a little bit for user to catch up
        mStartTime = 0;
        mStopTime = 0;
        mBackgroundThreadHandler.postDelayed(mPlaySoundRunnable, 2000);

        // Begin data stream to CSV file
        mCollecting = true;

        Date date = new Date();
        String dateString = new SimpleDateFormat("yyyy-MM-dd_hh:mm:ss").format(date);
        mOutputDir = "Adaptiv/reading_" + dateString + "/";

        accelFile = new FileIO(mOutputDir, ACC_FILE_NAME, context);
        gyroFile = new FileIO(mOutputDir, GYRO_FILE_NAME, context);
        laFile = new FileIO(mOutputDir, LINEAR_ACC_FILE_NAME, context);
        gravFile = new FileIO(mOutputDir, GRAVITY_FILE_NAME, context);
        stepFile = new FileIO(mOutputDir, STEP_FILE_NAME, context);

        if(locationManager.getAllProviders().size()!=0) {
            gpsListener = new GPSListener(context, activity);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 2, gpsListener);
        }
    }

    private void onCaptureStopped() {
        mBackgroundThreadHandler.removeCallbacks(mPlaySoundRunnable);
        mStopTime = getCurrentTime();
        mCollecting = false;

        //Log.d("ASKA", String.format("start: %d, stop: %d, bpm: %d", mStartTime, mStopTime, mTestBPM));
        String newName = String.format("50hz_%dbpm_%dsteps.csv", mTestBPM, (int) Math.floor(mStopTime - mStartTime) * mTestBPM / (60 * 1000));
        //Log.d("ASKA", "S: " + newName);
        accelFile.renameTo(mOutputDir, newName);
        accelFile.close();
        gyroFile.close();
        laFile.close();
        gravFile.close();
        stepFile.close();

        if (locationManager.getAllProviders().size()!=0) {
            gpsListener.closeFile();
            locationManager.removeUpdates(gpsListener);
        }
    }
}

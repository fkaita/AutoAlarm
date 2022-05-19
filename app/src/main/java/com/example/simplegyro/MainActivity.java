package com.example.simplegyro;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    Sensor accelerometer;
    Sensor magnetometer;
    private Sensor sensorRt;
    private Sensor sensorAc;
    private TextView textView;
    private float accelX;
    private float accelY;
    private float accelZ;
    private float rotationX;
    private float rotationY;
    private float rotationZ;
    // 'Handler()' is deprecated as of API 30: Android 11.0 (R)
    private final android.os.Handler handler = new Handler(Looper.getMainLooper());
    private final int period = 100;
    private Runnable runnable;
    private List<String> slicedData;
    private Switch switchRec;
    private TextView message;
    private TextView txtClass;
    private SoundPool soundPool;
    private int soundOne;
    private TestOpenHelper helper;
    private SQLiteDatabase db;

    private static final int PERMISSION_WRITE_EX_STR = 1;

    ActivityResultLauncher resultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent resultData  = result.getData();
                    if (resultData  != null) {
                        Uri uri = resultData.getData();

                        // Uriを表示
                        textView.setText(
                                String.format(Locale.US, "Uri:　%s",uri.toString()));

                        try(OutputStream outputStream =
                                    getContentResolver().openOutputStream(uri)) {
                            if(outputStream != null){
                                Date date=new Date();
                                SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd");
                                String time = "Data saved on " + formatter.format(date) + "\n";
                                outputStream.write(time.getBytes());

                                if(helper == null){
                                    helper = new TestOpenHelper(getApplicationContext());
                                }

                                if(db == null){
                                    db = helper.getReadableDatabase();
                                }
                                Log.d("debug","**********Cursor");

                                Cursor cursor = db.query(
                                        "testdb",
                                        new String[] { "time", "rotX", "rotY", "rotZ", "accX", "accY", "accZ" },
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                );

                                cursor.moveToFirst();
                                for (int i = 0; i < cursor.getCount(); i++) {
                                    StringBuilder sbuilder = new StringBuilder();
                                    sbuilder.append(cursor.getString(0));
                                    sbuilder.append(", ");
                                    sbuilder.append(cursor.getString(1));
                                    sbuilder.append(", ");
                                    sbuilder.append(cursor.getString(2));
                                    sbuilder.append(", ");
                                    sbuilder.append(cursor.getString(3));
                                    sbuilder.append(", ");
                                    sbuilder.append(cursor.getString(4));
                                    sbuilder.append(", ");
                                    sbuilder.append(cursor.getString(5));
                                    sbuilder.append(", ");
                                    sbuilder.append(cursor.getString(6));
                                    sbuilder.append("\n");
                                    outputStream.write(sbuilder.toString().getBytes());
                                    cursor.moveToNext();
                                }
                                // 忘れずに！
                                cursor.close();
                            }

                        } catch(Exception e){
                            e.printStackTrace();
                        }

                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        slicedData = new ArrayList<String>();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        textView = findViewById(R.id.txtView);
        txtClass = findViewById(R.id.txtClass);
        timerSet();


        if (Build.VERSION.SDK_INT >= 23) {
            if(ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_CONTACTS
                        },
                        PERMISSION_WRITE_EX_STR);
            }
        }

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                // USAGE_MEDIA
                // USAGE_GAME
                .setUsage(AudioAttributes.USAGE_GAME)
                // CONTENT_TYPE_MUSIC
                // CONTENT_TYPE_SPEECH, etc.
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        soundPool = new SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                // ストリーム数に応じて
                .setMaxStreams(2)
                .build();

        soundOne = soundPool.load(this, R.raw.sound1, 1);

        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                Log.d("debug","sampleId="+sampleId);
                Log.d("debug","status="+status);
            }
        });

        TextView message = findViewById(R.id.message);
        Switch switchRec = findViewById(R.id.switchRec);
        switchRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (switchRec.isChecked()){
                    if(helper == null){
                        helper = new TestOpenHelper(getApplicationContext());
                    }
                    if(db == null){
                        db = helper.getWritableDatabase();
                        helper.onUpgrade(db, 0,1);
                    }

                    message.setText("Recording");
                } else{
                    message.setText("");
                    Date date = new Date();
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss"); //ACTION_CREATE_DOCUMENT
                    String time = formatter.format(date);
                    String fileName = time + ".txt";
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, fileName);
                    resultLauncher.launch(intent);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permission, int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permission, grantResults);
        if (grantResults.length <= 0) {
            return;
        }
        switch (requestCode) {
            case PERMISSION_WRITE_EX_STR: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    /// 許可が取れた場合・・・
                    /// 必要な処理を書いておく
                } else {
                    /// 許可が取れなかった場合・・・
                    Toast.makeText(this,
                            "Cannot launch the app", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
            return;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
//        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
//        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);

        sensorRt = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorAc = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (sensorRt!=null & sensorAc!=null){
            sensorManager.registerListener((SensorEventListener) this, sensorRt, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener((SensorEventListener) this, sensorAc, SensorManager.SENSOR_DELAY_NORMAL);
        }else{
            String ns = "No Support";
            textView.setText(ns);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        stopTimerTask();
    }

    private void stopTimerTask() {
        handler.removeCallbacks(runnable);
    }

    float[] mGravity;
    float[] mGeomagnetic;
    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("debug", "onSensorChanged");
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ROTATION_VECTOR:
                rotationX = event.values[0];
                rotationY = event.values[1];
                rotationZ = event.values[2];
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accelX = event.values[0];
                accelY = event.values[1];
                accelZ = event.values[2];
                break;
            default:
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void timerSet(){
        runnable = new Runnable() {
            @Override
            public void run() {
                Date date = new Date();
                SimpleDateFormat formatter= new SimpleDateFormat("HH:mm:ss");
                String time=formatter.format(date);
                String s = "";
                String rotX = Float.toString(rotationX);
                String rotY = Float.toString(rotationY);
                String rotZ = Float.toString(rotationZ);
                String accX = Float.toString(accelX);
                String accY = Float.toString(accelY);
                String accZ = Float.toString(accelZ);
                s = time+"\n"+rotX +","+rotY+","+rotZ+"\n" + accX +","+accY+","+accZ+"\n";
                textView.setText(s);
                if (db!=null) {
                    insertData(db, time, rotX, rotY, rotZ, accX, accY, accZ);
                }

                if (slicedData.size()<60) {
                    slicedData.add(rotX);
                    slicedData.add(rotY);
                    slicedData.add(rotZ);
                    slicedData.add(accX);
                    slicedData.add(accY);
                    slicedData.add(accZ);
                    if (slicedData.size()==60) {
                        String str = slicedData.toString();
                        int estimation = SVC.main(slicedData);
                        txtClass.setText(String.valueOf(estimation));
                        if (estimation == 1) {
                            // Play sound if sleeping
//                            soundPool.play(soundOne, 1.0f, 1.0f, 0, 0, 1);
                        }
                    }
                }else {
                    slicedData = new ArrayList<String>();
                }
                handler.postDelayed(this, period);
            }
        };
        handler.post(runnable);
    }

    private void insertData(SQLiteDatabase db, String time, String rotX, String rotY, String rotZ, String accX, String accY, String accZ){
        ContentValues values = new ContentValues();
        values.put("time", time);
        values.put("rotX", rotX);
        values.put("rotY", rotY);
        values.put("rotZ", rotZ);
        values.put("accX", accX);
        values.put("accY", accY);
        values.put("accZ", accZ);

        db.insert("testdb", null, values);
    }

}
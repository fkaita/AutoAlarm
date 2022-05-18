package com.example.simplegyro;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor sensor;
    private TextView textView;
    private float sensorX;
    private float sensorY;
    private float sensorZ;
    // 'Handler()' is deprecated as of API 30: Android 11.0 (R)
    private final android.os.Handler handler = new Handler(Looper.getMainLooper());
    private final int period = 100;
    private Runnable runnable;
    private List<String> data;
    private List<String> slicedData;
    private Switch switchRec;
    private TextView message;
    private TextView txtClass;
    private SoundPool soundPool;
    private int soundOne;

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
                                for (int i=0; i<data.size(); i++) {
                                    outputStream.write(data.get(i).getBytes());
                                }
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

        data = new ArrayList<String>();
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
                    data.clear();
                    message.setText("Recording");
                } else{
                    message.setText("");
                    Date date=new Date();
                    SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
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
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (sensor!=null){
            sensorManager.registerListener((SensorEventListener) this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("debug", "onSensorChanged");
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            sensorX = event.values[0];
            sensorY = event.values[1];
            sensorZ = event.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void timerSet(){
        runnable = new Runnable() {
            @Override
            public void run() {
                String strTmp = String.format(Locale.US, "%f, %f, %f\n",
                        sensorX, sensorY, sensorZ);
                Date date=new Date();
                SimpleDateFormat formatter= new SimpleDateFormat("HH:mm:ss");
                String time=formatter.format(date);
                String s="";
                s=time+","+Float.toString(sensorX) +","+Float.toString(sensorY)+","+Float.toString(sensorZ)+"\n";
                textView.setText(s);
                if (slicedData.size()<30) {
                    slicedData.add(Float.toString(sensorX));
                    slicedData.add(Float.toString(sensorY));
                    slicedData.add(Float.toString(sensorZ));
                    if (slicedData.size()==30) {
//                        String str = slicedData.toString();
                        int estimation = SVC.main(slicedData);
                        txtClass.setText(String.valueOf(estimation));
                        if (estimation == 1) {
                            soundPool.play(soundOne, 1.0f, 1.0f, 0, 0, 1);
                        }
                    }
                }else {
                    slicedData = new ArrayList<String>();
                }

                data.add(s);
                handler.postDelayed(this, period);
            }
        };
        handler.post(runnable);
    }

}
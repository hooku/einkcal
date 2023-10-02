package com.hooku.einkcal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

public class EinkCalActivity extends AppCompatActivity {
    final Thread updateCalender = new Thread(new Runnable() {
        private volatile boolean isCalendarUpdating = false;

        @Override
        public void run() {
            if (isCalendarUpdating) {
                //showToast("Already updating");
                return;
            }
            isCalendarUpdating = true;

            String urlCalendar = "https://alltobid.cf/einkcal/1.png";

            EinkCalUtil.NetUtil netUtil = new EinkCalUtil.NetUtil(getApplicationContext());
            netUtil.setWifiStatus(true);
            try {
                URL url = new URL(urlCalendar);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setConnectTimeout(10000);
                httpURLConnection.setDoInput(true);
                httpURLConnection.setRequestProperty("Cache-Control", "no-cache");
                httpURLConnection.setDefaultUseCaches(false);
                httpURLConnection.setUseCaches(false);
                httpURLConnection.connect();
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = httpURLConnection.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ImageView imageView = findViewById(R.id.imageCalendar);
                            imageView.setImageBitmap(bitmap);
                            refreshScreen();
                        }
                    });
                } else {
                    String httpResp = String.format("HTTP resp %d", responseCode);
                    //showToast(httpResp);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            netUtil.setWifiStatus(false);
            isCalendarUpdating = false;
        }
    });
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String deviceStatus = String.format("Scr:%s", intent.getAction() == Intent.ACTION_SCREEN_ON ? "On" : "Off");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.textScreen);
                    textView.setText(deviceStatus);
                }
            });
        }
    };
    private final BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateCalender.start();
        }
    };

    private void updateStatusBar(String log) {
        EinkCalUtil.SysUtil sysUtil = new EinkCalUtil.SysUtil(getApplicationContext());
        int batteryLevel = sysUtil.getBatteryLevel();
        String time = sysUtil.getTime();
        String deviceStatus = String.format("Batt:%d%%", batteryLevel);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView textView = findViewById(R.id.textStatus);
                textView.setText(deviceStatus);
            }
        });
    }

    private void refreshScreen() {
        Toast.makeText(getApplicationContext(), "Updated", Toast.LENGTH_LONG).show();
    }

    private void installAlarm() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 40);

        Intent intent = new Intent(this, alarmReceiver.getClass());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), AlarmManager.INTERVAL_HOUR * 3, pendingIntent);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("updateCalendar");
        registerReceiver(alarmReceiver, intentFilter);
    }

    private void installScreen() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, intentFilter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    protected void onStart() {
        super.onStart();

        RelativeLayout relativeLayout = findViewById(R.id.layoutCalendar);
        relativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateCalender.start();
            }
        });

        ImageView imageView = findViewById(R.id.imageCalendar);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateCalender.start();
            }
        });

        installAlarm();
        installScreen();

        Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            public void run() {
                updateCalender.start();
            }
        };

        handler.postDelayed(runnable, 2000);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(alarmReceiver);
        super.onStop();
    }
}
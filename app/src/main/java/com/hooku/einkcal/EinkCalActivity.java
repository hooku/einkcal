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
        }
    };
    private volatile boolean isCalendarUpdating = false;

    private void updateCalendar() {
        EinkCalUtil.SysUtil sysUtil = new EinkCalUtil.SysUtil(getApplicationContext());
        EinkCalUtil.NetUtil netUtil = new EinkCalUtil.NetUtil(getApplicationContext());

        if (isCalendarUpdating)
            return;

        Handler delayLockHandler = new Handler();
        final Runnable runnable = new Runnable() {
            public void run() {
                sysUtil.lockScreen();
            }
        };

        final Thread updateCalenderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                isCalendarUpdating = true;

                updateStatusBar(String.format("Refreshing"));

                String urlCalendar = "https://alltobid.cf/einkcal/1.png";

                netUtil.setWifiStatus(true);

                for (int wifiAttempt = 0; wifiAttempt < 6; wifiAttempt++) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (netUtil.getWifiStatus() == EinkCalUtil.WifiStatus.WIFI_ON) {
                        String wifiInfo = netUtil.getWifiInfo();
                        updateStatusBar(wifiInfo);
                        break;
                    }
                }

                if (netUtil.getWifiStatus() == EinkCalUtil.WifiStatus.WIFI_ON) {
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
                            updateStatusBar(String.format("Update"));

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

                            delayLockHandler.postDelayed(runnable, 5000);
                        } else {
                            updateStatusBar(String.format("HTTP resp %d", responseCode));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        updateStatusBar(String.format("%s", e.getMessage()));
                    }
                } else {
                    updateStatusBar("Failed to enable WiFi");
                }
                netUtil.setWifiStatus(false);

                isCalendarUpdating = false;
            }
        });

        updateCalenderThread.start();
    }

    private void updateStatusBar(String log) {
        EinkCalUtil.SysUtil sysUtil = new EinkCalUtil.SysUtil(getApplicationContext());
        int batteryLevel = sysUtil.getBatteryLevel();
        String time = sysUtil.getTime();
        String deviceStatus = String.format("Batt:%d%% %s:%s", batteryLevel, log, time);

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

    private void installScreenStatus() {
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

        View.OnClickListener updateListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateCalendar();
            }
        };

        RelativeLayout relativeLayout = findViewById(R.id.layoutCalendar);
        relativeLayout.setOnClickListener(updateListener);

        ImageView imageView = findViewById(R.id.imageCalendar);
        imageView.setOnClickListener(updateListener);

        View.OnClickListener exitListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                System.exit(0);
            }
        };

        TextView textStatus = findViewById(R.id.textStatus);
        textStatus.setOnClickListener(exitListener);

        TextView textScreen = findViewById(R.id.textScreen);
        textScreen.setOnClickListener(exitListener);

        installAlarm();
        installScreenStatus();

        updateCalendar();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(alarmReceiver);
        super.onStop();
    }
}
package com.hooku.einkcal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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

import com.hooku.einkcal.receiver.Broadcast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

public class EinkCalActivity extends AppCompatActivity implements EinkCalInterface {
    private BroadcastReceiver broadcastReceiver;
    private volatile boolean isCalendarUpdating = false;

    @Override
    public void cbScreenOn() {
        updateScreenStatus("On");
    }

    @Override
    public void cbScreenOff() {
        updateScreenStatus("Off");
    }

    @Override
    public void cbAlarm() {
        updateCalendar();
    }

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

                updateDeviceStatus(String.format("Refreshing"));

                String urlCalendar = "https://alltobid.cf/einkcal/1.png";

                netUtil.setWifiStatus(true);

                for (int wifiAttempt = 0; wifiAttempt < 12; wifiAttempt++) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (netUtil.getWifiStatus() == EinkCalUtil.WifiStatus.WIFI_ON) {
                        String wifiInfo = netUtil.getWifiInfo();
                        updateDeviceStatus(wifiInfo);
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
                            updateDeviceStatus(String.format("Update"));

                            InputStream inputStream = httpURLConnection.getInputStream();
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            inputStream.close();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    final ImageView imageView = findViewById(R.id.imageCalendar);
                                    imageView.setImageBitmap(bitmap);
                                    refreshScreen();
                                }
                            });

                            delayLockHandler.postDelayed(runnable, 5000);
                        } else {
                            updateDeviceStatus(String.format("HTTP resp %d", responseCode));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        updateDeviceStatus(String.format("%s", e.getMessage()));
                    }
                } else {
                    updateDeviceStatus("Failed to enable WiFi");
                }
                netUtil.setWifiStatus(false);

                isCalendarUpdating = false;
            }
        });

        updateCalenderThread.start();
    }

    private void updateDeviceStatus(String status) {
        EinkCalUtil.SysUtil sysUtil = new EinkCalUtil.SysUtil(getApplicationContext());
        int batteryLevel = sysUtil.getBatteryLevel();
        String time = sysUtil.getTime();
        String deviceStatus = String.format("Batt:%d%% %s:%s", batteryLevel, status, time);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView textView = findViewById(R.id.textStatus);
                textView.setText(deviceStatus);
            }
        });
    }

    private void updateScreenStatus(String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView textView = findViewById(R.id.textScreen);
                textView.setText(status);
            }
        });
    }

    private void refreshScreen() {
        Toast.makeText(getApplicationContext(), "Updated", Toast.LENGTH_LONG).show();
    }

    private void installAlarm() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        Intent intent = new Intent(getApplicationContext(), broadcastReceiver.getClass());
        intent.setAction(Broadcast.ACTION_ALARM);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), 5000, pendingIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        broadcastReceiver = new Broadcast(this);

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

        final RelativeLayout relativeLayout = findViewById(R.id.layoutCalendar);
        relativeLayout.setOnClickListener(updateListener);

        final ImageView imageView = findViewById(R.id.imageCalendar);
        imageView.setOnClickListener(updateListener);

        View.OnClickListener exitListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                System.exit(0);
            }
        };

        final TextView textStatus = findViewById(R.id.textStatus);
        textStatus.setOnClickListener(exitListener);

        final TextView textScreen = findViewById(R.id.textScreen);
        textScreen.setOnClickListener(exitListener);

        installAlarm();

        updateCalendar();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
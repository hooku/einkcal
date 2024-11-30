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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hooku.einkcal.receiver.Alarm;
import com.hooku.einkcal.receiver.Broadcast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EinkCalActivity extends AppCompatActivity implements EinkCalInterface {
    private final String URL_CALENDAR = "https://alltobid.529000.xyz/einkcal/1.png";

    private final int REFRESH_15MIN_INTERVAL = 2;
    private final int SLEEP_HOUR_START = 1;
    private final int SLEEP_HOUR_STOP = 4;

    private final int WIFI_ON_ATTEMPT = 20;
    private final int HTTP_TIMEOUT = 15;
    private final int LOCK_DELAY = 5;
    private final int UPDATE_TIMEOUT = 40;

    private BroadcastReceiver alarmReceiver;
    private BroadcastReceiver broadcastReceiver;

    private volatile boolean isCalendarUpdating = false;
    private Thread lastUpdateThread = null;
    private Date lastUpdateTime;
    private long previousLastModified = 0;

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
        Calendar calendar = Calendar.getInstance();
        int hour24 = calendar.get(Calendar.HOUR_OF_DAY);
        if ((hour24 >= SLEEP_HOUR_START) && (hour24 <= SLEEP_HOUR_STOP)) {
            // night time
        } else {
            EinkCalUtil.SysUtil sysUtil = new EinkCalUtil.SysUtil(getApplicationContext());
            sysUtil.powerOnScreen();
        }
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

        final Thread calenderUpdateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                isCalendarUpdating = true;

                updateDeviceStatus(String.format("Refreshing"));

                try {
                    netUtil.setWifiStatus(true);
                } catch (TimeoutException e) {
                    updateDeviceStatus(e.getMessage());
                }

                for (int wifiAttempt = 0; wifiAttempt < WIFI_ON_ATTEMPT; wifiAttempt++) {
                    try {
                        Thread.sleep(DateUtils.SECOND_IN_MILLIS);
                    } catch (InterruptedException e) {
                        updateDeviceStatus(e.getMessage());
                    }
                    if (netUtil.getWifiStatus() == EinkCalUtil.WifiStatus.WIFI_ON) {
                        String wifiInfo = netUtil.getWifiInfo();
                        updateDeviceStatus(wifiInfo);
                        break;
                    }
                }

                if (netUtil.getWifiStatus() == EinkCalUtil.WifiStatus.WIFI_ON) {
                    try {
                        URL url = new URL(URL_CALENDAR);
                        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                        httpURLConnection.setConnectTimeout(HTTP_TIMEOUT * (int) DateUtils.SECOND_IN_MILLIS);
                        httpURLConnection.setDoInput(true);
                        httpURLConnection.setRequestProperty("Cache-Control", "no-cache");
                        httpURLConnection.setDefaultUseCaches(false);
                        httpURLConnection.setUseCaches(false);
                        httpURLConnection.connect();
                        int responseCode = httpURLConnection.getResponseCode();
                        long lastModified = httpURLConnection.getLastModified();

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            if (previousLastModified != lastModified) {
                                previousLastModified = lastModified;
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
                            } else {
                                updateDeviceStatus(String.format("Not changed"));
                            }

                            delayLockHandler.postDelayed(runnable, LOCK_DELAY * DateUtils.SECOND_IN_MILLIS);
                        } else {
                            updateDeviceStatus(String.format("HTTP resp %d", responseCode));
                        }
                    } catch (Exception e) {
                        updateDeviceStatus(e.getMessage());
                    }
                } else {
                    updateDeviceStatus("Failed to enable WiFi");
                }
                try {
                    netUtil.setWifiStatus(false);
                } catch (TimeoutException e) {
                    updateDeviceStatus(e.getMessage());
                }

                isCalendarUpdating = false;
            }
        });

        Date now = Calendar.getInstance().getTime();
        if (lastUpdateThread != null) {
            long diffInMilliseconds = now.getTime() - lastUpdateTime.getTime();
            long diffInSeconds = TimeUnit.SECONDS.convert(diffInMilliseconds, TimeUnit.MILLISECONDS);
            if ((diffInSeconds >= UPDATE_TIMEOUT) && lastUpdateThread.isAlive()) {
                lastUpdateThread.interrupt();
                isCalendarUpdating = false;
            }
        }
        lastUpdateTime = now;
        lastUpdateThread = calenderUpdateThread;
        calenderUpdateThread.start();
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

    private void installScreenBroadcast() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    private void installAlarm() {
        Intent intent = new Intent(getApplicationContext(), alarmReceiver.getClass());
        intent.setAction(Alarm.ACTION_ALARM);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 1);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), REFRESH_15MIN_INTERVAL * AlarmManager.INTERVAL_FIFTEEN_MINUTES, pendingIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        alarmReceiver = new Alarm(this);
        broadcastReceiver = new Broadcast(this);

        installScreenBroadcast();
        installAlarm();
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

        updateCalendar();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
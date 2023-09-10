package com.hooku.einkcal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {
    boolean isCalendarUpdating = false;

    private static long readLong(String fileName) {
        try {
            File file = new File(fileName);
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            return Long.valueOf(randomAccessFile.readLine());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private String getCPUFreq() {
        String cpuFreqFile = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
        long cpuFreq = readLong(cpuFreqFile);
        String cpuFreqMHz = Long.toString(cpuFreq / 1000) + "MHz";
        return cpuFreqMHz;
    }

    private String getWifiStatus() {
        String wifiStatus;
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            wifiStatus = String.format("%s (%d dBm, %d.%d.%d.%d)", wifiInfo.getSSID(), wifiInfo.getRssi(),
                    (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff));
        } else {
            wifiStatus = "OFF";
        }
        return wifiStatus;
    }

    private int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
        int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return batteryLevel;
    }

    private String getTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8:00"));
        Date localTime = calendar.getTime();
        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        String time = dateFormat.format(localTime);
        return time;
    }

    private String getDeviceStatus() {
        String cpuFreq = getCPUFreq();
        String wifiStatus = getWifiStatus();
        int batteryLevel = getBatteryLevel();
        String time = getTime();

        String deviceStatus = String.format("%s Wi-Fi:%s Batt:%d%% Refresh:%s", cpuFreq, wifiStatus, batteryLevel, time);
        return deviceStatus;
    }

    private void updateStatusBar(String log) {
        String deviceStatus = getDeviceStatus();
        String statusAndLog = String.format("%s %s", deviceStatus, log);

        TextView textView = (TextView) findViewById(R.id.textStatus);
        textView.setText(statusAndLog);
    }

    private void setWifiStatus(boolean isOn) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(isOn);

        ConnectivityManager connectivityManager = (ConnectivityManager)
                getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (isOn) {
            for (int ii = 0; ii < 6; ii++) {
                SystemClock.sleep(2000);

                if ((activeNetworkInfo != null) && activeNetworkInfo.isConnected()) {
                    break;
                } else {
                    updateStatusBar("Wait for Wi-Fi");
                }
            }
        } else {
            SystemClock.sleep(2000);
            updateStatusBar("");
        }
    }

    private void updateCalendar() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isCalendarUpdating) {
                    Toast.makeText(getApplicationContext(), "Already updating", Toast.LENGTH_LONG).show();
                    return;
                }
                isCalendarUpdating = true;

                String urlCalendar = "https://alltobid.cf/einkcal/1.png";
                setWifiStatus(true);
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
                        ImageView imageView = (ImageView) findViewById(R.id.imageCalendar);
                        imageView.setImageBitmap(bitmap);
                    } else {
                        String httpResp = String.format("HTTP resp %d", responseCode);
                        updateStatusBar(httpResp);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                setWifiStatus(false);
                isCalendarUpdating = false;
            }
        });
    }

    private void runSudo(String command) {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            outputStream.writeBytes(String.format("{}\n", command));
            outputStream.flush();
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            su.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void hideNavBar() {
        runSudo("settings put global policy_control immersive.navigation=*");
    }

    private void saveBattery() {
        runSudo("echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
    }

    private BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateCalendar();
        }
    };

    private void installAlarm() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 40);

        Intent intent = new Intent(this, alarmReceiver.getClass());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.setInexactRepeating(AlarmManager.RTC, calendar.getTimeInMillis(), AlarmManager.INTERVAL_HOUR * 6, pendingIntent);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("updateCalendar");
        registerReceiver(alarmReceiver, intentFilter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        hideNavBar();
        //saveBattery();
    }

    @Override
    protected void onStart() {
        super.onStart();

        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.layoutCalendar);
        relativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateCalendar();
            }
        });

        ImageView imageView = (ImageView) findViewById(R.id.imageCalendar);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateCalendar();
            }
        });

        installAlarm();

        Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            public void run() {
                updateCalendar();
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
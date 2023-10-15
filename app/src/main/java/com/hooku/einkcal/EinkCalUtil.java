package com.hooku.einkcal;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class EinkCalUtil {

    enum WifiStatus {
        WIFI_OFF,
        WIFI_ON,
        WIFI_ENABLING,
    }

    static class NetUtil {
        Context context;

        NetUtil(Context applicationContext) {
            context = applicationContext;
        }

        public WifiStatus getWifiStatus() {
            WifiStatus wifiStatus;
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

            if (wifiManager.isWifiEnabled()) {
                ConnectivityManager connectivityManager = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

                if ((activeNetworkInfo != null) && activeNetworkInfo.isConnected()) {
                    wifiStatus = WifiStatus.WIFI_ON;
                } else {
                    wifiStatus = WifiStatus.WIFI_ENABLING;
                }
            } else {
                wifiStatus = WifiStatus.WIFI_OFF;
            }

            return wifiStatus;
        }

        void setWifiStatus(boolean isOn) {
            try {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                wifiManager.setWifiEnabled(isOn);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public String getWifiInfo() {
            String wifiStatus;
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                wifiStatus = String.format("%s (%d dBm, %d.%d.%d.%d)", wifiInfo.getSSID(), wifiInfo.getRssi(),
                        (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff),
                        (ipAddress >> 24 & 0xff));
            } else {
                wifiStatus = "not enabled";
            }
            return wifiStatus;
        }
    }

    static class SysUtil {
        Context context;

        SysUtil(Context applicationContext) {
            context = applicationContext;
        }

        private long readLong(String fileName) {
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
            String cpuFreqMHz = cpuFreq / 1000 + "MHz";
            return cpuFreqMHz;
        }

        int getBatteryLevel() {
            BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return batteryLevel;
        }

        String getTime() {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8:00"));
            Date localTime = calendar.getTime();
            DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            String time = dateFormat.format(localTime);
            return time;
        }

        void lockScreen() {
            DevicePolicyManager manager = ((DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE));
            try {
                manager.lockNow();
            } catch (SecurityException e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                // dpm set-active-admin com.hooku.einkcal/.EinkCalUtil\$SysUtil\$adminReceiver
                addDeviceAdmin();
            }
        }

        private void addDeviceAdmin() {
            ComponentName deviceAdmin = new ComponentName(context, adminReceiver.class);
            Intent intent = new Intent(
                    DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "einkcal lock screen");

            context.startActivity(intent);
        }

        static public class adminReceiver extends DeviceAdminReceiver {
            @Override
            public void onDisabled(Context context, Intent intent) {
            }

            @Override
            public void onEnabled(Context context, Intent intent) {
            }
        }
    }

    static class RootUtil {
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

        void hideNavBar() {
            runSudo("settings put global policy_control immersive.navigation=*");
        }

        void saveBattery() {
            runSudo("echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
        }
    }
}

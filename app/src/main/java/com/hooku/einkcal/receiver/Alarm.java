package com.hooku.einkcal.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.hooku.einkcal.EinkCalInterface;

public class Alarm extends BroadcastReceiver {
    public static final String ACTION_ALARM = "com.hooku.einkcal.BroadcastAlarmReceiver";
    private static EinkCalInterface iface = null;

    public Alarm() {
    }

    public Alarm(EinkCalInterface iface) {
        Alarm.iface = iface;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_ALARM:
                if (iface == null) {
                    Log.w("Alarm", "iface null");
                } else {
                    Log.w("Alarm", "alarm callback");
                    iface.cbAlarm();
                }
                break;
            default:
                break;
        }
    }
}

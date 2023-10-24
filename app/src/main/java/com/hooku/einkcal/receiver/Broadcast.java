package com.hooku.einkcal.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hooku.einkcal.EinkCalInterface;

public class Broadcast extends BroadcastReceiver {
    public static final String ACTION_ALARM = "com.hooku.einkcal.BroadcastAlarmReceiver";
    private final EinkCalInterface iface;

    public Broadcast(EinkCalInterface iface) {
        this.iface = iface;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case Intent.ACTION_SCREEN_ON:
                iface.cbScreenOn();
                break;
            case Intent.ACTION_SCREEN_OFF:
                iface.cbScreenOff();
                break;
            case ACTION_ALARM:
                iface.cbAlarm();
                break;
            default:
                break;
        }
    }
}

package com.hooku.einkcal.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.hooku.einkcal.EinkCalInterface;

public class Broadcast extends BroadcastReceiver {
    private static EinkCalInterface iface = null;

    public Broadcast() {
    }

    public Broadcast(EinkCalInterface iface) {
        Broadcast.iface = iface;
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
            default:
                break;
        }
    }
}

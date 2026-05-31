package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Android system automatically binds to NotificationListenerService on boot
            // if it is enabled in settings. No manual start is required.
            Log.d("BootReceiver", "Device rebooted. Notification system will resume automatically.");
        }
    }
}
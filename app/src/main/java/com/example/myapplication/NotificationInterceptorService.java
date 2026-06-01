package com.example.myapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.NotificationEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationInterceptorService extends NotificationListenerService {
    private static final String TAG = "NotificationInterceptor";
    private static final String CHANNEL_ID = "FilterServiceChannel";
    private static final String ALARM_CHANNEL_ID = "EmergencyAlarmChannel";
    public static final String ACTION_STOP_ALARM = "com.example.myapplication.STOP_ALARM";
    private static final int ALARM_NOTIFICATION_ID = 999;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private NotificationClassifier classifier;
    
    private String lastNotificationContent = "";
    private long lastNotificationTime = 0;
    private static MediaPlayer mediaPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        classifier = new NotificationClassifier(this);
        createNotificationChannels();
        startForegroundService();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        syncExistingNotifications();
    }

    private void syncExistingNotifications() {
        executorService.execute(() -> {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    // Process silently during initial sync
                    processNotification(sbn, false);
                }
            }
        });
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "Filter Service", NotificationManager.IMPORTANCE_LOW);
                manager.createNotificationChannel(serviceChannel);

                NotificationChannel alarmChannel = new NotificationChannel(ALARM_CHANNEL_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH);
                alarmChannel.setSound(null, null);
                manager.createNotificationChannel(alarmChannel);
            }
        }
    }

    private void startForegroundService() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Notification Filter Active")
                .setContentText("Intelligent AI protection running...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_ALARM.equals(intent.getAction())) {
            stopEmergencyAlarm();
        }
        return START_STICKY;
    }

    private void stopEmergencyAlarm() {
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; } catch (Exception e) {}
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(ALARM_NOTIFICATION_ID);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        processNotification(sbn, true);
    }

    private void processNotification(StatusBarNotification sbn, boolean allowAlarm) {
        String rawPkg = sbn.getPackageName();
        if (rawPkg.equals(getPackageName()) || rawPkg.contains("netspeed") || rawPkg.equals("android")) return;

        // Resolve App Name
        String appDisplayName = getAppName(rawPkg);
        
        Bundle extras = sbn.getNotification().extras;
        String title = extras.getString(android.app.Notification.EXTRA_TITLE, "");
        
        // Comprehensive Text Capture
        CharSequence textChar = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        CharSequence bigTextChar = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT);
        String text = (textChar != null) ? textChar.toString() : (bigTextChar != null ? bigTextChar.toString() : "");

        if (title.isEmpty() && text.isEmpty()) return;
        if (text.matches("^\\d+ new messages?$")) return;

        // Strict 10s De-duplication
        String currentContent = rawPkg + "|" + title + "|" + text;
        long currentTime = System.currentTimeMillis();
        if (currentContent.equals(lastNotificationContent) && (currentTime - lastNotificationTime < 10000)) return;
        
        lastNotificationContent = currentContent;
        lastNotificationTime = currentTime;

        executorService.execute(() -> {
            try {
                boolean isAlarming = classifier.isAlarming(title, text);
                boolean isSpam = !isAlarming && classifier.isSpam(title, text);
                String summary = classifier.summarize(title, text);

                if (isAlarming && allowAlarm) playEmergencyAlarm(title, text);
                else if (isSpam) cancelNotification(sbn.getKey());

                // Save to DB: appDisplayName goes into packageName field for UI mapping
                NotificationEntity entity = new NotificationEntity(
                        appDisplayName, title, text, summary, System.currentTimeMillis(), isSpam);
                AppDatabase.getInstance(getApplicationContext()).notificationDao().insert(entity);

                Intent refresh = new Intent("com.example.myapplication.REFRESH_UI");
                refresh.setPackage(getPackageName());
                sendBroadcast(refresh);
            } catch (Exception e) {
                Log.e(TAG, "Error", e);
            }
        });
    }

    private String getAppName(String packageName) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return (label != null) ? label.toString() : packageName;
        } catch (Exception e) {
            // Fallback for common apps if system resolution fails
            if (packageName.contains("whatsapp")) return "WhatsApp";
            if (packageName.contains("linkedin")) return "LinkedIn";
            if (packageName.contains("snapchat")) return "Snapchat";
            if (packageName.contains("instagram")) return "Instagram";
            if (packageName.contains("facebook")) return "Facebook";
            if (packageName.contains("youtube")) return "YouTube";
            
            // Extract the last part of package name as a last resort
            String[] parts = packageName.split("\\.");
            if (parts.length > 0) {
                String name = parts[parts.length - 1];
                return name.substring(0, 1).toUpperCase() + name.substring(1);
            }
            return packageName;
        }
    }

    private void playEmergencyAlarm(String title, String text) {
        try {
            if (mediaPlayer != null) stopEmergencyAlarm();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            showStopAlarmNotification(title, text);
        } catch (Exception e) {}
    }

    private void showStopAlarmNotification(String title, String text) {
        Intent intent = new Intent(this, NotificationInterceptorService.class);
        intent.setAction(ACTION_STOP_ALARM);
        PendingIntent pending = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle("EMERGENCY ALERT: " + title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(R.drawable.ic_launcher_foreground, "STOP ALARM", pending)
                .setOngoing(true)
                .build();
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(ALARM_NOTIFICATION_ID, notification);
    }
}
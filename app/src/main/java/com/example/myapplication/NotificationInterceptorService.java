package com.example.myapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.Ringtone;
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
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private NotificationClassifier classifier;

    @Override
    public void onCreate() {
        super.onCreate();
        classifier = new NotificationClassifier(this);
        createNotificationChannel();
        startForegroundService();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Notification Filter Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Notification Filter Active")
                .setContentText("Protecting you from promotional spam...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Skip our own service notification
        if (sbn.getPackageName().equals(getPackageName())) return;

        String packageName = sbn.getPackageName();
        Bundle extras = sbn.getNotification().extras;
        String title = extras.getString(android.app.Notification.EXTRA_TITLE, "No Title");
        CharSequence textChar = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        String text = textChar != null ? textChar.toString() : "";

        Log.d(TAG, "Intercepted: " + packageName + " | " + title);

        executorService.execute(() -> {
            boolean isAlarming = classifier.isAlarming(title, text);
            boolean isSpam = !isAlarming && classifier.isSpam(title, text);
            String summary = classifier.summarize(text);

            if (isAlarming) {
                Log.d(TAG, "URGENT MESSAGE DETECTED! Playing alarm...");
                playEmergencyAlarm();
            } else if (isSpam) {
                Log.d(TAG, "AI Action: Suppressing spam from " + packageName);
                cancelNotification(sbn.getKey());
            }

            // Archive to Room Database with the AI-generated summary
            NotificationEntity entity = new NotificationEntity(
                    packageName, title, text, summary, System.currentTimeMillis(), isSpam);
            AppDatabase.getInstance(getApplicationContext()).notificationDao().insert(entity);
        });
    }

    private void playEmergencyAlarm() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
            
            // Auto-stop after 5 seconds to prevent annoyance if the user is busy
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r::stop, 5000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY;
    }
}
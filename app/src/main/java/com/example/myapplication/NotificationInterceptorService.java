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
        Log.d(TAG, "Service Created");
        classifier = new NotificationClassifier(this);
        createNotificationChannels();
        startForegroundService();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification Listener Connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Notification Listener Disconnected");
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                // Persistent Service Channel
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "Notification Filter Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                manager.createNotificationChannel(serviceChannel);

                // Emergency Alarm Channel
                NotificationChannel alarmChannel = new NotificationChannel(
                        ALARM_CHANNEL_ID,
                        "Emergency Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                );
                alarmChannel.setSound(null, null);
                alarmChannel.enableVibration(true);
                alarmChannel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(alarmChannel);
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
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
                Log.d(TAG, "Emergency alarm stopped by user.");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping media player", e);
            }
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(ALARM_NOTIFICATION_ID);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Log.d(TAG, "Received notification from: " + packageName);

        if (packageName.equals(getPackageName()) || 
            packageName.contains("netspeed") || 
            packageName.equals("android") || 
            packageName.equals("com.android.systemui")) {
            return;
        }

        Bundle extras = sbn.getNotification().extras;
        String title = extras.getString(android.app.Notification.EXTRA_TITLE, "");
        
        CharSequence textChar = extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        CharSequence bigTextChar = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT);
        CharSequence subTextChar = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT);
        
        String tempText = "";
        if (textChar != null) tempText = textChar.toString();
        else if (bigTextChar != null) tempText = bigTextChar.toString();
        else if (subTextChar != null) tempText = subTextChar.toString();
        
        final String text = tempText;

        // 5. Filter out aggregate noise and low-value updates
        if (title.isEmpty() && text.isEmpty()) return;
        
        // Filter out "X new messages" (WhatsApp noise seen in your screenshot)
        if (text.matches("^\\d+ new messages?$") || text.contains("Checking for new messages")) {
            return;
        }

        // 6. Robust De-duplication (Improved for WhatsApp Group variations)
        // Extract base title (remove sender name after colon in groups)
        String baseTitle = title.split(":")[0].trim();
        String currentContent = packageName + "|" + baseTitle + "|" + text;
        long currentTime = System.currentTimeMillis();
        
        boolean isAlarming = classifier.isAlarming(title, text);
        
        // Use a 5s window for group chat de-duplication to prevent the double cards
        if (!isAlarming && currentContent.equals(lastNotificationContent) && (currentTime - lastNotificationTime < 5000)) {
            return;
        }
        
        lastNotificationContent = currentContent;
        lastNotificationTime = currentTime;

        Log.d(TAG, "INTERCEPTING: " + title);

        executorService.execute(() -> {
            try {
                boolean isSpam = !isAlarming && classifier.isSpam(title, text);
                String cleanedText = cleanNotificationText(title, text);
                String summary = classifier.summarize(title, cleanedText);

                if (isAlarming) {
                    playEmergencyAlarm(title, text);
                } else if (isSpam) {
                    cancelNotification(sbn.getKey());
                }

                NotificationEntity entity = new NotificationEntity(
                        packageName, title, text, summary, System.currentTimeMillis(), isSpam);
                AppDatabase.getInstance(getApplicationContext()).notificationDao().insert(entity);

                Intent refreshIntent = new Intent("com.example.myapplication.REFRESH_UI");
                refreshIntent.setPackage(getPackageName());
                sendBroadcast(refreshIntent);
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing", e);
            }
        });
    }

    private String cleanNotificationText(String title, String text) {
        if (text == null || text.isEmpty()) return "";
        if (title != null && text.startsWith(title)) {
            String cleaned = text.replaceFirst(title + "[:\\s]*", "");
            return cleaned.isEmpty() ? text : cleaned;
        }
        return text.replaceAll("^[\\w\\s]+:\\s*", "");
    }

    private void playEmergencyAlarm(String title, String text) {
        try {
            if (mediaPlayer != null) {
                stopEmergencyAlarm();
            }

            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getApplicationContext(), alarmUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            showStopAlarmNotification(title, text);
            Log.d(TAG, "Emergency alarm started with looping.");
        } catch (Exception e) {
            Log.e(TAG, "MediaPlayer error", e);
        }
    }

    private void showStopAlarmNotification(String title, String text) {
        Intent stopIntent = new Intent(this, NotificationInterceptorService.class);
        stopIntent.setAction(ACTION_STOP_ALARM);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification notification = new NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
                .setContentTitle("EMERGENCY ALERT: " + title)
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_launcher_foreground, "STOP ALARM", stopPendingIntent)
                .setOngoing(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(ALARM_NOTIFICATION_ID, notification);
            Log.d(TAG, "Emergency notification shown.");
        }
    }
}
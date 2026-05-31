package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.NotificationEntity;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private NotificationAdapter adapter;
    private RecyclerView recyclerView;
    private TextView textEmpty, textCollectiveSummary;
    private View cardCollectiveSummary;
    private Button btnSettings;
    private TabLayout tabLayout;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NotificationClassifier classifier;
    private SharedPreferences prefs;

    private static final long FOUR_HOURS = 4 * 60 * 60 * 1000L;
    private static final long TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000L;
    
    private static final String PREF_CLEAR_RECENT = "clear_recent";
    private static final String PREF_CLEAR_24H = "clear_24h";
    private static final String PREF_CLEAR_SPAM = "clear_spam";

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Refresh UI broadcast received");
            loadNotifications();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NotificationPrefs", MODE_PRIVATE);
        classifier = new NotificationClassifier(this);
        recyclerView = findViewById(R.id.recycler_view);
        textEmpty = findViewById(R.id.text_empty);
        textCollectiveSummary = findViewById(R.id.text_collective_summary);
        cardCollectiveSummary = findViewById(R.id.card_collective_summary);
        btnSettings = findViewById(R.id.btn_settings);
        tabLayout = findViewById(R.id.tab_layout);

        adapter = new NotificationAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadNotifications();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadNotifications();
        startAutoRefresh();
    }

    private void startAutoRefresh() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadNotifications();
                mainHandler.postDelayed(this, 30000); // Every 30 seconds
            }
        }, 30000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, new IntentFilter("com.example.myapplication.REFRESH_UI"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshReceiver, new IntentFilter("com.example.myapplication.REFRESH_UI"));
        }
        
        updateSettingsButton();
        loadNotifications();
    }

    private void updateSettingsButton() {
        if (isNotificationServiceEnabled()) {
            btnSettings.setVisibility(View.GONE);
        } else {
            btnSettings.setVisibility(View.VISIBLE);
            btnSettings.setText("Grant Access");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(refreshReceiver);
        } catch (Exception ignored) {}
    }

    private void loadNotifications() {
        int selectedTab = tabLayout.getSelectedTabPosition();
        executorService.execute(() -> {
            try {
                List<NotificationEntity> notifications;
                long currentTime = System.currentTimeMillis();

                if (selectedTab == 0) { // Recent
                    long lastCleared = prefs.getLong(PREF_CLEAR_RECENT, 0);
                    long since = Math.max(lastCleared, currentTime - FOUR_HOURS);
                    notifications = AppDatabase.getInstance(this).notificationDao().getNormalNotificationsSince(since);
                } else if (selectedTab == 1) { // 24 Hours
                    long lastCleared = prefs.getLong(PREF_CLEAR_24H, 0);
                    long since = Math.max(lastCleared, currentTime - TWENTY_FOUR_HOURS);
                    notifications = AppDatabase.getInstance(this).notificationDao().getNormalNotificationsSince(since);
                } else { // Spam Digest
                    long lastCleared = prefs.getLong(PREF_CLEAR_SPAM, 0);
                    notifications = AppDatabase.getInstance(this).notificationDao().getSpamNotificationsSince(lastCleared);
                }

                mainHandler.post(() -> {
                    if (notifications.isEmpty()) {
                        textEmpty.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                        cardCollectiveSummary.setVisibility(View.GONE);
                    } else {
                        textEmpty.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.setNotifications(notifications);
                        
                        if (selectedTab == 2) {
                            cardCollectiveSummary.setVisibility(View.VISIBLE);
                            List<String> spamTexts = new ArrayList<>();
                            for (NotificationEntity n : notifications) spamTexts.add(n.text);
                            textCollectiveSummary.setText(classifier.generateCollectiveSummary(spamTexts));
                        } else {
                            cardCollectiveSummary.setVisibility(View.GONE);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading notifications", e);
            }
        });
    }

    public void clearDigest(View view) {
        int selectedTab = tabLayout.getSelectedTabPosition();
        long now = System.currentTimeMillis();
        
        SharedPreferences.Editor editor = prefs.edit();
        if (selectedTab == 0) editor.putLong(PREF_CLEAR_RECENT, now);
        else if (selectedTab == 1) editor.putLong(PREF_CLEAR_24H, now);
        else editor.putLong(PREF_CLEAR_SPAM, now);
        editor.apply();
        
        loadNotifications();
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
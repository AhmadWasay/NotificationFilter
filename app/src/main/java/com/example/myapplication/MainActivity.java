package com.example.myapplication;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
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
    private NotificationAdapter adapter;
    private RecyclerView recyclerView;
    private TextView textEmpty, textCollectiveSummary;
    private View cardCollectiveSummary;
    private Button btnSettings;
    private TabLayout tabLayout;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NotificationClassifier classifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isNotificationServiceEnabled()) {
            btnSettings.setVisibility(View.GONE);
        } else {
            btnSettings.setVisibility(View.VISIBLE);
            btnSettings.setText("Grant Access");
        }
        loadNotifications();
    }

    private void loadNotifications() {
        boolean isSpamTab = tabLayout.getSelectedTabPosition() == 0;
        
        executorService.execute(() -> {
            List<NotificationEntity> notifications;
            if (isSpamTab) {
                notifications = AppDatabase.getInstance(this).notificationDao().getSpamNotifications();
            } else {
                notifications = AppDatabase.getInstance(this).notificationDao().getNormalNotifications();
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
                    
                    if (isSpamTab) {
                        cardCollectiveSummary.setVisibility(View.VISIBLE);
                        List<String> spamTexts = new ArrayList<>();
                        for (NotificationEntity n : notifications) spamTexts.add(n.text);
                        textCollectiveSummary.setText(classifier.generateCollectiveSummary(spamTexts));
                    } else {
                        cardCollectiveSummary.setVisibility(View.GONE);
                    }
                }
            });
        });
    }

    public void clearDigest(View view) {
        executorService.execute(() -> {
            AppDatabase.getInstance(this).notificationDao().deleteAll();
            loadNotifications();
        });
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
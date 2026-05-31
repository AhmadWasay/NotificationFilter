package com.example.myapplication.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(NotificationEntity notification);

    @Query("SELECT * FROM notifications WHERE isSpam = 1 AND timestamp >= :since ORDER BY timestamp DESC")
    List<NotificationEntity> getSpamNotificationsSince(long since);

    @Query("SELECT * FROM notifications WHERE isSpam = 0 AND timestamp >= :since ORDER BY timestamp DESC")
    List<NotificationEntity> getNormalNotificationsSince(long since);

    @Query("DELETE FROM notifications WHERE timestamp < :threshold")
    void deleteOldNotifications(long threshold);

    @Query("DELETE FROM notifications")
    void deleteAll();
}
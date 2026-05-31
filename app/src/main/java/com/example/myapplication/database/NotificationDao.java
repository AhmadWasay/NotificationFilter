package com.example.myapplication.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(NotificationEntity notification);

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    List<NotificationEntity> getAllNotifications();

    @Query("SELECT * FROM notifications WHERE isSpam = 1 ORDER BY timestamp DESC")
    List<NotificationEntity> getSpamNotifications();

    @Query("SELECT * FROM notifications WHERE isSpam = 0 ORDER BY timestamp DESC")
    List<NotificationEntity> getNormalNotifications();

    @Query("DELETE FROM notifications")
    void deleteAll();
}
package com.example.myapplication.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class NotificationEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    public String packageName;
    public String title;
    public String text;
    public String summary;
    public long timestamp;
    public boolean isSpam;

    public NotificationEntity(String packageName, String title, String text, String summary, long timestamp, boolean isSpam) {
        this.packageName = packageName;
        this.title = title;
        this.text = text;
        this.summary = summary;
        this.timestamp = timestamp;
        this.isSpam = isSpam;
    }
}
package com.example.myapplication;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.database.NotificationEntity;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    private List<NotificationEntity> notifications = new ArrayList<>();

    public void setNotifications(List<NotificationEntity> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationEntity notification = notifications.get(position);
        holder.textPackage.setText(notification.packageName);
        holder.textTitle.setText(notification.title);
        holder.textContent.setText(notification.text);
        
        if (notification.summary != null && !notification.summary.isEmpty()) {
            holder.textSummary.setText("AI Summary: " + notification.summary);
            holder.textSummary.setVisibility(View.VISIBLE);
        } else {
            holder.textSummary.setVisibility(View.GONE);
        }

        if (notification.isSpam) {
            holder.textStatus.setText("SPAM");
            holder.textStatus.setVisibility(View.VISIBLE);
        } else {
            holder.textStatus.setText("NORMAL");
            holder.textStatus.setVisibility(View.GONE); // Or change color for normal
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textPackage, textTitle, textContent, textStatus, textSummary;

        ViewHolder(View itemView) {
            super(itemView);
            textPackage = itemView.findViewById(R.id.text_package);
            textTitle = itemView.findViewById(R.id.text_title);
            textContent = itemView.findViewById(R.id.text_content);
            textStatus = itemView.findViewById(R.id.text_status);
            textSummary = itemView.findViewById(R.id.text_summary);
        }
    }
}
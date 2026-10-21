package com.example.myecowatch1.ui.alerts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.Notification;
import com.example.myecowatch1.utils.NotificationDateFormatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.ViewHolder> {
    private List<Notification> list = new ArrayList<>();
    private final OnNotifListener listener;
    private boolean isSelectionMode = false;
    private final Set<Integer> selectedIds = new HashSet<>();
    private String currentTab = "All";

    public interface OnNotifListener {
        void onNotificationClick(Notification notification);
        void onDelete(Notification notification);
        void onRestore(Notification notification);
        void onSelectionChanged(int count);
    }

    public NotificationsAdapter(OnNotifListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setList(List<Notification> list) {
        this.list = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setTab(String tab) {
        this.currentTab = tab;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean mode) {
        isSelectionMode = mode;
        if (!mode) selectedIds.clear();
        notifyDataSetChanged();
    }

    public List<Integer> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public void selectAll() {
        selectedIds.clear();
        for (Notification notification : list) selectedIds.add(notification.getId());
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedIds.size());
    }

    public void unselectAll() {
        selectedIds.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(0);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = list.get(position);
        holder.title.setText(notification.getTitle() == null
                ? "EcoWatch" : notification.getTitle());
        holder.message.setText(notification.getMessage() == null
                ? "Your report was updated." : notification.getMessage());
        holder.time.setText(NotificationDateFormatter.format(notification.getCreatedAt()));

        if (notification.getReportId() == null || notification.getReportId().trim().isEmpty()) {
            holder.reportId.setVisibility(View.GONE);
        } else {
            holder.reportId.setVisibility(View.VISIBLE);
            holder.reportId.setText("Report: " + notification.getReportId());
        }

        boolean unread = !notification.isRead() && !"Trash".equals(currentTab);
        holder.unreadDot.setVisibility(unread ? View.VISIBLE : View.GONE);
        holder.itemView.setAlpha(unread ? 1f : 0.78f);

        holder.checkbox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.checkbox.setChecked(selectedIds.contains(notification.getId()));
        holder.checkbox.setOnClickListener(v -> toggleSelection(notification));

        if ("Trash".equals(currentTab)) {
            holder.restoreBtn.setVisibility(View.VISIBLE);
            holder.deleteBtn.setVisibility(View.VISIBLE);
        } else {
            holder.restoreBtn.setVisibility(View.GONE);
            holder.deleteBtn.setVisibility(View.VISIBLE);
        }

        holder.deleteBtn.setOnClickListener(v -> listener.onDelete(notification));
        holder.restoreBtn.setOnClickListener(v -> listener.onRestore(notification));
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(notification);
            } else {
                listener.onNotificationClick(notification);
            }
        });
    }

    private void toggleSelection(Notification notification) {
        if (selectedIds.contains(notification.getId())) {
            selectedIds.remove(notification.getId());
        } else {
            selectedIds.add(notification.getId());
        }
        int position = list.indexOf(notification);
        if (position >= 0) notifyItemChanged(position);
        listener.onSelectionChanged(selectedIds.size());
    }

    @Override
    public long getItemId(int position) {
        Notification notification = list.get(position);
        return notification.getId() > 0
                ? notification.getId()
                : com.example.myecowatch1.data.repository.NotificationRepository
                        .stableNotificationId(notification.getMongoId());
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView message;
        final TextView reportId;
        final TextView time;
        final ImageView icon;
        final View unreadDot;
        final CheckBox checkbox;
        final ImageButton deleteBtn;
        final ImageButton restoreBtn;

        ViewHolder(View v) {
            super(v);
            title = v.findViewById(R.id.notifTitle);
            message = v.findViewById(R.id.notifMessage);
            reportId = v.findViewById(R.id.notifReportId);
            time = v.findViewById(R.id.notifTime);
            icon = v.findViewById(R.id.notifIcon);
            unreadDot = v.findViewById(R.id.notifUnreadDot);
            checkbox = v.findViewById(R.id.notifCheckbox);
            deleteBtn = v.findViewById(R.id.deleteNotifBtn);
            restoreBtn = v.findViewById(R.id.restoreNotifBtn);
        }
    }
}

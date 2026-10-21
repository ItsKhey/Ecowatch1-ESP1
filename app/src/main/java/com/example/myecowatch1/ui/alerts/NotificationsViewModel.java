package com.example.myecowatch1.ui.alerts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.myecowatch1.data.models.Notification;
import com.example.myecowatch1.data.repository.NotificationRepository;

import java.util.List;

public class NotificationsViewModel extends AndroidViewModel {
    private final NotificationRepository repository;
    private final LiveData<List<Notification>> allNotifications;
    private final LiveData<List<Notification>> adminNotifications;
    private final LiveData<List<Notification>> trashNotifications;
    private final LiveData<Integer> unreadCount;
    private final LiveData<Boolean> loading;
    private final LiveData<String> error;

    public NotificationsViewModel(@NonNull Application application) {
        super(application);
        repository = NotificationRepository.getInstance(application);
        allNotifications = repository.getAllNotifications();
        adminNotifications = repository.getAdminNotifications();
        trashNotifications = repository.getTrashNotifications();
        unreadCount = repository.getUnreadCount();
        loading = repository.getLoading();
        error = repository.getError();
    }

    public LiveData<List<Notification>> getAllNotifications() { return allNotifications; }
    public LiveData<List<Notification>> getAdminNotifications() { return adminNotifications; }
    public LiveData<List<Notification>> getTrashNotifications() { return trashNotifications; }
    public LiveData<Integer> getUnreadCount() { return unreadCount; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void refresh() { repository.fetchNotifications(); }
    public void markAsRead(Notification notification) { repository.markAsRead(notification); }
    public void markAllAsRead() { repository.markAllAsRead(); }
    public void softDelete(Notification notification) { repository.softDelete(notification); }
    public void restore(Notification notification) { repository.restore(notification); }
    public void permanentDelete(Notification notification) { repository.permanentDelete(notification); }
}

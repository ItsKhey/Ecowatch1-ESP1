package com.example.myecowatch1;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.myecowatch1.data.repository.NotificationRepository;
import com.example.myecowatch1.notifications.FcmTokenSyncScheduler;
import com.example.myecowatch1.utils.SessionManager;

public class MyEcoWatchApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Apply saved theme preference
        SessionManager sessionManager = new SessionManager(this);
        int themeMode = sessionManager.getThemeMode();
        AppCompatDelegate.setDefaultNightMode(themeMode);

        // Create notification channel for Android 8.0+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    NotificationRepository.CHANNEL_ID,
                    "EcoWatch Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for EcoWatch updates and report completions");
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        if (sessionManager.isLoggedIn()) {
            FcmTokenSyncScheduler.syncCurrentToken(this);
        }
    }
}

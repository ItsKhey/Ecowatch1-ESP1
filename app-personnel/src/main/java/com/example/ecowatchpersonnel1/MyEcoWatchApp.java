package com.example.ecowatchpersonnel1;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationRepository;
import com.example.ecowatchpersonnel1.notifications.FcmTokenSyncScheduler;
import com.example.ecowatchpersonnel1.utils.SessionManager;

/**
 * Process-wide setup for the Personnel app: theme, the notification channel the
 * backend pushes into, and re-binding this device's FCM token after a cold start.
 */
public class MyEcoWatchApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        SessionManager sessionManager = new SessionManager(this);
        AppCompatDelegate.setDefaultNightMode(sessionManager.getThemeMode());

        createNotificationChannel();

        if (sessionManager.isLoggedIn()) {
            FcmTokenSyncScheduler.syncCurrentToken(this);
        }
    }

    /**
     * Channel id must match {@code PERSONNEL_CHANNEL_ID} in the backend's
     * fcmService and the app manifest's click action wiring. Sharing any of them
     * would let a personnel push land in the resident or admin channel.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                PersonnelNotificationRepository.CHANNEL_ID,
                "EcoWatch Task Notifications",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Assignments and updates for your cleaning tasks");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}

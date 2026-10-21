package com.example.myecowatch1.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.myecowatch1.data.repository.NotificationRepository;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class EcoWatchFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        FcmTokenSyncScheduler.enqueue(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        NotificationRepository.getInstance(getApplicationContext())
                .upsertPushNotification(message.getData());
    }

    @Override
    public void onDeletedMessages() {
        // The next authenticated refresh reconciles any server-side deletion.
        Log.d("EcoWatchFCM", "Deleted FCM messages will be reconciled on refresh.");
    }
}

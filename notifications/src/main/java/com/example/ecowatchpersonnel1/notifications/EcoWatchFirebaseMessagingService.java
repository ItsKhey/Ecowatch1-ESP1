package com.example.ecowatchpersonnel1.notifications;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationRepository;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives assignment pushes for the signed-in cleaner.
 *
 * <p>The backend writes the durable in-app row before it pushes, so this service
 * never has to create anything server-side; it either refreshes the token or
 * surfaces the row that already exists.
 */
public class EcoWatchFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        FcmTokenSyncScheduler.enqueue(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        PersonnelNotificationRepository.getInstance(getApplicationContext())
                .upsertPushNotification(message.getData());
    }

    @Override
    public void onDeletedMessages() {
        Log.d("EcoWatchPersonnelFCM",
                "Deleted FCM messages will be reconciled on the next refresh.");
    }
}

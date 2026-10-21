package com.example.ecowatchpersonnel1.notifications;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.example.ecowatchpersonnel1.utils.SessionManager;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.concurrent.TimeUnit;

/**
 * Queues the device token for upload to {@code POST /api/personnel/fcm-token}.
 *
 * <p>Single-flight and retrying: a token can arrive before the cleaner has signed
 * in, or while the network is down, and both must not lose the binding.
 */
public final class FcmTokenSyncScheduler {

    private static final String UNIQUE_WORK = "ecowatch-personnel-fcm-token-sync";

    private FcmTokenSyncScheduler() {
    }

    public static void enqueue(@NonNull Context context, @NonNull String token) {
        if (token.trim().isEmpty()) return;
        if (!new SessionManager(context).isLoggedIn()) return;

        Data input = new Data.Builder()
                .putString(FcmTokenSyncWorker.INPUT_TOKEN, token)
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FcmTokenSyncWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request);
    }

    /**
     * Fetches the current token and queues it.
     *
     * <p>Firebase throws when no {@code google-services.json} matches this app id,
     * which is a supported state for a local build; in that case the in-app list
     * and its REST polling still work, so this only logs.
     */
    public static void syncCurrentToken(@NonNull Context context) {
        if (!new SessionManager(context).isLoggedIn()) return;
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            enqueue(context, task.getResult());
                        } else {
                            Log.w("FcmTokenSync",
                                    "Firebase token unavailable; add a matching "
                                            + "app-personnel/google-services.json.",
                                    task.getException());
                        }
                    });
        } catch (RuntimeException error) {
            Log.w("FcmTokenSync",
                    "Firebase is not configured; in-app notifications remain active.",
                    error);
        }
    }
}

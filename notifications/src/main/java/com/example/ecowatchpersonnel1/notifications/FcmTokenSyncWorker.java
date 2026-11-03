package com.example.ecowatchpersonnel1.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationRepository;
import com.example.ecowatchpersonnel1.utils.SessionManager;

/** Uploads the FCM token for the signed-in cleaner, retrying while it matters. */
public class FcmTokenSyncWorker extends Worker {

    static final String INPUT_TOKEN = "fcm_token";

    public FcmTokenSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SessionManager sessionManager = new SessionManager(getApplicationContext());
        if (!sessionManager.isLoggedIn()) return Result.success();

        String token = getInputData().getString(INPUT_TOKEN);
        if (token == null || token.trim().isEmpty()) return Result.failure();

        try {
            boolean updated = PersonnelNotificationRepository
                    .getInstance(getApplicationContext())
                    .registerFcmTokenBlocking(token);
            return updated ? Result.success() : Result.retry();
        } catch (Exception error) {
            return getRunAttemptCount() < 5 ? Result.retry() : Result.failure();
        }
    }
}

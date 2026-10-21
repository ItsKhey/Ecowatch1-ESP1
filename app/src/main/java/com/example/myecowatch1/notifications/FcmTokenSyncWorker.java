package com.example.myecowatch1.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.myecowatch1.data.repository.NotificationRepository;
import com.example.myecowatch1.utils.SessionManager;

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
            boolean updated = NotificationRepository
                    .getInstance(getApplicationContext())
                    .updateFcmTokenBlocking(token);
            return updated ? Result.success() : Result.retry();
        } catch (Exception error) {
            return getRunAttemptCount() < 5 ? Result.retry() : Result.failure();
        }
    }
}

package com.example.myecowatch1.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myecowatch1.data.local.AppDatabase;
import com.example.myecowatch1.data.local.NotificationSettingDao;
import com.example.myecowatch1.data.models.NotificationSetting;
import com.example.myecowatch1.data.remote.ApiService;
import com.example.myecowatch1.data.remote.NotificationPreferences;
import com.example.myecowatch1.data.remote.NotificationPreferencesRequest;
import com.example.myecowatch1.data.remote.NotificationPreferencesResponse;
import com.example.myecowatch1.data.remote.RetrofitClient;
import com.example.myecowatch1.utils.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Keeps notification preferences in step between the device and the server.
 *
 * <p>The local Room row stays the source of truth for the settings screen so it
 * renders instantly and works offline, but the server owns the real record. That
 * matters for two reasons the previous device-local-only arrangement could not
 * give: the choices survive a reinstall and agree across a resident's devices,
 * and the backend can finally see them when deciding who to push to.
 *
 * <p>Writes are fire-and-forget. A failed sync leaves the local value in place
 * and is retried on the next launch, because losing a preference is less bad
 * than blocking the resident from turning something off.
 */
public class NotificationPreferenceRepository {

    private static final String TAG = "NotifPrefRepository";

    private final ApiService apiService;
    private final NotificationSettingDao dao;
    private final SessionManager sessionManager;
    private final ExecutorService executor;

    public NotificationPreferenceRepository(@NonNull Application application) {
        this.apiService = RetrofitClient.getApiService(application);
        this.dao = AppDatabase.getInstance(application).notificationSettingDao();
        this.sessionManager = new SessionManager(application);
        this.executor = Executors.newSingleThreadExecutor();
    }

    private String residentId() {
        String residentId = sessionManager.fetchResidentId();
        if (!sessionManager.isLoggedIn() || residentId == null || residentId.trim().isEmpty()) {
            return null;
        }
        return residentId;
    }

    /**
     * Pulls the server's record and overwrites the local row.
     *
     * <p>Called when the settings screen opens. The server wins, so a change made
     * on another device or before a reinstall is reflected rather than silently
     * reverted by stale local state.
     */
    public void syncFromServer(@Nullable Runnable onComplete) {
        final String residentId = residentId();
        if (residentId == null) {
            notifyComplete(onComplete);
            return;
        }

        apiService.getNotificationPreferences().enqueue(new Callback<NotificationPreferencesResponse>() {
            @Override
            public void onResponse(Call<NotificationPreferencesResponse> call,
                                   Response<NotificationPreferencesResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    applyRemote(residentId, response.body().getPreferences());
                } else {
                    Log.w(TAG, "Could not read notification preferences (code "
                            + response.code() + "); keeping the local values.");
                }
                notifyComplete(onComplete);
            }

            @Override
            public void onFailure(Call<NotificationPreferencesResponse> call, Throwable t) {
                Log.w(TAG, "Notification preference read failed: " + t.getMessage());
                notifyComplete(onComplete);
            }
        });
    }

    private void applyRemote(final String residentId, final NotificationPreferences remote) {
        executor.execute(() -> {
            NotificationSetting settings = dao.getSettingsByResidentId(residentId);
            if (settings == null) settings = new NotificationSetting(residentId);

            settings.setMasterEnabled(remote.isMasterEnabled());
            settings.setReportStatusUpdates(remote.isReportStatusUpdates());
            settings.setReportCompleted(remote.isReportCompleted());
            settings.setReportDenied(remote.isReportDenied());
            settings.setCenroAnnouncements(remote.isCenroAnnouncements());
            settings.setSecurityAlerts(remote.isSecurityAlerts());
            settings.setSoundEnabled(remote.isSoundEnabled());
            settings.setVibrationEnabled(remote.isVibrationEnabled());
            // Null when the resident never picked a sound, which means the
            // device default rather than an empty custom tone.
            settings.setSoundUri(remote.getSoundUri() == null ? "" : remote.getSoundUri());
            dao.saveSettings(settings);
        });
    }

    /** Pushes a single changed toggle so the server can act on it. */
    public void pushCenroAnnouncements(boolean enabled) {
        push(NotificationPreferencesRequest.forCenroAnnouncements(enabled));
    }

    public void pushMasterEnabled(boolean enabled) {
        push(NotificationPreferencesRequest.forMasterEnabled(enabled));
    }

    public void pushReportStatusUpdates(boolean enabled) {
        push(NotificationPreferencesRequest.forReportStatusUpdates(enabled));
    }

    public void pushReportCompleted(boolean enabled) {
        push(NotificationPreferencesRequest.forReportCompleted(enabled));
    }

    public void pushReportDenied(boolean enabled) {
        push(NotificationPreferencesRequest.forReportDenied(enabled));
    }

    public void pushSecurityAlerts(boolean enabled) {
        push(NotificationPreferencesRequest.forSecurityAlerts(enabled));
    }

    /**
     * Stores the chosen ringtone, or clears it when {@code uri} is blank.
     *
     * <p>Synced so a picked sound survives a reinstall and matches on the
     * resident's other devices.
     */
    public void pushSoundUri(@Nullable String uri) {
        push(NotificationPreferencesRequest.forSoundUri(uri));
    }

    /**
     * Sends one toggle. Failures are logged only: the local row is already saved
     * and the next {@link #syncFromServer} reconciles, so a dropped request must
     * not surface as an error the resident has to act on.
     */
    private void push(NotificationPreferencesRequest request) {
        if (residentId() == null) return;

        apiService.updateNotificationPreferences(request)
                .enqueue(new Callback<NotificationPreferencesResponse>() {
                    @Override
                    public void onResponse(Call<NotificationPreferencesResponse> call,
                                           Response<NotificationPreferencesResponse> response) {
                        if (!response.isSuccessful()) {
                            Log.w(TAG, "Notification preference write rejected (code "
                                    + response.code() + "); it will be retried on next launch.");
                        }
                    }

                    @Override
                    public void onFailure(Call<NotificationPreferencesResponse> call, Throwable t) {
                        Log.w(TAG, "Notification preference write failed: " + t.getMessage());
                    }
                });
    }

    private void notifyComplete(@Nullable final Runnable onComplete) {
        if (onComplete == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
    }
}

package com.example.myecowatch1.data.repository;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.local.AppDatabase;
import com.example.myecowatch1.data.local.NotificationDao;
import com.example.myecowatch1.data.local.NotificationTombstoneDao;
import com.example.myecowatch1.data.models.Notification;
import com.example.myecowatch1.data.models.NotificationTombstone;
import com.example.myecowatch1.data.remote.ApiService;
import com.example.myecowatch1.data.remote.FcmTokenRequest;
import com.example.myecowatch1.data.remote.RetrofitClient;
import com.example.myecowatch1.ui.home.DashboardActivity;
import com.example.myecowatch1.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Backend-backed notification repository. Room is a display cache only; the
 * protected API remains authoritative for ordering, read state, and trash.
 */
public final class NotificationRepository {
    public static final String CHANNEL_ID = "ecowatch_notifications";

    /** Backend notification type for a published CENRO announcement. */
    public static final String ANNOUNCEMENT_TYPE = "ANNOUNCEMENT";

    /**
     * Types the "From Admin" tab collects.
     *
     * <p>ANNOUNCEMENT belongs here: it is a CENRO broadcast, so leaving it out
     * would make the notice show under "All" while silently vanishing from the
     * tab a resident actually reads for official news.
     */
    private static final List<String> ADMIN_TYPES = Collections.unmodifiableList(Arrays.asList(
            "ADMIN",
            "PERSONNEL_ASSIGNED",
            "PERSONNEL_UNASSIGNED",
            "CLEANING_STARTED",
            "ADMIN_RESPONSE_UPDATED",
            "REPORT_DENIED",
            // Duplicate review: the resident's report matched one that already
            // existed for the same location.
            "REPORT_DUPLICATE",
            "REPORT_COMPLETED",
            "REPORT_UPDATE",
            "ANNOUNCEMENT"
    ));
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static volatile NotificationRepository instance;

    private final Context application;
    private final NotificationDao notificationDao;
    private final NotificationTombstoneDao notificationTombstoneDao;
    private final ApiService apiService;
    private final SessionManager sessionManager;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    private NotificationRepository(Context application) {
        this.application = application.getApplicationContext();
        AppDatabase database = AppDatabase.getInstance(application);
        this.notificationDao = database.notificationDao();
        this.notificationTombstoneDao = database.notificationTombstoneDao();
        this.apiService = RetrofitClient.getApiService(application);
        this.sessionManager = new SessionManager(application);
    }

    public static NotificationRepository getInstance(@NonNull Context application) {
        if (instance == null) {
            synchronized (NotificationRepository.class) {
                if (instance == null) {
                    instance = new NotificationRepository(application);
                }
            }
        }
        return instance;
    }

    public LiveData<List<Notification>> getAllNotifications() {
        return notificationDao.getAllNotifications(currentResidentId());
    }

    public LiveData<List<Notification>> getAdminNotifications() {
        return notificationDao.getAdminNotifications(currentResidentId(), ADMIN_TYPES);
    }

    public LiveData<List<Notification>> getTrashNotifications() {
        return notificationDao.getTrashNotifications(currentResidentId());
    }

    public LiveData<Integer> getUnreadCount() {
        String residentId = currentResidentId();
        return residentId == null
                ? new MutableLiveData<>(0)
                : notificationDao.observeUnreadCount(residentId);
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void fetchNotifications() {
        fetchNotifications(null);
    }

    public void fetchNotifications(@Nullable RefreshCallback callback) {
        final String residentId = sessionManager.fetchResidentId();
        final String jwt = sessionManager.fetchAuthToken();
        if (residentId == null || jwt == null || jwt.trim().isEmpty()) {
            if (callback != null) callback.onError("Log in to load notifications.");
            return;
        }

        loading.setValue(true);
        error.setValue(null);
        apiService.getMyNotifications().enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call, Response<List<Notification>> response) {
                if (!isCurrentSession(residentId, jwt)) return;
                if (!response.isSuccessful() || response.body() == null) {
                    finishWithError(readError(response, "Unable to load notifications."), callback);
                    return;
                }
                persistRemote(residentId, response.body(), false, true, () ->
                        fetchTrash(residentId, jwt, callback));
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable throwable) {
                if (isCurrentSession(residentId, jwt)) {
                    finishWithError(throwable.getMessage(), callback);
                }
            }
        });
    }

    private void fetchTrash(String residentId, String jwt, RefreshCallback callback) {
        apiService.getMyTrashNotifications().enqueue(new Callback<List<Notification>>() {
            @Override
            public void onResponse(Call<List<Notification>> call, Response<List<Notification>> response) {
                if (!isCurrentSession(residentId, jwt)) return;
                if (!response.isSuccessful() || response.body() == null) {
                    finishWithError(readError(response, "Unable to load notification trash."), callback);
                    return;
                }
                persistRemote(residentId, response.body(), true, false, null);
                refreshUnreadFromServer(residentId, jwt, callback);
            }

            @Override
            public void onFailure(Call<List<Notification>> call, Throwable throwable) {
                if (isCurrentSession(residentId, jwt)) {
                    finishWithError(throwable.getMessage(), callback);
                }
            }
        });
    }

    private void persistRemote(
            String residentId,
            List<Notification> remote,
            boolean trashed,
            boolean displayNew,
            Runnable completion
    ) {
        EXECUTOR.execute(() -> {
            // Notification ids this resident permanently deleted from the Trash
            // tab. The server can keep serving them (the upstream delete is
            // best-effort), so the local tombstone is authoritative and those
            // rows are never re-imported.
            Set<String> tombstoned = new HashSet<>(
                    notificationTombstoneDao.getTombstonedMongoIds(residentId)
            );

            for (Notification incoming : remote) {
                if (incoming == null || isBlank(incoming.getMongoId())) continue;
                if (!residentId.equalsIgnoreCase(incoming.getResidentId())) continue;

                // Re-assert deletion instead of re-importing. This is what makes
                // a hard delete permanent across pull-to-refresh, fragment
                // recreation, app restart and process death.
                if (!NotificationSyncPolicy.shouldImport(incoming.getMongoId(), tombstoned)) {
                    continue;
                }

                incoming.setResidentId(residentId);
                incoming.setDeleted(trashed);

                Notification existing = notificationDao.getByMongoId(
                        residentId,
                        incoming.getMongoId()
                );
                boolean isNew = existing == null;
                if (existing != null) incoming.setId(existing.getId());
                notificationDao.insertNotification(incoming);

                if (isNew && displayNew && !incoming.isRead() && !incoming.isDeleted()) {
                    showSystemNotification(incoming);
                }
            }

            // Retry any upstream delete the server has not honoured yet, so a
            // failure while offline converges instead of leaving a tombstone
            // that hides a row the server still holds.
            List<String> stillOnServer = NotificationSyncPolicy
                    .collectTombstonedStillOnServer(remote, tombstoned);
            if (!stillOnServer.isEmpty()) {
                pushNotificationDelete(stillOnServer, residentId);
            }

            if (completion != null) completion.run();
        });
    }

    private void refreshUnreadFromServer(String residentId, String jwt, RefreshCallback callback) {
        apiService.getUnreadCount().enqueue(new Callback<com.example.myecowatch1.data.remote.UnreadCountResponse>() {
            @Override
            public void onResponse(
                    Call<com.example.myecowatch1.data.remote.UnreadCountResponse> call,
                    Response<com.example.myecowatch1.data.remote.UnreadCountResponse> response
            ) {
                if (!isCurrentSession(residentId, jwt)) return;
                if (response.isSuccessful() && response.body() != null) {
                    loading.postValue(false);
                    if (callback != null) callback.onComplete();
                } else {
                    finishWithError(readError(response, "Unable to refresh unread count."), callback);
                }
            }

            @Override
            public void onFailure(
                    Call<com.example.myecowatch1.data.remote.UnreadCountResponse> call,
                    Throwable throwable
            ) {
                if (isCurrentSession(residentId, jwt)) {
                    finishWithError(throwable.getMessage(), callback);
                }
            }
        });
    }

    private void refreshUnreadFromServer() {
        String residentId = sessionManager.fetchResidentId();
        String jwt = sessionManager.fetchAuthToken();
        if (residentId != null && jwt != null) {
            refreshUnreadFromServer(residentId, jwt, null);
        }
    }

    public void markAsRead(Notification notification) {
        if (notification == null || isBlank(notification.getMongoId())) return;
        String residentId = sessionManager.fetchResidentId();
        if (residentId == null) return;
        String notificationId = notification.getMongoId();
        notification.setRead(true);
        EXECUTOR.execute(() -> notificationDao.markAsRead(residentId, notificationId));
        apiService.markNotificationAsRead(notificationId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() && isCurrentResident(residentId)) {
                    notification.setRead(false);
                    EXECUTOR.execute(() -> notificationDao.markAsUnread(residentId, notificationId));
                    fetchNotifications();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentResident(residentId)) fetchNotifications();
            }
        });
    }

    public void markAsReadByServerId(@Nullable String notificationId) {
        if (isBlank(notificationId)) return;
        String residentId = sessionManager.fetchResidentId();
        if (residentId == null) return;
        EXECUTOR.execute(() -> notificationDao.markAsRead(residentId, notificationId));
        apiService.markNotificationAsRead(notificationId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() && isCurrentResident(residentId)) fetchNotifications();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentResident(residentId)) fetchNotifications();
            }
        });
    }

    public void markAllAsRead() {
        String residentId = sessionManager.fetchResidentId();
        if (residentId == null) return;
        EXECUTOR.execute(() -> notificationDao.markAllAsRead(residentId));
        apiService.markAllNotificationsAsRead().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!response.isSuccessful() && isCurrentResident(residentId)) fetchNotifications();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentResident(residentId)) fetchNotifications();
            }
        });
    }

    public void clearBadgeCount() {
        markAllAsRead();
    }

    /**
     * Moves a notification into the Trash tab.
     *
     * <p>The local row is flagged first so it lands in Trash straight away even
     * offline; the upstream move follows as a best-effort call. If that call
     * fails, nothing is rolled back: the next sync mirrors the server, which
     * still serves the notification in the active feed, and returns it to the
     * All tab on its own.
     */
    public void softDelete(Notification notification) {
        if (notification == null) return;
        final String residentId = currentResidentIdOrNull(notification.getResidentId());
        final String mongoId = blankToNull(notification.getMongoId());
        final int localId = notification.getId();

        EXECUTOR.execute(() -> {
            if (residentId == null) return;
            notificationDao.softDelete(residentId, localId, nowIso());
        });

        if (isBlank(mongoId)) return;
        mutateRemote(notification, true, null);
    }

    /**
     * Takes a notification back out of the Trash tab.
     *
     * <p>Unlike a permanent delete, this is reversible, so the local row is only
     * un-flagged once the server has confirmed the restore. A failure resyncs
     * and the notification returns to Trash rather than half-restored.
     */
    public void restore(Notification notification) {
        mutateRemote(notification, false, () -> {
            if (isCurrentResident(notification.getResidentId())) {
                notificationDao.restore(notification.getResidentId(), notification.getId());
            }
        });
    }

    /**
     * Permanently removes a notification that the resident deleted from inside
     * the Trash tab.
     *
     * <p>The local purge is unconditional and happens first, because that is the
     * only ordering that cannot fail. Previously the Room {@code DELETE} was
     * gated behind the upstream response, so an offline or failing request left
     * the "deleted" notification sitting in the local database forever.
     *
     * <p>The tombstone is written before the row is dropped. The sync treats a
     * missing local row as new, so the absence has to be backed by a durable
     * record of intent; writing it first also means a crash between the two
     * steps cannot resurrect the notification. Upstream deletion is then
     * best-effort: a failure is logged and never rolled back, because the
     * tombstone already hides the row from the UI and {@code fetchNotifications}
     * re-asserts the delete for any id the server still serves.
     */
    public void permanentDelete(Notification notification) {
        if (notification == null) return;
        final String residentId = currentResidentIdOrNull(notification.getResidentId());
        final String mongoId = blankToNull(notification.getMongoId());
        final int localId = notification.getId();

        // A row with no server id cannot be re-asserted upstream, but it can
        // still be purged locally, which is what the user asked for.
        final List<String> mongoIds =
                NotificationSyncPolicy.normalizeIds(Collections.singletonList(mongoId));

        EXECUTOR.execute(() -> {
            if (residentId == null) return;
            recordNotificationTombstones(mongoIds, residentId);
            notificationDao.permanentDelete(residentId, localId);
            // Drop any further local copies of the same server record so no
            // duplicate survives the delete.
            if (!mongoIds.isEmpty()) {
                notificationDao.deleteByMongoIds(residentId, mongoIds);
            }
        });

        if (mongoId == null) return;
        pushNotificationDelete(Collections.singletonList(mongoId), residentId);
    }

    /**
     * Permanently deletes one tombstoned id upstream and re-asserts it on the
     * next sync if the server keeps serving it.
     */
    private void pushNotificationDelete(List<String> mongoIds, String residentId) {
        List<String> normalized = NotificationSyncPolicy.normalizeIds(mongoIds);
        if (normalized.isEmpty()) return;

        String target = residentId == null
                ? sessionManager.fetchResidentId()
                : residentId;

        for (String mongoId : normalized) {
            apiService.deleteNotification(mongoId).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call,
                                      Response<Map<String, Object>> response) {
                    if (response.isSuccessful()) return;
                    if (isCurrentResident(target)) {
                        Log.w("NotificationRepo", "Server refused notification delete (code "
                                + response.code() + "); the id stays tombstoned and is retried on sync");
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                    Log.w("NotificationRepo", "Failed to delete notification upstream; "
                            + "the id stays tombstoned and is retried on sync", throwable);
                }
            });
        }
    }

    /** Writes one tombstone per server id, ignoring blank/duplicate values. */
    private void recordNotificationTombstones(List<String> mongoIds, String residentId) {
        List<String> normalized = NotificationSyncPolicy.normalizeIds(mongoIds);
        if (normalized.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<NotificationTombstone> tombstones = new ArrayList<>();
        for (String mongoId : normalized) {
            tombstones.add(new NotificationTombstone(mongoId, residentId, now));
        }
        notificationTombstoneDao.insertAll(tombstones);
    }

    /**
     * Sends a trash/restore call. {@code onSuccess} is optional: the local state
     * for soft delete and permanent delete is now applied eagerly, so the
     * upstream call only has to converge the server.
     */
    private void mutateRemote(Notification notification, boolean trash, Runnable onSuccess) {
        if (notification == null || isBlank(notification.getMongoId())) return;
        Call<Map<String, Object>> call = trash
                ? apiService.moveNotificationToTrash(notification.getMongoId())
                : apiService.restoreNotification(notification.getMongoId());
        String residentId = notification.getResidentId();
        call.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && isCurrentResident(residentId)) {
                    if (onSuccess != null) EXECUTOR.execute(onSuccess);
                } else if (isCurrentResident(residentId)) {
                    // The server still owns the truth for trash state, so resync
                    // rather than leaving the two out of step.
                    fetchNotifications();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable throwable) {
                if (isCurrentResident(residentId)) fetchNotifications();
            }
        });
    }

    /** Handles a data-only FCM message with the same deduplication path as REST. */
    public void upsertPushNotification(Map<String, String> data) {
        String residentId = sessionManager.fetchResidentId();
        String jwt = sessionManager.fetchAuthToken();
        if (residentId == null || jwt == null || data == null) return;
        Notification notification = fromPushData(residentId, data);
        if (notification == null) return;
        persistRemote(residentId, Collections.singletonList(notification), false, true,
                this::refreshUnreadFromServer);
    }

    public boolean updateFcmTokenBlocking(String token) throws Exception {
        String residentId = sessionManager.fetchResidentId();
        String jwt = sessionManager.fetchAuthToken();
        if (isBlank(residentId) || isBlank(jwt) || isBlank(token)) return false;
        Response<Map<String, Object>> response = apiService.updateFcmToken(
                "Bearer " + jwt,
                new FcmTokenRequest(token)
        ).execute();
        return response.isSuccessful();
    }

    public void clearFcmTokenBestEffort() {
        String jwt = sessionManager.fetchAuthToken();
        if (isBlank(jwt)) return;
        apiService.clearFcmToken("Bearer " + jwt).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
            }

            @Override
            public void onFailure(Call<Void> call, Throwable throwable) {
                Log.w("NotificationRepo", "Unable to unregister FCM token", throwable);
            }
        });
    }

    public void clearLocalDataForResident(String residentId) {
        if (isBlank(residentId)) return;
        EXECUTOR.execute(() -> {
            notificationDao.deleteAllForResident(residentId);
            // Tombstones describe one account's deletions. Keeping them after a
            // logout would silently suppress notifications the next user is
            // entitled to receive.
            notificationTombstoneDao.deleteAllForResident(residentId);
        });
    }

    @Nullable
    public static Notification fromPushData(String residentId, Map<String, String> data) {
        if (isBlank(data.get("notificationId")) || isBlank(residentId)) return null;
        Notification notification = new Notification();
        notification.setMongoId(data.get("notificationId"));
        notification.setResidentId(residentId);
        notification.setReportId(blankToNull(data.get("reportId")));
        notification.setTitle(blankToNull(data.get("title")));
        notification.setMessage(blankToNull(data.get("message")));
        notification.setType(blankToNull(data.get("type")));
        notification.setCreatedAt(blankToNull(data.get("createdAt")));
        notification.setRead(false);
        notification.setDeleted(false);
        return notification;
    }

    public static int stableNotificationId(@Nullable String serverId) {
        if (serverId == null) return 1;
        int value = serverId.hashCode() & Integer.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    public static boolean isAdminNotificationType(@Nullable String type) {
        return type != null && ADMIN_TYPES.contains(type.trim().toUpperCase());
    }

    @SuppressLint("MissingPermission")
    private void showSystemNotification(Notification notification) {
        if (ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(application, DashboardActivity.class)
                .setAction(DashboardActivity.ACTION_OPEN_NOTIFICATION)
                .putExtra(DashboardActivity.EXTRA_NOTIFICATION_ID, notification.getMongoId())
                .putExtra(DashboardActivity.EXTRA_REPORT_ID, notification.getReportId())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // The destination is derived from the stored type rather than carried in
        // the push payload. A resident can also arrive here through the REST
        // sync, which has no payload to read a target from, and an announcement
        // owns no report id to route on. Deriving it from type means the tap
        // lands on the announcement either way it was delivered.
        if (ANNOUNCEMENT_TYPE.equalsIgnoreCase(
                notification.getType() == null ? "" : notification.getType().trim())) {
            intent.putExtra(
                    DashboardActivity.EXTRA_TARGET_SCREEN,
                    DashboardActivity.TARGET_SCREEN_ANNOUNCEMENTS);
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                application,
                stableNotificationId(notification.getMongoId()),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(application, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notification.getTitle() == null
                        ? "EcoWatch" : notification.getTitle())
                .setContentText(notification.getMessage() == null
                        ? "Your CENRO notice was published." : notification.getMessage())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setGroup("ecowatch_report_updates");
        try {
            NotificationManagerCompat.from(application).notify(
                    stableNotificationId(notification.getMongoId()),
                    builder.build()
            );
        } catch (SecurityException ignored) {
            // Android 13+ permission can be revoked between check and notify.
        }
    }

    private boolean isCurrentSession(String residentId, String jwt) {
        return isCurrentResident(residentId) && jwt.equals(sessionManager.fetchAuthToken());
    }

    private boolean isCurrentResident(String residentId) {
        return residentId != null && residentId.equalsIgnoreCase(sessionManager.fetchResidentId());
    }

    private String currentResidentId() {
        return sessionManager.fetchResidentId() == null ? "" : sessionManager.fetchResidentId();
    }

    /**
     * Resident that owns a mutation, or {@code null} when the signed-in account
     * no longer matches the row the user acted on. Null means "do not touch the
     * local database", which keeps one account's delete from touching another's.
     */
    private String currentResidentIdOrNull(String rowResidentId) {
        String current = sessionManager.fetchResidentId();
        if (current == null || rowResidentId == null) return null;
        return current.equalsIgnoreCase(rowResidentId) ? rowResidentId : null;
    }

    private void finishWithError(String message, RefreshCallback callback) {
        String safeMessage = isBlank(message) ? "Notification sync failed." : message;
        error.postValue(safeMessage);
        loading.postValue(false);
        if (callback != null) callback.onError(safeMessage);
    }

    private String readError(Response<?> response, String fallback) {
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                String message = new com.google.gson.JsonParser().parse(body.string())
                        .getAsJsonObject()
                        .get("message")
                        .getAsString();
                if (!isBlank(message)) return message;
            }
        } catch (Exception ignored) {
        }
        return fallback + " (" + response.code() + ")";
    }

    private static String nowIso() {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
        );
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date());
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public interface RefreshCallback {
        void onComplete();
        void onError(String error);
    }
}

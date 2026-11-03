package com.example.ecowatchpersonnel1.data.repository;

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

import com.example.ecowatchpersonnel1.core.R;
import com.example.ecowatchpersonnel1.data.local.PersonnelDatabase;
import com.example.ecowatchpersonnel1.data.local.PersonnelNotificationDao;
import com.example.ecowatchpersonnel1.data.local.PersonnelNotificationEntity;
import com.example.ecowatchpersonnel1.data.local.PersonnelNotificationTombstone;
import com.example.ecowatchpersonnel1.data.local.PersonnelNotificationTombstoneDao;
import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.remote.ApiCalls;
import com.example.ecowatchpersonnel1.data.remote.ApiService;
import com.example.ecowatchpersonnel1.data.remote.FcmTokenRequest;
import com.example.ecowatchpersonnel1.data.remote.PersonnelNotificationsResponse;
import com.example.ecowatchpersonnel1.data.remote.RetrofitClient;
import com.example.ecowatchpersonnel1.data.remote.UnreadCountResponse;
import com.example.ecowatchpersonnel1.utils.NotificationPreferences;
import com.example.ecowatchpersonnel1.utils.SessionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Backend-backed notification list for the signed-in cleaner.
 *
 * <p>The server owns which notifications exist and their read state. Room holds
 * this device's copy, and exists for one reason: the Trash tab. The personnel
 * backend has no move-to-trash or restore route, so trashing is necessarily a
 * local decision until the cleaner confirms it from inside Trash.
 *
 * <p>Because the server keeps serving a trashed row, the sync deliberately does
 * <em>not</em> mirror the server's membership. It merges server rows in, but the
 * retained {@code isDeleted} flag wins, so a trashed notification stays in Trash
 * across pull-to-refresh, app restart and offline.
 */
public final class PersonnelNotificationRepository {

    /** Must match {@code PERSONNEL_CHANNEL_ID} on the backend and the manifest. */
    public static final String CHANNEL_ID = "ecowatch_personnel_notifications";

    private static final int FETCH_LIMIT = 100;
    private static final String LOG_TAG = "PersonnelNotifRepo";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private static volatile PersonnelNotificationRepository instance;

    private final Context appContext;
    private final ApiService api;
    private final SessionManager sessionManager;
    private final PersonnelNotificationDao notificationDao;
    private final PersonnelNotificationTombstoneDao tombstoneDao;

    private final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);

    /**
     * The active feed, straight from Room.
     *
     * <p>Room-backed rather than held in memory so a trash, a restore and a sync
     * all reach the list through the same observer, and so the list is already
     * correct when the screen opens without waiting on the network.
     */
    private volatile LiveData<List<PersonnelNotification>> notifications;

    /** The Trash tab, straight from Room. */
    private volatile LiveData<List<PersonnelNotification>> trashNotifications;

    /** Ids this cleaner destroyed from Trash, read once per sync. */
    private volatile LiveData<Integer> trashCount;

    /**
     * Reloads the feed from the server and merges it into Room.
     *
     * <p>Failure leaves the cached list on screen: a flaky connection must not
     * read as "you have no notifications", which is the same rule
     * {@code TaskHistoryDao.deleteMissing} is documented under.
     */
    private PersonnelNotificationRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.api = RetrofitClient.getApiService(appContext);
        this.sessionManager = new SessionManager(appContext);
        PersonnelDatabase database = PersonnelDatabase.getInstance(appContext);
        this.notificationDao = database.personnelNotificationDao();
        this.tombstoneDao = database.personnelNotificationTombstoneDao();

        bindToSession(sessionManager.fetchPersonnelId());
    }

    /**
     * Points the exposed LiveData at one cleaner's rows.
     *
     * <p>The queries are bound per account because field devices are shared: a
     * repository created under one cleaner must not keep serving that cleaner's
     * rows after a different cleaner signs in. Called on construction and again
     * from {@link #refresh()} so a sign-in that happened after this singleton was
     * created still binds correctly.
     */
    private synchronized void bindToSession(@Nullable String personnelId) {
        if (boundPersonnelId != null && boundPersonnelId.equals(personnelId)) return;
        boundPersonnelId = personnelId;

        if (isBlank(personnelId)) {
            notifications = new MutableLiveData<>(new ArrayList<>());
            trashNotifications = new MutableLiveData<>(new ArrayList<>());
            trashCount = new MutableLiveData<>(0);
            return;
        }

        notifications = map(notificationDao.observeActive(personnelId));
        trashNotifications = map(notificationDao.observeTrash(personnelId));
        trashCount = notificationDao.observeTrashCount(personnelId);
    }

    @Nullable
    private volatile String boundPersonnelId;

    public static PersonnelNotificationRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (PersonnelNotificationRepository.class) {
                if (instance == null) {
                    instance = new PersonnelNotificationRepository(context);
                }
            }
        }
        return instance;
    }

    public LiveData<List<PersonnelNotification>> getNotifications() {
        return notifications;
    }

    /** Rows the cleaner trashed and has not yet destroyed. */
    public LiveData<List<PersonnelNotification>> getTrashNotifications() {
        return trashNotifications;
    }

    public LiveData<Integer> getTrashCount() {
        return trashCount;
    }

    public LiveData<Integer> getUnreadCount() {
        return unreadCount;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    /**
     * Reloads the feed and the unread badge from the server.
     *
     * <p>Both calls are issued even when the caller only opened the list, because
     * the badge is rendered on a different screen and would otherwise drift.
     */
    public void refresh() {
        bindToSession(sessionManager.fetchPersonnelId());

        if (!sessionManager.isLoggedIn()) {
            loading.setValue(false);
            unreadCount.setValue(0);
            return;
        }

        final String personnelId = sessionManager.fetchPersonnelId();
        loading.setValue(true);
        error.setValue(null);

        ApiCalls.enqueue(api.getNotifications(FETCH_LIMIT),
                new ApiCallback<PersonnelNotificationsResponse>() {
                    @Override
                    public void onSuccess(PersonnelNotificationsResponse result) {
                        mergeIntoCache(personnelId, result == null
                                ? new ArrayList<>()
                                : result.getNotifications());
                        loading.setValue(false);
                        refreshUnreadCount();
                    }

                    @Override
                    public void onError(String message) {
                        error.setValue(message);
                        loading.setValue(false);
                    }
                });
    }

    /**
     * Folds a server page into the local cache.
     *
     * <p>Three rules, and each one exists because breaking it is a bug the user
     * sees:
     *
     * <ul>
     *   <li>A tombstoned id is skipped and its delete re-asserted, so a destroyed
     *       notification cannot come back.</li>
     *   <li>The existing row's {@code isDeleted} flag is carried over, so a
     *       trashed notification stays in Trash even though the server, which
     *       never heard about the trash, still lists it as active.</li>
     *   <li>Read state is only taken from the server for rows the cleaner has not
     *       trashed, so trashing cannot silently mark a row unread.</li>
     * </ul>
     */
    private void mergeIntoCache(String personnelId, List<PersonnelNotification> remote) {
        if (isBlank(personnelId)) return;

        EXECUTOR.execute(() -> {
            Set<String> tombstoned = new HashSet<>(tombstoneDao.getTombstonedIds(personnelId));

            List<PersonnelNotificationEntity> rows = new ArrayList<>();
            for (PersonnelNotification incoming : remote) {
                if (incoming == null) continue;
                String id = PersonnelNotificationIds.resolve(incoming);
                if (id == null) continue;

                if (!PersonnelNotificationSyncPolicy.shouldImport(id, tombstoned)) {
                    continue;
                }

                PersonnelNotificationEntity entity =
                        PersonnelNotificationEntity.from(incoming, personnelId);

                PersonnelNotificationEntity existing =
                        notificationDao.getById(personnelId, id);
                if (existing != null) {
                    // Trashing is local-only, so the local flag outranks the
                    // server, which still believes the row is active.
                    entity.isDeleted = existing.isDeleted;
                    entity.deletedAt = existing.deletedAt;
                    if (entity.isDeleted) {
                        entity.isRead = existing.isRead;
                    }
                }
                rows.add(entity);
            }

            if (!rows.isEmpty()) {
                notificationDao.upsertAll(rows);
            }

            // Retry any upstream delete the server has not honoured yet, so a
            // failure while offline converges instead of leaving a tombstone that
            // hides a row the server still holds.
            List<String> stillOnServer = PersonnelNotificationSyncPolicy
                    .collectTombstonedStillOnServer(remote, tombstoned);
            if (!stillOnServer.isEmpty()) {
                pushDelete(stillOnServer);
            }
        });
    }

    public void refreshUnreadCount() {
        if (!sessionManager.isLoggedIn()) {
            unreadCount.setValue(0);
            return;
        }
        ApiCalls.enqueue(api.getUnreadCount(), new ApiCallback<UnreadCountResponse>() {
            @Override
            public void onSuccess(UnreadCountResponse result) {
                unreadCount.setValue(result == null ? 0 : result.getUnreadCount());
            }

            @Override
            public void onError(String message) {
                // The badge is secondary; a stale count is better than an error.
            }
        });
    }

    /** Marks one row read and updates the visible list optimistically. */
    public void markAsRead(@Nullable PersonnelNotification notification) {
        if (notification == null || isBlank(PersonnelNotificationIds.resolve(notification))) return;

        final String notificationId = PersonnelNotificationIds.resolve(notification);
        notification.isRead = true;
        persistReadState(notificationId, true);
        decrementBadge();

        ApiCalls.enqueue(api.markNotificationAsRead(notificationId),
                new ApiCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> result) {
                        refreshUnreadCount();
                    }

                    @Override
                    public void onError(String message) {
                        refresh();
                    }
                });
    }

    /**
     * Marks a row read when only its id is known, which is the case for a push
     * whose data payload is handled before the list has been fetched.
     */
    public void markAsReadById(@Nullable String notificationId) {
        if (isBlank(notificationId)) return;
        final String id = notificationId.trim();

        List<PersonnelNotification> current = notifications.getValue();
        boolean found = false;
        if (current != null) {
            for (PersonnelNotification item : current) {
                if (id.equals(PersonnelNotificationIds.resolve(item))) {
                    item.isRead = true;
                    found = true;
                }
            }
        }
        if (found) {
            persistReadState(id, true);
            decrementBadge();
        }

        ApiCalls.enqueue(api.markNotificationAsRead(id),
                new ApiCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> result) {
                        refreshUnreadCount();
                    }

                    @Override
                    public void onError(String message) {
                        // Best effort: the next refresh reconciles it.
                    }
                });
    }

    public void markAllAsRead() {
        final String personnelId = sessionManager.fetchPersonnelId();

        List<PersonnelNotification> current = notifications.getValue();
        if (current != null) {
            for (PersonnelNotification item : current) {
                item.isRead = true;
            }
        }
        persistReadStateForAll();
        unreadCount.setValue(0);

        ApiCalls.enqueue(api.markAllNotificationsAsRead(),
                new ApiCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> result) {
                    }

                    @Override
                    public void onError(String message) {
                        refresh();
                    }
                });
    }

    /**
     * Moves notifications into the Trash tab.
     *
     * <p>This is the first of the two deletion steps and it is reversible: the row
     * stays in Room with {@code isDeleted = 1} and the server is not told, because
     * it has no route for it. Nothing is tombstoned, because the retained row is
     * already the durable record that stops the sync resurrecting it into All.
     *
     * <p>The badge is adjusted locally rather than refetched so the count cannot
     * disagree with the row that just left the list.
     */
    public void softDelete(@Nullable Set<String> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) return;
        final String personnelId = sessionManager.fetchPersonnelId();
        if (isBlank(personnelId)) return;

        final List<String> ids = PersonnelNotificationSyncPolicy
                .normalizeIds(new ArrayList<>(notificationIds));
        if (ids.isEmpty()) return;

        long now = System.currentTimeMillis();
        // Whether each row was unread is only known after the read, so the badge is
        // published from the worker with postValue rather than guessed on the main
        // thread. Reading it here would always see zero and leave the badge
        // disagreeing with the rows that just left the list.
        EXECUTOR.execute(() -> {
            int unreadLeaving = 0;
            for (String id : ids) {
                PersonnelNotificationEntity row = notificationDao.getById(personnelId, id);
                if (row == null || row.isDeleted) continue;
                notificationDao.softDelete(personnelId, id, now);
                if (!row.isRead) unreadLeaving++;
            }
            if (unreadLeaving > 0) {
                Integer current = unreadCount.getValue();
                unreadCount.postValue(
                        Math.max(0, (current == null ? 0 : current) - unreadLeaving));
            }
        });
    }

    /**
     * Takes notifications back out of Trash, which returns them to the All tab.
     *
     * <p>Restoring clears the flag and the timestamp, so the row stops appearing
     * in Trash immediately. The server is not told: it never recorded the trash,
     * so there is nothing to undo and the row is already in its active feed.
     */
    public void restore(@Nullable Set<String> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) return;
        final String personnelId = sessionManager.fetchPersonnelId();
        if (isBlank(personnelId)) return;

        final List<String> ids = PersonnelNotificationSyncPolicy
                .normalizeIds(new ArrayList<>(notificationIds));
        if (ids.isEmpty()) return;

        EXECUTOR.execute(() -> {
            int restoredUnread = 0;
            for (String id : ids) {
                PersonnelNotificationEntity row = notificationDao.getById(personnelId, id);
                if (row != null && row.isDeleted && !row.isRead) restoredUnread++;
                notificationDao.restore(personnelId, id);
            }
            if (restoredUnread > 0) {
                Integer current = unreadCount.getValue();
                unreadCount.postValue((current == null ? 0 : current) + restoredUnread);
            }
        });
    }

    /**
     * Destroys notifications the cleaner deleted from inside the Trash tab.
     *
     * <p>The local purge is unconditional and happens first, because that is the
     * only ordering that cannot fail. Gating the Room {@code DELETE} behind the
     * server response is what previously left "deleted" notifications sitting in
     * the database whenever the request failed.
     *
     * <p>The tombstone is written before the row is dropped: the sync reads a
     * missing row as new, so the absence has to be backed by a durable record, and
     * writing it first also means a crash between the two steps cannot resurrect
     * the notification. The upstream delete is then best-effort — a failure is
     * logged and never rolled back, because the tombstone already hides the row
     * and {@link #mergeIntoCache} re-asserts the delete on the next sync.
     */
    public void deleteNotificationsPermanently(@Nullable Set<String> notificationIds,
                                              @Nullable ApiCallback<Void> callback) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            if (callback != null) callback.onSuccess(null);
            return;
        }

        final String personnelId = sessionManager.fetchPersonnelId();
        final List<String> ids = PersonnelNotificationSyncPolicy
                .normalizeIds(new ArrayList<>(notificationIds));
        if (ids.isEmpty()) {
            if (callback != null) callback.onSuccess(null);
            return;
        }

        if (!isBlank(personnelId)) {
            EXECUTOR.execute(() -> {
                recordTombstones(ids, personnelId);
                for (String id : ids) {
                    notificationDao.hardDelete(personnelId, id);
                }
            });
        }

        pushDelete(ids);

        if (callback != null) callback.onSuccess(null);
    }

    /**
     * Wipes this cleaner's notification rows and tombstones.
     *
     * <p>Called on sign-out. Field devices are shared, so leaving one cleaner's
     * alerts and delete history on the phone would show the next person both the
     * previous account's notifications and the fact that they were trashed.
     */
    public void clearLocalDataForPersonnel(@Nullable String personnelId) {
        if (isBlank(personnelId)) return;
        EXECUTOR.execute(() -> {
            notificationDao.clearForPersonnel(personnelId);
            tombstoneDao.clearForPersonnel(personnelId);
        });
    }

    /** Writes one tombstone per server id, ignoring blank/duplicate values. */
    private void recordTombstones(List<String> notificationIds, String personnelId) {
        List<String> normalized = PersonnelNotificationSyncPolicy.normalizeIds(notificationIds);
        if (normalized.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<PersonnelNotificationTombstone> tombstones = new ArrayList<>();
        for (String id : normalized) {
            tombstones.add(new PersonnelNotificationTombstone(id, personnelId, now));
        }
        tombstoneDao.insertAll(tombstones);
    }

    /**
     * Permanently deletes ids upstream and re-asserts them on the next sync if
     * the server keeps serving them.
     */
    private void pushDelete(List<String> notificationIds) {
        List<String> normalized = PersonnelNotificationSyncPolicy.normalizeIds(notificationIds);
        if (normalized.isEmpty()) return;

        for (String id : normalized) {
            ApiCalls.enqueue(api.deleteNotification(id), new ApiCallback<Map<String, Object>>() {
                @Override
                public void onSuccess(Map<String, Object> result) {
                    refreshUnreadCount();
                }

                @Override
                public void onError(String message) {
                    Log.w(LOG_TAG, "Server delete failed for notification " + id
                            + ": " + message + "; the id stays tombstoned and is retried on sync");
                }
            });
        }
    }

    /**
     * Binds this device to the signed-in cleaner. Blocking because it runs from a
     * background worker that must report success back to WorkManager.
     */
    public boolean registerFcmTokenBlocking(String token) throws Exception {
        if (isBlank(token) || !sessionManager.isLoggedIn()) return false;
        Response<Map<String, Object>> response =
                api.registerFcmToken(new FcmTokenRequest(token)).execute();
        return response.isSuccessful();
    }

    /** Best-effort unbind on sign-out; the local session is cleared regardless. */
    public void clearFcmTokenBestEffort() {
        if (!sessionManager.isLoggedIn()) return;
        try {
            api.clearFcmToken().enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                    Log.w(LOG_TAG, "Unable to unregister FCM token", throwable);
                }
            });
        } catch (RuntimeException error) {
            Log.w(LOG_TAG, "Unable to unregister FCM token", error);
        }
    }

    /**
     * Handles a foreground FCM message: shows the system notification the
     * background path would have shown, and folds the row into the list.
     *
     * <p>A push for a notification the cleaner already destroyed is dropped rather
     * than resurrected, and a push cannot un-trash a row, so the trashed flag wins
     * here for the same reason it wins in the sync.
     */
    public void upsertPushNotification(Map<String, String> data) {
        if (data == null || !sessionManager.isLoggedIn()) return;

        String notificationId = data.get("notificationId");
        if (isBlank(notificationId)) return;
        final String personnelId = sessionManager.fetchPersonnelId();

        PersonnelNotification notification = new PersonnelNotification();
        notification.notificationId = notificationId;
        notification.id = notificationId;
        notification.assignmentId = blankToNull(data.get("assignmentId"));
        notification.reportId = blankToNull(data.get("reportId"));
        notification.title = blankToNull(data.get("title"));
        notification.message = blankToNull(data.get("message"));
        notification.type = blankToNull(data.get("type"));
        notification.createdAt = blankToNull(data.get("createdAt"));
        notification.isRead = false;

        if (!isBlank(personnelId)) {
            PersonnelNotificationEntity entity =
                    PersonnelNotificationEntity.from(notification, personnelId);
            EXECUTOR.execute(() -> {
                if (!PersonnelNotificationSyncPolicy.shouldImport(
                        notificationId, new HashSet<>(tombstoneDao.getTombstonedIds(personnelId)))) {
                    return;
                }
                PersonnelNotificationEntity existing =
                        notificationDao.getById(personnelId, notificationId);
                if (existing != null && existing.isDeleted) {
                    // The server still believes this row is active. It is in the
                    // cleaner's Trash, so it stays there.
                    return;
                }
                notificationDao.upsertAll(java.util.Collections.singletonList(entity));
            });
        }

        // The row is recorded whatever the cleaner chose to be alerted about. Only
        // the device alert is gated, so silencing a category hides the interruption
        // without erasing the record of the assignment.
        showSystemNotification(notification);
    }

    /** Writes a read flag to the cache so the list reflects it without a refetch. */
    private void persistReadState(String notificationId, boolean read) {
        final String personnelId = sessionManager.fetchPersonnelId();
        if (isBlank(personnelId)) return;
        EXECUTOR.execute(() -> {
            PersonnelNotificationEntity row =
                    notificationDao.getById(personnelId, notificationId);
            if (row == null) return;
            row.isRead = read;
            row.syncedAt = System.currentTimeMillis();
            notificationDao.upsertAll(java.util.Collections.singletonList(row));
        });
    }

    /**
     * Writes the read flag to every cached row that has it cleared.
     *
     * <p>Iterated rather than issued as one UPDATE so a row is only rewritten when
     * it actually changes, which keeps the refresh that follows from repainting
     * rows that were already read.
     */
    private void persistReadStateForAll() {
        final String personnelId = sessionManager.fetchPersonnelId();
        if (isBlank(personnelId)) return;

        List<PersonnelNotification> current = notifications.getValue();
        if (current == null) return;

        final List<String> ids = new ArrayList<>();
        for (PersonnelNotification item : current) {
            String id = PersonnelNotificationIds.resolve(item);
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return;

        EXECUTOR.execute(() -> {
            for (String id : PersonnelNotificationSyncPolicy.normalizeIds(ids)) {
                PersonnelNotificationEntity row = notificationDao.getById(personnelId, id);
                if (row == null || row.isRead) continue;
                row.isRead = true;
                row.syncedAt = System.currentTimeMillis();
                notificationDao.upsertAll(java.util.Collections.singletonList(row));
            }
        });
    }

    private void decrementBadge() {
        Integer current = unreadCount.getValue();
        int value = current == null ? 0 : current;
        unreadCount.setValue(Math.max(0, value - 1));
    }

    @SuppressLint("MissingPermission")
    private void showSystemNotification(PersonnelNotification notification) {
        if (!NotificationPreferences.allowsAlert(appContext, notification.type)) {
            return;
        }

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = PersonnelNotificationIntents.openNotification(
                appContext,
                PersonnelNotificationIds.resolve(notification),
                notification.assignmentId,
                notification.reportId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                stableNotificationId(PersonnelNotificationIds.resolve(notification)),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(notification.title == null
                                ? "EcoWatch Personnel" : notification.title)
                        .setContentText(notification.message == null
                                ? "You have a new update." : notification.message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setGroup("ecowatch_personnel_tasks");
        try {
            NotificationManagerCompat.from(appContext).notify(
                    stableNotificationId(PersonnelNotificationIds.resolve(notification)),
                    builder.build());
        } catch (SecurityException ignored) {
            // The Android 13+ permission can be revoked between check and notify.
        }
    }

    /** A positive id derived from the server id, for stable notification slots. */
    public static int stableNotificationId(@Nullable String serverId) {
        if (serverId == null) return 1;
        int value = serverId.hashCode() & Integer.MAX_VALUE;
        return value == 0 ? 1 : value;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /** Converts cached rows into the models the UI renders. */
    private static LiveData<List<PersonnelNotification>> map(
            LiveData<List<PersonnelNotificationEntity>> source) {
        final MutableLiveData<List<PersonnelNotification>> mapped =
                new MutableLiveData<>(new ArrayList<>());
        source.observeForever(new androidx.lifecycle.Observer<List<PersonnelNotificationEntity>>() {
            @Override
            public void onChanged(List<PersonnelNotificationEntity> rows) {
                List<PersonnelNotification> models = new ArrayList<>();
                if (rows != null) {
                    for (PersonnelNotificationEntity row : rows) {
                        if (row != null) models.add(row.toModel());
                    }
                }
                mapped.setValue(models);
            }
        });
        return mapped;
    }
}

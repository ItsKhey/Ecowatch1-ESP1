package com.example.ecowatchpersonnel1.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;

/**
 * This device's copy of one personnel notification.
 *
 * <p>The server owns which notifications exist and their read state; this table
 * is what makes the Trash tab possible. The personnel backend exposes no
 * move-to-trash or restore route, so "deleted" is necessarily a local decision
 * until the cleaner confirms it from inside Trash.
 *
 * <p>Rows are keyed by {@code notificationId} and written with REPLACE, so a
 * re-sync updates a row in place rather than piling up duplicates.
 *
 * <p>{@code isDeleted} is deliberately retained across re-syncs. The server keeps
 * serving a row the cleaner trashed, because trashing never reached it, so
 * mirroring the server blindly would put every trashed notification straight back
 * into the All tab on the next pull-to-refresh.
 */
@Entity(
        tableName = "personnel_notifications",
        indices = {
                @Index(value = {"personnelId", "isDeleted"}),
                @Index(value = {"personnelId", "isRead"})
        }
)
public class PersonnelNotificationEntity {

    /** The server's identifier for the notification; the natural key. */
    @PrimaryKey
    @NonNull
    public String notificationId = "";

    /** Which cleaner the row belongs to, so a shared device cannot leak rows. */
    @ColumnInfo(name = "personnelId")
    public String personnelId;

    @ColumnInfo(name = "assignmentId")
    public String assignmentId;

    @ColumnInfo(name = "reportId")
    public String reportId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "message")
    public String message;

    @ColumnInfo(name = "type")
    public String type;

    @ColumnInfo(name = "isRead")
    public boolean isRead;

    /** True while the row sits in Trash. Retained across re-syncs. */
    @ColumnInfo(name = "isDeleted")
    public boolean isDeleted;

    /** When the cleaner trashed the row; drives the Trash tab's ordering. */
    @ColumnInfo(name = "deletedAt")
    public Long deletedAt;

    @ColumnInfo(name = "createdAt")
    public String createdAt;

    /** When this device last wrote the row, so a stale cache can be recognised. */
    @ColumnInfo(name = "syncedAt")
    public long syncedAt;

    /**
     * The id to address the row by on the server.
     *
     * <p>The payload carries two candidate fields and the list and push paths do
     * not agree on which one is populated, so both are consulted. A row with
     * neither is unaddressable and is never trashed or deleted.
     */
    public static String resolveServerId(PersonnelNotification notification) {
        return com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationIds.resolve(notification);
    }

    /** Flattens a server notification into a storable, untrashed row. */
    public static PersonnelNotificationEntity from(
            @NonNull PersonnelNotification notification, String personnelId) {
        String serverId = resolveServerId(notification);
        PersonnelNotificationEntity entity = new PersonnelNotificationEntity();
        entity.notificationId = serverId == null ? "" : serverId;
        entity.personnelId = personnelId;
        entity.assignmentId = notification.assignmentId;
        entity.reportId = notification.reportId;
        entity.title = notification.title;
        entity.message = notification.message;
        entity.type = notification.type;
        entity.isRead = notification.isRead;
        entity.isDeleted = false;
        entity.deletedAt = null;
        entity.createdAt = notification.createdAt;
        entity.syncedAt = System.currentTimeMillis();
        return entity;
    }

    /**
     * Rebuilds the model the UI renders.
     *
     * <p>Both id fields are populated from the same key, because the adapter and
     * the delete calls must resolve the row the same way the sync did.
     */
    public PersonnelNotification toModel() {
        PersonnelNotification model = new PersonnelNotification();
        model.notificationId = notificationId;
        model.id = notificationId;
        model.assignmentId = assignmentId;
        model.reportId = reportId;
        model.title = title;
        model.message = message;
        model.type = type;
        model.isRead = isRead;
        model.createdAt = createdAt;
        return model;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package com.example.myecowatch1.data.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Durable record that a notification was permanently deleted from the Trash tab.
 *
 * <p>Without this, a hard delete is a local {@code DELETE} plus a best-effort
 * upstream call. The next {@code GET notifications/my-notifications} (or the
 * trash endpoint) still returns the row, {@code persistRemote} finds no local
 * counterpart and re-inserts it, so the "deleted" notification reappears on
 * every pull-to-refresh.
 *
 * <p>Soft-deleted notifications must NOT be tombstoned: they stay in Room with
 * {@code isDeleted = 1} precisely so the Trash tab can list and restore them.
 * Only the hard delete from inside Trash writes a row here.
 *
 * <p>The {@code residentId} index must stay declared here. Room validates the
 * live schema against this declaration on every open, so a migration that
 * creates an index the entity does not declare (or vice versa) aborts the
 * upgrade with "Migration didn't properly handle".
 */
@Entity(
        tableName = "notification_tombstones",
        indices = {@Index(value = "residentId", name = "index_notification_tombstones_residentId")}
)
public class NotificationTombstone {

    /** The server notification id. Null/blank ids are never tombstoned. */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "mongoId")
    private String mongoId;

    /** Owner of the tombstone, so one account's deletes never affect another. */
    @ColumnInfo(name = "residentId")
    private String residentId;

    @ColumnInfo(name = "deletedAt")
    private long deletedAt;

    public NotificationTombstone(@NonNull String mongoId, String residentId, long deletedAt) {
        this.mongoId = mongoId;
        this.residentId = residentId;
        this.deletedAt = deletedAt;
    }

    @NonNull
    public String getMongoId() {
        return mongoId;
    }

    public String getResidentId() {
        return residentId;
    }

    public long getDeletedAt() {
        return deletedAt;
    }
}

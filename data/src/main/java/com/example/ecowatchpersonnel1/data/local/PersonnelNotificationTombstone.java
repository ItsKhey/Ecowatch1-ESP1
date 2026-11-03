package com.example.ecowatchpersonnel1.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Durable record that a notification was permanently deleted from the Trash tab.
 *
 * <p>A hard delete drops the row from {@code personnel_notifications} and asks the
 * server to drop it too. The upstream call is best-effort: offline or refused, the
 * server keeps serving the row, and the next sync would see no local counterpart,
 * read the gap as "never seen" and re-insert it. The notification the cleaner
 * destroyed would come back on every pull-to-refresh.
 *
 * <p>Recording the decision locally makes deletion win over reconciliation. Because
 * this lives in Room it survives pull-to-refresh, fragment recreation, app restart
 * and process death.
 *
 * <p>Only the hard delete writes a row here. Trashing keeps the notification row
 * with {@code isDeleted = 1}, which is already a durable record of the decision.
 */
@Entity(
        tableName = "personnel_notification_tombstones",
        indices = {@Index(value = "personnelId", name = "index_personnel_notification_tombstones_personnelId")}
)
public class PersonnelNotificationTombstone {

    /** The server notification id. Null/blank ids are never tombstoned. */
    @PrimaryKey
    @NonNull
    public String notificationId = "";

    /** Owner of the tombstone, so one account's deletes never affect another. */
    @ColumnInfo(name = "personnelId")
    public String personnelId;

    @ColumnInfo(name = "deletedAt")
    public long deletedAt;

    public PersonnelNotificationTombstone(@NonNull String notificationId,
                                          String personnelId,
                                          long deletedAt) {
        this.notificationId = notificationId;
        this.personnelId = personnelId;
        this.deletedAt = deletedAt;
    }
}

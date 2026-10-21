package com.example.myecowatch1.data.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Durable record that a server-side history log was permanently deleted.
 *
 * <p>Without this, a permanent delete is a local {@code DELETE} plus a
 * best-effort upstream call. The next {@code GET history/my-history} still
 * returns the row, the sync sees no local counterpart (because the row is
 * gone) and re-inserts it, so the entry reappears on every refresh.
 *
 * <p>Persisting the decision locally makes deletion win over reconciliation:
 * {@code fetchHistory()} consults this table and refuses to re-import a
 * tombstoned id. Because it lives in Room it survives pull-to-refresh,
 * fragment recreation, app restart and process death.
 *
 * <p>The {@code residentId} index must stay declared here. Room validates the
 * live schema against this declaration on every open, so a migration that
 * creates an index the entity does not declare (or vice versa) aborts the
 * upgrade with "Migration didn't properly handle".
 */
@Entity(
        tableName = "history_tombstones",
        indices = {@Index(value = "residentId", name = "index_history_tombstones_residentId")}
)
public class HistoryTombstone {

    /** The server history id. Null/blank ids are never tombstoned. */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "mongoId")
    private String mongoId;

    /** Owner of the tombstone, so one account's deletes never affect another. */
    @ColumnInfo(name = "residentId")
    private String residentId;

    @ColumnInfo(name = "deletedAt")
    private long deletedAt;

    public HistoryTombstone(@NonNull String mongoId, String residentId, long deletedAt) {
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

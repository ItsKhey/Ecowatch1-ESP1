package com.example.myecowatch1.data.repository;

import com.example.myecowatch1.data.models.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure reconciliation rules for the notification sync, kept free of Android and
 * Room types so the permanent-delete contract can be unit tested.
 *
 * <p>The bug this encodes: deleting a notification from the Trash tab removes
 * the row from Room, so the next sync saw no local counterpart, treated the gap
 * as "brand new" and re-inserted it. A soft delete was unaffected because the
 * row is retained with {@code isDeleted = 1} and only the Trash tab shows it.
 * The only correct discriminator is an explicit record of what was deleted.
 */
public final class NotificationSyncPolicy {

    private NotificationSyncPolicy() {
    }

    /**
     * Whether a server row may be imported into the local cache.
     *
     * <p>A notification the resident permanently deleted must never come back,
     * no matter how many times the server returns it.
     */
    public static boolean shouldImport(String mongoId, Set<String> tombstoned) {
        return TombstonePolicy.shouldImport(mongoId, tombstoned);
    }

    /**
     * Normalises server ids for tombstoning: drops null/blank values and
     * removes duplicates while preserving first-seen order.
     */
    public static List<String> normalizeIds(List<String> mongoIds) {
        return TombstonePolicy.normalizeIds(mongoIds);
    }

    /**
     * Tombstoned ids the server still serves across either feed (active or
     * trash). These are re-asserted with a delete so a previously failed or
     * evicted upstream delete converges instead of resurrecting the row.
     */
    public static List<String> collectTombstonedStillOnServer(List<Notification> serverRows,
                                                              Set<String> tombstoned) {
        List<String> stillPresent = new ArrayList<>();
        if (serverRows == null || tombstoned == null || tombstoned.isEmpty()) {
            return stillPresent;
        }

        for (Notification row : serverRows) {
            if (row == null) continue;
            TombstonePolicy.recordTombstonedMatch(
                    row.getMongoId(), tombstoned, stillPresent);
        }
        return stillPresent;
    }
}

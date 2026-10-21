package com.example.myecowatch1.data.repository;

import com.example.myecowatch1.data.models.History;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure reconciliation rules for the history sync, kept free of Android and
 * Room types so the permanent-delete contract can be unit tested.
 *
 * <p>The bug this encodes: history sync used to treat "no local row" as "new
 * row" and re-insert it. A permanent delete removes the local row, so the very
 * next {@code GET history/my-history} resurrected it. A soft delete kept the
 * row and was therefore unaffected. The only correct discriminator is an
 * explicit record of what the user deleted.
 */
public final class HistorySyncPolicy {

    private HistorySyncPolicy() {
    }

    /**
     * Normalises a set of server ids for tombstoning: drops null/blank values
     * and removes duplicates while preserving first-seen order.
     */
    public static List<String> normalizeIds(List<String> mongoIds) {
        return TombstonePolicy.normalizeIds(mongoIds);
    }

    /**
     * Whether a server row may be imported into the local cache.
     *
     * <p>A row the resident permanently deleted must never be imported again,
     * no matter how many times the server returns it.
     */
    public static boolean shouldImport(String mongoId, Set<String> tombstoned) {
        return TombstonePolicy.shouldImport(mongoId, tombstoned);
    }

    /**
     * Tombstoned ids that the server still serves. These are re-asserted with a
     * delete so a previously failed or evicted upstream delete converges.
     */
    public static List<String> collectTombstonedStillOnServer(List<History> serverRows,
                                                              Set<String> tombstoned) {
        List<String> stillPresent = new ArrayList<>();
        if (serverRows == null || tombstoned == null || tombstoned.isEmpty()) {
            return stillPresent;
        }

        for (History row : serverRows) {
            if (row == null) continue;
            TombstonePolicy.recordTombstonedMatch(
                    row.getMongoId(), tombstoned, stillPresent);
        }
        return stillPresent;
    }
}

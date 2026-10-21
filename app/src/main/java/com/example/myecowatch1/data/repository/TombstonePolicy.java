package com.example.myecowatch1.data.repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-id rules shared by every tombstoned cache (history, notifications).
 *
 * <p>A permanent delete removes the row from Room. The sync then sees no local
 * counterpart for a server id that is still being served and, treating the gap
 * as "never seen before", re-imports it. The entry reappears on every refresh.
 * The only correct discriminator is an explicit, durable record of what the user
 * deleted, so these rules govern that record.
 *
 * <p>Deliberately free of Android and Room types so the permanent-delete
 * contract stays unit testable on the JVM.
 */
public final class TombstonePolicy {

    private TombstonePolicy() {
    }

    /**
     * Normalises server ids for tombstoning: drops null/blank values and
     * removes duplicates while preserving first-seen order.
     *
     * <p>One pass over this result drives the tombstone set, the local purge
     * and the upstream payload, so all three always reference the same ids.
     */
    public static List<String> normalizeIds(List<String> mongoIds) {
        List<String> normalized = new ArrayList<>();
        if (mongoIds == null) return normalized;

        Set<String> seen = new HashSet<>();
        for (String mongoId : mongoIds) {
            if (mongoId == null) continue;
            String trimmed = mongoId.trim();
            if (trimmed.isEmpty()) continue;
            if (!seen.add(trimmed)) continue;
            normalized.add(trimmed);
        }
        return normalized;
    }

    /**
     * Whether a server row may be imported into the local cache.
     *
     * <p>A row the resident permanently deleted must never be imported again,
     * no matter how many times the server returns it.
     */
    public static boolean shouldImport(String mongoId, Set<String> tombstoned) {
        if (mongoId == null || mongoId.trim().isEmpty()) return false;
        return tombstoned == null || !tombstoned.contains(mongoId.trim());
    }

    /**
     * Adds the id of a server row that matches a tombstone, ignoring ids that
     * were already recorded.
     *
     * <p>Kept separate from the entity-specific collectors so history and
     * notifications share the matching rule and cannot drift apart. The
     * destination is normally a {@link List}, whose {@code add} always reports
     * success, so membership is checked explicitly rather than relying on the
     * return value.
     *
     * @return {@code true} when the id was absent and has now been recorded
     */
    public static boolean recordTombstonedMatch(
            String mongoId,
            Set<String> tombstoned,
            List<String> destination
    ) {
        if (mongoId == null || mongoId.trim().isEmpty()) return false;
        if (tombstoned == null || destination == null) return false;

        String trimmed = mongoId.trim();
        if (!tombstoned.contains(trimmed)) return false;
        // A server may serve the same id from both the active and the trash
        // feed, and the retry delete must be queued once per id.
        if (destination.contains(trimmed)) return false;
        destination.add(trimmed);
        return true;
    }
}

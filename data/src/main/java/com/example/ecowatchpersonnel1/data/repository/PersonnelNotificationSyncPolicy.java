package com.example.ecowatchpersonnel1.data.repository;

import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure reconciliation rules for the personnel notification sync, free of Android
 * and Room types so the delete contract can be unit tested.
 *
 * <p>Two separate decisions are encoded here, and confusing them is what breaks
 * the Trash tab:
 *
 * <ul>
 *   <li><b>Trashing</b> keeps the row with {@code isDeleted = 1}. The server has
 *       no trash route and keeps serving the row, so the retained local row is
 *       the record. Nothing is tombstoned, and the row must stay importable.</li>
 *   <li><b>Destroying</b> removes the row. Its absence then looks identical to
 *       "never seen", which is why a tombstone is required: without one the next
 *       refresh re-imports and the notification the cleaner destroyed returns.</li>
 * </ul>
 */
public final class PersonnelNotificationSyncPolicy {

    private PersonnelNotificationSyncPolicy() {
    }

    /**
     * Whether a server row may be imported into the local cache.
     *
     * <p>A notification the cleaner permanently deleted must never come back, no
     * matter how many times the server returns it.
     */
    public static boolean shouldImport(String notificationId, Set<String> tombstoned) {
        if (notificationId == null || notificationId.trim().isEmpty()) return false;
        return tombstoned == null || !tombstoned.contains(notificationId.trim());
    }

    /**
     * Normalises ids for tombstoning: drops null/blank values and removes
     * duplicates while preserving first-seen order.
     *
     * <p>One pass over this result drives the tombstone set, the local purge and
     * the upstream payload, so all three always reference the same ids.
     */
    public static List<String> normalizeIds(List<String> notificationIds) {
        List<String> normalized = new ArrayList<>();
        if (notificationIds == null) return normalized;

        Set<String> seen = new HashSet<>();
        for (String id : notificationIds) {
            if (id == null) continue;
            String trimmed = id.trim();
            if (trimmed.isEmpty()) continue;
            if (!seen.add(trimmed)) continue;
            normalized.add(trimmed);
        }
        return normalized;
    }

    /**
     * Tombstoned ids the server still serves. These are re-asserted with a delete
     * so a previously failed or evicted upstream delete converges instead of
     * leaving a notification the cleaner destroyed back on screen.
     */
    public static List<String> collectTombstonedStillOnServer(
            List<PersonnelNotification> serverRows, Set<String> tombstoned) {
        List<String> stillPresent = new ArrayList<>();
        if (serverRows == null || tombstoned == null || tombstoned.isEmpty()) {
            return stillPresent;
        }

        Set<String> added = new HashSet<>();
        for (PersonnelNotification row : serverRows) {
            if (row == null) continue;
            String id = PersonnelNotificationIds.resolve(row);
            if (id == null || id.trim().isEmpty()) continue;
            if (!tombstoned.contains(id.trim())) continue;
            if (added.add(id.trim())) {
                stillPresent.add(id.trim());
            }
        }
        return stillPresent;
    }
}

package com.example.myecowatch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.myecowatch1.data.models.Notification;
import com.example.myecowatch1.data.repository.NotificationSyncPolicy;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regression cover for the notification permanent-delete resurrection bug.
 *
 * <p>Previously the Room {@code DELETE} only ran after the server confirmed the
 * delete, so an offline or failing request left the notification in the local
 * database. Even on success, the row's absence was indistinguishable from "never
 * seen": the sync re-inserted it and it came back on every pull-to-refresh.
 * These tests pin the corrected rule: deletion is recorded, and a tombstoned id
 * is never imported again.
 */
public class NotificationPermanentDeleteTest {

    /** Server shape returned by GET /api/notifications/my-notifications. */
    private static Notification serverRow(String id) {
        return new Gson().fromJson(
                "{\"notification_id\":\"" + id + "\",\"resident_id\":\"RES-001\","
                        + "\"title\":\"Personnel Assigned\","
                        + "\"message\":\"Cleanup personnel have been assigned.\","
                        + "\"type\":\"PERSONNEL_ASSIGNED\","
                        + "\"is_read\":false,"
                        + "\"created_at\":\"2026-09-25T05:53:12.123+00:00\"}",
                Notification.class);
    }

    @Test
    public void tombstonedIdIsNeverReImported() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertFalse(NotificationSyncPolicy.shouldImport("42", tombstoned));
    }

    @Test
    public void untombstonedIdIsStillImported() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertTrue(NotificationSyncPolicy.shouldImport("43", tombstoned));
    }

    /**
     * The regression itself: the server keeps serving a deleted id, and the sync
     * must not re-insert it. An empty tombstone set reproduces the old, broken
     * behaviour.
     */
    @Test
    public void deletedNotificationStaysDeletedAcrossRepeatedRefreshes() {
        Notification row = serverRow("42");

        // Before the fix: no tombstone existed, so the row looked brand new.
        assertTrue(NotificationSyncPolicy.shouldImport(
                row.getMongoId(), Collections.emptySet()));

        // After the resident hard-deletes it from Trash, a tombstone is recorded.
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        // Every subsequent refresh re-evaluates the same row and refuses it.
        for (int refresh = 1; refresh <= 5; refresh++) {
            assertFalse("notification resurrected on refresh " + refresh,
                    NotificationSyncPolicy.shouldImport(row.getMongoId(), tombstoned));
        }
    }

    @Test
    public void serverIdBindsToMongoIdNotTheLocalPrimaryKey() {
        // Documents why the presence probe used to mis-classify deleted rows:
        // the server id column lands on mongoId, leaving the Room autoGenerate
        // primary key at 0, so a re-insert gets a brand new local id.
        Notification row = serverRow("42");

        assertEquals("42", row.getMongoId());
        assertEquals(0, row.getId());
    }

    @Test
    public void tombstonedIdsTheServerStillServesAreReAsserted() {
        Notification deleted = serverRow("42");
        Notification alsoDeleted = serverRow("77");
        Notification kept = serverRow("43");

        List<Notification> serverRows = Arrays.asList(deleted, alsoDeleted, kept);
        Set<String> tombstoned = new HashSet<>(Arrays.asList("42", "77"));

        List<String> stillOnServer = NotificationSyncPolicy
                .collectTombstonedStillOnServer(serverRows, tombstoned);

        // Only the tombstoned ids are re-deleted; the live row is untouched.
        assertEquals(Arrays.asList("42", "77"), stillOnServer);
    }

    @Test
    public void nothingToReAssertWhenTheServerHonouredTheDelete() {
        Notification kept = serverRow("43");
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertTrue(NotificationSyncPolicy
                .collectTombstonedStillOnServer(Collections.singletonList(kept), tombstoned)
                .isEmpty());
    }

    /**
     * A duplicate server id in one response must not queue the retry delete
     * twice, so the collector deduplicates.
     */
    @Test
    public void duplicateServerRowsCollapseToOneRetry() {
        Notification first = serverRow("42");
        Notification second = serverRow("42");
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertEquals(Collections.singletonList("42"), NotificationSyncPolicy
                .collectTombstonedStillOnServer(Arrays.asList(first, second), tombstoned));
    }

    @Test
    public void normalizeIdsDropsBlankAndDuplicateValues() {
        List<String> raw = new ArrayList<>(Arrays.asList(
                "42", "  42  ", null, "", "   ", "77"));

        List<String> normalized = NotificationSyncPolicy.normalizeIds(raw);

        assertEquals(Arrays.asList("42", "77"), normalized);
    }

    @Test
    public void normalizeIdsToleratesNull() {
        assertTrue(NotificationSyncPolicy.normalizeIds(null).isEmpty());
    }

    @Test
    public void whitespacePaddedServerIdStillMatchesItsTombstone() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertFalse(NotificationSyncPolicy.shouldImport("  42  ", tombstoned));
        assertFalse(NotificationSyncPolicy.shouldImport("", tombstoned));
        assertFalse(NotificationSyncPolicy.shouldImport(null, tombstoned));
    }

    /**
     * Soft delete must stay unaffected: the row is retained locally with
     * isDeleted=1, so it is still imported and only the Trash tab shows it.
     * Permanent-delete tombstones must not swallow soft-deleted ids, otherwise
     * a notification the user only meant to trash would vanish with no way back.
     */
    @Test
    public void softDeletedNotificationsAreUnaffectedByPermanentDeleteTombstones() {
        Notification softDeleted = serverRow("42");
        softDeleted.setDeleted(true);

        assertTrue(softDeleted.isDeleted());
        assertTrue("soft-deleted row keeps its tombstone-free id",
                NotificationSyncPolicy.shouldImport(
                        softDeleted.getMongoId(), Collections.emptySet()));
    }
}

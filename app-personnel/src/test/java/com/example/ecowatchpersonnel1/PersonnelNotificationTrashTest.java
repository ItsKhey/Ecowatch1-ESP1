package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.data.local.PersonnelNotificationEntity;
import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;
import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationIds;
import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationSyncPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regression cover for the personnel Trash tab.
 *
 * <p>The Trash tab was a stub that always submitted an empty list, and the only
 * delete path called the server's hard delete straight from the All tab, so there
 * was no soft delete at all. The personnel backend also has no move-to-trash or
 * restore route, which makes the two deletion steps entirely local decisions.
 * These tests pin that split: trashing retains the row, destroying tombstones it.
 */
public class PersonnelNotificationTrashTest {

    private static PersonnelNotification serverRow(String id) {
        PersonnelNotification n = new PersonnelNotification();
        n.notificationId = id;
        n.title = "Personnel Assigned";
        n.type = PersonnelNotification.TYPE_ASSIGNED;
        n.createdAt = "2026-09-25T05:53:12.123+00:00";
        return n;
    }

    // ---- id resolution: the list and push paths disagree on which field is set ----

    @Test
    public void notificationIdWinsWhenBothFieldsArePresent() {
        PersonnelNotification n = serverRow("42");
        n.id = "stale-42";

        assertEquals("42", PersonnelNotificationIds.resolve(n));
    }

    @Test
    public void idIsUsedWhenNotificationIdIsAbsent() {
        PersonnelNotification n = new PersonnelNotification();
        n.id = "42";

        assertEquals("42", PersonnelNotificationIds.resolve(n));
    }

    @Test
    public void unaddressableRowResolvesToNull() {
        assertNull(PersonnelNotificationIds.resolve(new PersonnelNotification()));
        assertNull(PersonnelNotificationIds.resolve((PersonnelNotification) null));
    }

    @Test
    public void whitespaceOnlyIdIsTreatedAsAbsent() {
        PersonnelNotification n = new PersonnelNotification();
        n.notificationId = "   ";
        n.id = "42";

        assertEquals("42", PersonnelNotificationIds.resolve(n));
    }

    // ---- soft delete: retained, not tombstoned ----

    /**
     * The rule that makes Trash work at all. Trashing keeps the row with
     * isDeleted=1; because the server never heard about the trash and still serves
     * the row, the sync would otherwise put it straight back into the All tab.
     */
    @Test
    public void trashedRowSurvivesReSyncBecauseItIsStillImportable() {
        PersonnelNotificationEntity entity =
                PersonnelNotificationEntity.from(serverRow("42"), "PER-1");
        entity.isDeleted = true;
        entity.deletedAt = 1758000000000L;

        assertTrue(entity.isDeleted);
        assertTrue("a trashed row must stay importable, or the sync resurrects it",
                PersonnelNotificationSyncPolicy.shouldImport(
                        entity.notificationId, Collections.emptySet()));
    }

    @Test
    public void aFreshRowFromTheServerIsUntrashed() {
        PersonnelNotificationEntity entity =
                PersonnelNotificationEntity.from(serverRow("42"), "PER-1");

        assertFalse(entity.isDeleted);
        assertNull(entity.deletedAt);
    }

    @Test
    public void entityRoundTripsThroughTheModel() {
        PersonnelNotification source = serverRow("42");
        source.assignmentId = "ASG-7";
        source.reportId = "REP-3";
        source.message = "Head to Cluster 3.";
        source.isRead = true;

        PersonnelNotificationEntity entity =
                PersonnelNotificationEntity.from(source, "PER-1");
        PersonnelNotification restored = entity.toModel();

        assertEquals("42", restored.notificationId);
        assertEquals("42", restored.id);
        assertEquals("ASG-7", restored.assignmentId);
        assertEquals("REP-3", restored.reportId);
        assertEquals("Personnel Assigned", restored.title);
        assertEquals("Head to Cluster 3.", restored.message);
        assertEquals(PersonnelNotification.TYPE_ASSIGNED, restored.type);
        assertTrue(restored.isRead);
        assertTrue(restored.opensTask());
    }

    @Test
    public void unaddressableRowIsNotStored() {
        PersonnelNotificationEntity entity =
                PersonnelNotificationEntity.from(new PersonnelNotification(), "PER-1");

        assertEquals("", entity.notificationId);
    }

    // ---- hard delete: tombstoned, never re-imported ----

    @Test
    public void tombstonedIdIsNeverReImported() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertFalse(PersonnelNotificationSyncPolicy.shouldImport("42", tombstoned));
    }

    @Test
    public void untombstonedIdIsStillImported() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertTrue(PersonnelNotificationSyncPolicy.shouldImport("43", tombstoned));
    }

    /**
     * The regression itself: the server keeps serving a destroyed id, and the sync
     * must not re-insert it. An empty tombstone set reproduces the old behaviour
     * where a "deleted" notification came back on every pull-to-refresh.
     */
    @Test
    public void destroyedNotificationStaysDestroyedAcrossRepeatedRefreshes() {
        PersonnelNotification row = serverRow("42");

        // Before the fix: no tombstone existed, so the row looked brand new.
        assertTrue(PersonnelNotificationSyncPolicy.shouldImport(
                row.notificationId, Collections.emptySet()));

        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        for (int refresh = 1; refresh <= 5; refresh++) {
            assertFalse("notification resurrected on refresh " + refresh,
                    PersonnelNotificationSyncPolicy.shouldImport("42", tombstoned));
        }
    }

    @Test
    public void destroyedIdsTheServerStillServesAreReAsserted() {
        PersonnelNotification deleted = serverRow("42");
        PersonnelNotification alsoDeleted = serverRow("77");
        PersonnelNotification kept = serverRow("43");

        Set<String> tombstoned = new HashSet<>(Arrays.asList("42", "77"));

        assertEquals(Arrays.asList("42", "77"), PersonnelNotificationSyncPolicy
                .collectTombstonedStillOnServer(
                        Arrays.asList(deleted, alsoDeleted, kept), tombstoned));
    }

    @Test
    public void nothingToReAssertWhenTheServerHonouredTheDelete() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertTrue(PersonnelNotificationSyncPolicy
                .collectTombstonedStillOnServer(
                        Collections.singletonList(serverRow("43")), tombstoned)
                .isEmpty());
    }

    /** The list and the trash feed can both carry an id; retry the delete once. */
    @Test
    public void duplicateServerRowsCollapseToOneRetry() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertEquals(Collections.singletonList("42"), PersonnelNotificationSyncPolicy
                .collectTombstonedStillOnServer(
                        Arrays.asList(serverRow("42"), serverRow("42")), tombstoned));
    }

    @Test
    public void reAssertMatchesRowsIdentifiedByTheFallbackField() {
        PersonnelNotification n = new PersonnelNotification();
        n.id = "42";
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertEquals(Collections.singletonList("42"), PersonnelNotificationSyncPolicy
                .collectTombstonedStillOnServer(Collections.singletonList(n), tombstoned));
    }

    // ---- id normalisation shared by the tombstone set, the purge and the payload ----

    @Test
    public void normalizeIdsDropsBlankAndDuplicateValues() {
        List<String> normalized = PersonnelNotificationSyncPolicy.normalizeIds(
                new ArrayList<>(Arrays.asList("42", "  42  ", null, "", "   ", "77")));

        assertEquals(Arrays.asList("42", "77"), normalized);
    }

    @Test
    public void normalizeIdsToleratesNull() {
        assertTrue(PersonnelNotificationSyncPolicy.normalizeIds(null).isEmpty());
    }

    @Test
    public void whitespacePaddedIdStillMatchesItsTombstone() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertFalse(PersonnelNotificationSyncPolicy.shouldImport("  42  ", tombstoned));
        assertFalse(PersonnelNotificationSyncPolicy.shouldImport("", tombstoned));
        assertFalse(PersonnelNotificationSyncPolicy.shouldImport(null, tombstoned));
    }
}

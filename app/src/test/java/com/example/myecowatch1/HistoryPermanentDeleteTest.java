package com.example.myecowatch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.myecowatch1.data.models.History;
import com.example.myecowatch1.data.repository.HistorySyncPolicy;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regression cover for the permanent-delete resurrection bug.
 *
 * <p>Previously {@code fetchHistory} re-imported any server row that had no
 * local counterpart. A permanent delete removes the local row, so the next
 * pull-to-refresh brought the entry back. These tests pin the corrected rule:
 * deletion is recorded, and a tombstoned id is never imported again.
 */
public class HistoryPermanentDeleteTest {

    /** Server shape returned by GET /api/history/my-history. */
    private static History serverRow(String id) {
        return new Gson().fromJson(
                "{\"id\":\"" + id + "\",\"action\":\"PERMANENTLY_DELETED\","
                        + "\"description\":\"Report removed\",\"timestamp\":1758000000000}",
                History.class);
    }

    @Test
    public void tombstonedIdIsNeverReImported() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertFalse(HistorySyncPolicy.shouldImport("42", tombstoned));
    }

    @Test
    public void untombstonedIdIsStillImported() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertTrue(HistorySyncPolicy.shouldImport("43", tombstoned));
    }

    /**
     * The regression itself: the server keeps serving a deleted id, and the
     * sync must not re-insert it. An empty tombstone set reproduces the old,
     * broken behaviour.
     */
    @Test
    public void deletedEntryStaysDeletedAcrossRepeatedRefreshes() {
        History row = serverRow("42");

        // Before the fix: no tombstone existed, so the row looked brand new.
        assertTrue(HistorySyncPolicy.shouldImport(row.getMongoId(), Collections.emptySet()));

        // After the user permanently deletes it, a tombstone is recorded.
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        // Every subsequent refresh re-evaluates the same row and refuses it.
        for (int refresh = 1; refresh <= 5; refresh++) {
            assertFalse("row resurrected on refresh " + refresh,
                    HistorySyncPolicy.shouldImport(row.getMongoId(), tombstoned));
        }
    }

    @Test
    public void serverIdBindsToMongoIdNotTheLocalPrimaryKey() {
        // Documents why the old presence probe mis-classified deleted rows:
        // the server "id" column lands on mongoId, leaving the Room
        // autoGenerate primary key at 0, so a re-insert gets a brand new local
        // id and looks like a fresh entry.
        History row = serverRow("42");

        assertEquals("42", row.getMongoId());
        assertEquals("42", row.getStoredMongoId());
        assertEquals(0, row.getId());
    }

    @Test
    public void tombstonedIdsTheServerStillServesAreReAsserted() {
        History deleted = serverRow("42");
        History alsoDeleted = serverRow("77");
        History kept = serverRow("43");

        List<History> serverRows = Arrays.asList(deleted, alsoDeleted, kept);
        Set<String> tombstoned = new HashSet<>(Arrays.asList("42", "77"));

        List<String> stillOnServer = HistorySyncPolicy.collectTombstonedStillOnServer(
                serverRows, tombstoned);

        // Only the tombstoned ids are re-deleted; the live row is untouched.
        assertEquals(Arrays.asList("42", "77"), stillOnServer);
    }

    @Test
    public void nothingToReAssertWhenTheServerHonouredTheDelete() {
        History kept = serverRow("43");
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertTrue(HistorySyncPolicy
                .collectTombstonedStillOnServer(Collections.singletonList(kept), tombstoned)
                .isEmpty());
    }

    @Test
    public void normalizeIdsDropsBlankAndDuplicateValues() {
        List<String> raw = new ArrayList<>(Arrays.asList(
                "42", "  42  ", null, "", "   ", "77"));

        List<String> normalized = HistorySyncPolicy.normalizeIds(raw);

        assertEquals(Arrays.asList("42", "77"), normalized);
    }

    @Test
    public void normalizeIdsToleratesNull() {
        assertTrue(HistorySyncPolicy.normalizeIds(null).isEmpty());
    }

    @Test
    public void whitespacePaddedServerIdStillMatchesItsTombstone() {
        Set<String> tombstoned = new HashSet<>(Collections.singletonList("42"));

        assertFalse(HistorySyncPolicy.shouldImport("  42  ", tombstoned));
        assertFalse(HistorySyncPolicy.shouldImport("", tombstoned));
        assertFalse(HistorySyncPolicy.shouldImport(null, tombstoned));
    }

    /**
     * Soft delete must stay unaffected: the row is retained locally with
     * isDeleted=1, so it is still imported/kept and only the Trash tab shows it.
     * Permanent-delete tombstones must not swallow soft-deleted ids.
     */
    @Test
    public void softDeletedRowsAreUnaffectedByPermanentDeleteTombstones() {
        History softDeleted = serverRow("42");
        softDeleted.setDeleted(true);
        softDeleted.setDeletedAt(1758000000000L);

        assertTrue(softDeleted.isDeleted());
        assertTrue("soft-deleted row keeps its tombstone-free id",
                HistorySyncPolicy.shouldImport(softDeleted.getMongoId(), Collections.emptySet()));
    }
}

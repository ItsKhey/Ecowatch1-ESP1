package com.example.ecowatchpersonnel1.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The three task counts shown as cards on the profile screen.
 *
 * <p>They are derived, not owned: the personnel API has no statistics route, so
 * the numbers are recounted from the task list on every profile load. The last
 * known values are kept here so the cards can show real numbers the moment the
 * screen opens (and still show them with no connection) instead of waiting on a
 * round trip, or falling back to the "0" the layout ships with.
 *
 * <p>Kept in preferences rather than a Room table for the same reason
 * {@link SavedAccounts} is: three ints that are never queried do not justify a
 * database version bump and a migration.
 *
 * <p>The admin app keeps its own copy of the same three numbers for the same
 * cleaner, so the two screens agree even when one of them is showing a cached
 * value.
 */
public final class TaskStatsStore {

    private static final String PREFS = "ecowatch_task_stats";
    private static final String KEY_OWNER = "owner_personnel_id";
    private static final String KEY_TOTAL_ASSIGNED = "total_assigned";
    private static final String KEY_ON_HAND = "on_hand";
    private static final String KEY_IN_PROGRESS = "in_progress";
    private static final String KEY_COMPLETED = "completed";

    private TaskStatsStore() {
        // Utility class.
    }

    /** Immutable so the screen and the store always agree on one set of numbers. */
    public static final class Stats {
        /** Every task this cleaner was ever given, whatever its status now. */
        public final int totalAssigned;
        /** Work still in this cleaner's hands: assigned, accepted or in progress. */
        public final int onHand;
        public final int inProgress;
        public final int completed;

        public Stats(int totalAssigned, int onHand, int inProgress, int completed) {
            this.totalAssigned = Math.max(0, totalAssigned);
            this.onHand = Math.max(0, onHand);
            this.inProgress = Math.max(0, inProgress);
            this.completed = Math.max(0, completed);
        }

        /** Counts for a cleaner who has never had a task. */
        public static Stats empty() {
            return new Stats(0, 0, 0, 0);
        }

        /** True when nothing has ever been recorded for this cleaner. */
        public boolean isEmpty() {
            return totalAssigned == 0 && onHand == 0 && inProgress == 0 && completed == 0;
        }
    }

    /**
     * The last recorded counts for this cleaner, or zeros before anything has
     * been recorded.
     *
     * <p>Scoped to the personnel id because one device signs into several field
     * accounts: without the check, a second cleaner would see the first one's
     * totals for as long as the recount takes, or for good with no connection.
     */
    public static Stats load(Context context, String owner) {
        SharedPreferences prefs = prefs(context);
        if (!sameOwner(owner, prefs.getString(KEY_OWNER, null))) {
            return Stats.empty();
        }
        return new Stats(
                prefs.getInt(KEY_TOTAL_ASSIGNED, 0),
                prefs.getInt(KEY_ON_HAND, 0),
                prefs.getInt(KEY_IN_PROGRESS, 0),
                prefs.getInt(KEY_COMPLETED, 0));
    }

    /**
     * Records the freshly counted values for this cleaner, replacing the
     * previous ones.
     *
     * <p>Replaces rather than accumulates: these are point-in-time totals of a
     * list the server can add to or drop from, so the latest recount is the only
     * one that is true.
     */
    public static void save(Context context, String owner, Stats stats) {
        if (owner == null || owner.trim().isEmpty()) {
            return;
        }
        Stats value = stats == null ? Stats.empty() : stats;
        prefs(context).edit()
                .putString(KEY_OWNER, owner.trim())
                .putInt(KEY_TOTAL_ASSIGNED, value.totalAssigned)
                .putInt(KEY_ON_HAND, value.onHand)
                .putInt(KEY_IN_PROGRESS, value.inProgress)
                .putInt(KEY_COMPLETED, value.completed)
                .apply();
    }

    private static boolean sameOwner(String requested, String stored) {
        if (requested == null || requested.trim().isEmpty()) {
            return false;
        }
        return requested.trim().equals(stored);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

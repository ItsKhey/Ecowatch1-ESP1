package com.example.ecowatchpersonnel1.data.remote;

/**
 * Response for {@code GET /api/personnel/task-counts}.
 *
 * <p>Counts straight from the assignment rows, so they cover every assignment
 * rather than one page of the task list. {@link Counts#onHand()} is derived the
 * same way the profile cards define it: work still in this cleaner's hands.
 */
public class TaskCountsResponse {

    public boolean success;
    public Counts counts;

    public Counts getCounts() {
        return counts == null ? new Counts() : counts;
    }

    /** The per-status totals the server counted. */
    public static class Counts {
        public int totalAssigned;
        public int total_assigned;
        public int assigned;
        public int accepted;
        public int inProgress;
        public int in_progress;
        public int completed;
        public int cancelled;

        /**
         * Every task this cleaner was ever given, whatever its status now.
         *
         * <p>Falls back to the sum of the per-status counts if the server omits
         * the total, so the card still shows the right number rather than zero.
         */
        public int totalAssignedCount() {
            int explicit = Math.max(totalAssigned, total_assigned);
            if (explicit > 0) return explicit;
            return Math.max(0, assigned) + Math.max(0, accepted) + inProgressCount()
                    + completedCount() + Math.max(0, cancelled);
        }

        /** Assigned, accepted or in progress: still in this cleaner's hands. */
        public int onHand() {
            return Math.max(0, assigned) + Math.max(0, accepted) + inProgressCount();
        }

        /** Accepts either spelling of the in-progress field. */
        public int inProgressCount() {
            return inProgress > 0 ? Math.max(0, inProgress) : Math.max(0, in_progress);
        }

        public int completedCount() {
            return Math.max(0, completed);
        }
    }
}
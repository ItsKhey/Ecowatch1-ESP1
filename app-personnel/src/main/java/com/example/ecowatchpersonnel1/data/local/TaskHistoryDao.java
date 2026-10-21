package com.example.ecowatchpersonnel1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * The device's record of the tasks this cleaner has been given.
 *
 * <p>Every query is scoped by {@code personnelId}: field devices are shared, so a
 * table that was not scoped would show the previous account's work until the
 * first sync replaced it.
 */
@Dao
public interface TaskHistoryDao {

    /**
     * Newest first, where "newest" is the moment the task last moved rather than
     * the moment it was created: a task accepted today should sit above one
     * assigned today but finished last week.
     */
    @Query("SELECT * FROM task_history WHERE personnelId = :personnelId "
            + "ORDER BY COALESCE(completedAt, startedAt, acceptedAt, assignedAt, updatedAt, createdAt) DESC")
    LiveData<List<TaskHistoryEntity>> observeForPersonnel(String personnelId);

    /**
     * Counts per status, used to label the filter chips from the same rows the
     * list shows rather than from the server's paged task list.
     */
    @Query("SELECT status, COUNT(*) AS total FROM task_history "
            + "WHERE personnelId = :personnelId GROUP BY status")
    LiveData<List<TaskStatusCount>> observeStatusCounts(String personnelId);

    @Query("SELECT COUNT(*) FROM task_history WHERE personnelId = :personnelId")
    LiveData<Integer> observeTotal(String personnelId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<TaskHistoryEntity> entities);

    /**
     * Drops rows the server no longer returns.
     *
     * <p>Only called after a successful full sync, and skipped when the server
     * returned nothing: an empty page after a flaky connection must not be read
     * as "this cleaner has no work", which would silently erase their history.
     *
     * @param keepIds assignment ids the server just sent; may be null to keep all.
     */
    @Query("DELETE FROM task_history WHERE personnelId = :personnelId "
            + "AND assignmentId NOT IN (:keepIds)")
    void deleteMissing(String personnelId, List<String> keepIds);

    /** Wipes one cleaner's rows, used when signing out of that account. */
    @Query("DELETE FROM task_history WHERE personnelId = :personnelId")
    void clearForPersonnel(String personnelId);

    /** Row count for a cleaner, used by the tests and by the sync bookkeeping. */
    @Query("SELECT COUNT(*) FROM task_history WHERE personnelId = :personnelId")
    int countForPersonnel(String personnelId);

    /** One status bucket, for the chip labels. */
    class TaskStatusCount {
        public String status;
        public int total;
    }
}
package com.example.myecowatch1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myecowatch1.data.models.History;

import java.util.List;

@Dao
public interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHistory(History history);

    @Query("SELECT * FROM history WHERE residentId = :residentId AND isDeleted = 0 ORDER BY timestampValue DESC, id DESC")
    LiveData<List<History>> getHistoryByResidentId(String residentId);

    @Query("SELECT * FROM history WHERE residentId = :residentId AND isDeleted = 1 ORDER BY timestampValue DESC, id DESC")
    LiveData<List<History>> getTrashHistoryByResidentId(String residentId);

    @Query("UPDATE history SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    void softDelete(int id, long deletedAt);

    @Query("UPDATE history SET isDeleted = 0, deletedAt = NULL WHERE id = :id")
    void restore(int id);

    @Query("SELECT * FROM history WHERE mongoId = :mongoId LIMIT 1")
    History getHistoryByMongoId(String mongoId);

    @Query("DELETE FROM history WHERE mongoId IS NULL AND residentId = :residentId")
    void deleteLocalDuplicates(String residentId);

    @Query("DELETE FROM history")
    void deleteAllHistory();

    @Query("UPDATE history SET residentId = :residentId WHERE residentId IS NULL")
    void updateNullResidentIds(String residentId);

    @Query("UPDATE history SET reportId = :serverReportId WHERE reportId = :localReportId AND residentId = :residentId")
    void updateReportId(String localReportId, String serverReportId, String residentId);

    @Query("SELECT COUNT(*) FROM history WHERE reportId = :reportId AND residentId = :residentId")
    int countByReportId(String reportId, String residentId);

    @Query("DELETE FROM history WHERE id IN (:ids)")
    void deleteHistoryByIds(List<Integer> ids);

    @Query("UPDATE history SET isDeleted = 1, deletedAt = :deletedAt WHERE id IN (:ids)")
    void bulkSoftDelete(List<Integer> ids, long deletedAt);

    @Query("UPDATE history SET isDeleted = 0, deletedAt = NULL WHERE id IN (:ids)")
    void bulkRestore(List<Integer> ids);

    @Query("SELECT * FROM history WHERE id IN (:ids)")
    List<History> getHistoryByIds(List<Integer> ids);

    /**
     * Removes every local copy of the given server ids, not just the rows the
     * user happened to select. The history table has no unique index on
     * mongoId, so a second copy of the same server record would otherwise
     * survive a permanent delete and stay on screen.
     */
    @Query("DELETE FROM history WHERE mongoId IN (:mongoIds) AND residentId = :residentId")
    void deleteByMongoIds(List<String> mongoIds, String residentId);
}

package com.example.myecowatch1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myecowatch1.data.models.Report;

import java.util.List;

@Dao
public interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertReport(Report report);

    @Query("SELECT * FROM reports WHERE residentId = :residentId AND isDeleted = 0 AND isArchived = 0 AND status = 'Pending' ORDER BY id DESC")
    LiveData<List<Report>> getInboxReports(String residentId);

    @Query("SELECT * FROM reports WHERE residentId = :residentId AND isDeleted = 0 AND (isArchived = 1 OR status IN ('Completed', 'Denied')) ORDER BY id DESC")
    LiveData<List<Report>> getArchivedReports(String residentId);

    @Query("SELECT * FROM reports WHERE residentId = :residentId AND isDeleted = 1 ORDER BY id DESC")
    LiveData<List<Report>> getTrashReports(String residentId);

    @Query("UPDATE reports SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :localId")
    void softDelete(int localId, long deletedAt);

    @Query("UPDATE reports SET isDeleted = 0, isArchived = 0, deletedAt = NULL, isRestored = 1 WHERE id = :localId")
    void restoreReport(int localId);

    @Query("UPDATE reports SET isArchived = 1 WHERE id = :localId")
    void archiveReport(int localId);

    @Query("DELETE FROM reports WHERE isDeleted = 1 AND deletedAt < :threshold")
    void permanentDeleteOldTrash(long threshold);

    @Query("UPDATE reports SET isDeleted = 1, deletedAt = :deletedAt WHERE id IN (:ids)")
    void bulkSoftDelete(List<Integer> ids, long deletedAt);

    @Query("UPDATE reports SET isArchived = 1 WHERE id IN (:ids)")
    void bulkArchive(List<Integer> ids);

    @Query("UPDATE reports SET isDeleted = 0, isArchived = 0, deletedAt = NULL, isRestored = 1 WHERE id IN (:ids)")
    void bulkRestore(List<Integer> ids);

    @Query("SELECT * FROM reports WHERE residentId = :residentId ORDER BY id DESC")
    LiveData<List<Report>> getAllReports(String residentId);

    @Query("SELECT * FROM reports WHERE isSynced = 0 AND residentId = :residentId")
    List<Report> getUnsyncedReports(String residentId);

    @Query("UPDATE reports SET isSynced = 1, reportId = :reportId, mongoId = :mongoId WHERE id = :localId")
    void markAsSynced(int localId, String reportId, String mongoId);

    @Query("SELECT * FROM reports WHERE mongoId = :mongoId LIMIT 1")
    Report getReportByMongoId(String mongoId);

    @Query("SELECT * FROM reports WHERE reportId = :reportId LIMIT 1")
    Report getReportByServerId(String reportId);

    @Query("SELECT * FROM reports WHERE id IN (:ids)")
    List<Report> getReportsByIds(List<Integer> ids);

    @Query("DELETE FROM reports WHERE id IN (:ids)")
    void bulkDeleteByIds(List<Integer> ids);

    @Query("UPDATE reports SET residentId = :residentId WHERE residentId IS NULL OR residentId = ''")
    void updateNullResidentIds(String residentId);

    @Query("DELETE FROM reports WHERE reportId = :reportId")
    void deleteByServerId(String reportId);

    @Query("DELETE FROM reports WHERE id = :localId")
    void deleteByLocalId(int localId);

    @Query("DELETE FROM reports")
    void deleteAllReports();

    @Query("DELETE FROM reports WHERE reportId IS NULL AND isSynced = 1")
    void deleteCleanup();
}
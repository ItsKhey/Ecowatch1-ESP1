package com.example.ecowatchpersonnel1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * The device's record of this cleaner's notifications, split by trash state.
 *
 * <p>Every query is scoped by {@code personnelId} for the same reason
 * {@link TaskHistoryDao} is: field devices are shared, so an unscoped table would
 * show the previous account's alerts until the first sync replaced them.
 */
@Dao
public interface PersonnelNotificationDao {

    /** The All tab: everything the cleaner has not trashed. */
    @Query("SELECT * FROM personnel_notifications WHERE personnelId = :personnelId "
            + "AND isDeleted = 0 ORDER BY createdAt DESC, notificationId DESC")
    LiveData<List<PersonnelNotificationEntity>> observeActive(String personnelId);

    /** The Trash tab, newest deletion first so the last action leads. */
    @Query("SELECT * FROM personnel_notifications WHERE personnelId = :personnelId "
            + "AND isDeleted = 1 ORDER BY COALESCE(deletedAt, syncedAt) DESC")
    LiveData<List<PersonnelNotificationEntity>> observeTrash(String personnelId);

    @Query("SELECT COUNT(*) FROM personnel_notifications WHERE personnelId = :personnelId "
            + "AND isDeleted = 1")
    LiveData<Integer> observeTrashCount(String personnelId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PersonnelNotificationEntity> entities);

    @Query("SELECT * FROM personnel_notifications WHERE personnelId = :personnelId "
            + "AND notificationId = :notificationId LIMIT 1")
    PersonnelNotificationEntity getById(String personnelId, String notificationId);

    /**
     * Moves a row into Trash without removing it.
     *
     * <p>No server counterpart exists: the personnel backend has no
     * move-to-trash route, so this is the whole soft delete.
     */
    @Query("UPDATE personnel_notifications SET isDeleted = 1, deletedAt = :deletedAt "
            + "WHERE personnelId = :personnelId AND notificationId = :notificationId")
    void softDelete(String personnelId, String notificationId, long deletedAt);

    /** Takes a row back out of Trash, which puts it in the All tab again. */
    @Query("UPDATE personnel_notifications SET isDeleted = 0, deletedAt = NULL "
            + "WHERE personnelId = :personnelId AND notificationId = :notificationId")
    void restore(String personnelId, String notificationId);

    /** Destroys the row. Only ever called for a row the cleaner confirmed in Trash. */
    @Query("DELETE FROM personnel_notifications WHERE personnelId = :personnelId "
            + "AND notificationId = :notificationId")
    void hardDelete(String personnelId, String notificationId);

    /** Wipes one cleaner's rows, used when signing out of that account. */
    @Query("DELETE FROM personnel_notifications WHERE personnelId = :personnelId")
    void clearForPersonnel(String personnelId);
}

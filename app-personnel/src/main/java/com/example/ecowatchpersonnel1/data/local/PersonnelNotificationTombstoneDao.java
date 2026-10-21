package com.example.ecowatchpersonnel1.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Durable tombstones for notifications destroyed from the Trash tab.
 *
 * @see PersonnelNotificationTombstone for why the delete has to be recorded
 */
@Dao
public interface PersonnelNotificationTombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PersonnelNotificationTombstone> tombstones);

    /**
     * Server notification ids this cleaner permanently deleted. The sync reads
     * this once per refresh and refuses to re-import matching rows.
     */
    @Query("SELECT notificationId FROM personnel_notification_tombstones "
            + "WHERE personnelId = :personnelId")
    List<String> getTombstonedIds(String personnelId);

    /** Cleared on sign-out so tombstones never leak across accounts. */
    @Query("DELETE FROM personnel_notification_tombstones WHERE personnelId = :personnelId")
    void clearForPersonnel(String personnelId);

    @Query("DELETE FROM personnel_notification_tombstones")
    void deleteAll();
}

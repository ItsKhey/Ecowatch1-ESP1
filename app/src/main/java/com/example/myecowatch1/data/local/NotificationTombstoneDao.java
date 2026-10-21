package com.example.myecowatch1.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myecowatch1.data.models.NotificationTombstone;

import java.util.List;

@Dao
public interface NotificationTombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<NotificationTombstone> tombstones);

    /**
     * Server notification ids this resident permanently deleted from the Trash
     * tab. The sync reads this once per refresh and skips matching rows instead
     * of re-importing them.
     */
    @Query("SELECT mongoId FROM notification_tombstones WHERE residentId = :residentId")
    List<String> getTombstonedMongoIds(String residentId);

    /** Cleared on logout / account switch so tombstones never leak across users. */
    @Query("DELETE FROM notification_tombstones WHERE residentId = :residentId")
    void deleteAllForResident(String residentId);

    @Query("DELETE FROM notification_tombstones")
    void deleteAll();
}

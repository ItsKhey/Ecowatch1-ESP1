package com.example.myecowatch1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myecowatch1.data.models.Announcement;

import java.util.List;

@Dao
public interface AnnouncementDao {

    /**
     * REPLACE on the unique announcementId keeps the feed idempotent: syncing
     * the same announcement twice updates one row instead of adding another.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAnnouncements(List<Announcement> announcements);

    @Query("SELECT * FROM announcements ORDER BY "
            + "CASE WHEN createdAt IS NULL THEN 1 ELSE 0 END, createdAt DESC, id DESC")
    LiveData<List<Announcement>> getAllAnnouncements();

    @Query("SELECT COUNT(*) FROM announcements")
    int count();

    /** Drops cached announcements so a sync cannot leave a deleted one behind. */
    @Query("DELETE FROM announcements")
    void deleteAll();
}

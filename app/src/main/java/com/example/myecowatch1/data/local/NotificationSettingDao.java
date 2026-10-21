package com.example.myecowatch1.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myecowatch1.data.models.NotificationSetting;

@Dao
public interface NotificationSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveSettings(NotificationSetting settings);

    @Query("SELECT * FROM notification_settings WHERE residentId = :residentId LIMIT 1")
    NotificationSetting getSettingsByResidentId(String residentId);
}

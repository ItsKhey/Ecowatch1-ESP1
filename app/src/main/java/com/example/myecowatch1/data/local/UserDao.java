package com.example.myecowatch1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.myecowatch1.data.models.User;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Query("SELECT * FROM users LIMIT 1")
    LiveData<User> getUser();

    @Query("SELECT * FROM users LIMIT 1")
    User getSyncUser();

    @Query("DELETE FROM users")
    void logout();
}
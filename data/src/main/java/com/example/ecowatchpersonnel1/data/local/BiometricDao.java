package com.example.ecowatchpersonnel1.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * The biometric registrations saved on this device.
 *
 * <p>Every read and write is keyed by personnel id. That is the whole mechanism
 * behind accounts not overlapping on a shared phone: there is no query here that
 * can return another cleaner's row, and no write that touches one.
 */
@Dao
public interface BiometricDao {

    /**
     * Writes a registration.
     *
     * <p>REPLACE, keyed by personnel id, so re-registering the same account
     * updates its one row instead of accumulating duplicates that could disagree
     * about whether the fingerprint is enabled.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(BiometricSetting setting);

    /**
     * One cleaner's row, read on a background thread.
     *
     * <p>The blocking counterpart of {@link #observeByPersonnelId}, for the
     * decisions a click handler has to make before it can show the next screen.
     * Never call this on the main thread.
     */
    @Query("SELECT * FROM biometric_settings WHERE personnelId = :personnelId LIMIT 1")
    BiometricSetting findByPersonnelId(String personnelId);

    /**
     * Live view of one cleaner's row.
     *
     * <p>Live so the login screen and the security switch both follow the database
     * rather than a copy of it: registering a fingerprint in Security &amp; Privacy
     * makes the login button appear without anything having to tell the login
     * screen it changed.
     */
    @Query("SELECT * FROM biometric_settings WHERE personnelId = :personnelId LIMIT 1")
    LiveData<BiometricSetting> observeByPersonnelId(String personnelId);

    /**
     * Every account with a fingerprint on this device.
     *
     * <p>Used by the login screen to decide whether the button has anything to
     * open. Scoped by the query rather than by the caller, so no code path can
     * accidentally list a disabled row.
     */
    @Query("SELECT * FROM biometric_settings WHERE fingerprintEnabled = 1")
    LiveData<List<BiometricSetting>> observeRegisteredAccounts();

    /** Turns the flag off but keeps the row, so re-enabling does not lose the account. */
    @Query("UPDATE biometric_settings SET fingerprintEnabled = :enabled, updatedAt = :updatedAt "
            + "WHERE personnelId = :personnelId")
    void setEnabled(String personnelId, boolean enabled, long updatedAt);

    /**
     * Repairs the display name on an existing row.
     *
     * <p>Older builds could write the personnel id here after a fingerprint login.
     * The row still worked, but the login chooser saw it as a different account and
     * showed two rows with the tag on the wrong one. A password login knows the real
     * username, so it refreshes the row in place.
     */
    @Query("UPDATE biometric_settings SET username = :username, updatedAt = :updatedAt "
            + "WHERE personnelId = :personnelId")
    void updateUsername(String personnelId, String username, long updatedAt);

    /** Forgets one account entirely, used when it is removed from the device. */
    @Query("DELETE FROM biometric_settings WHERE personnelId = :personnelId")
    void delete(String personnelId);

    /** Synchronous list, for the background checks in the repository. */
    @Query("SELECT * FROM biometric_settings WHERE fingerprintEnabled = 1")
    List<BiometricSetting> findRegisteredAccounts();

    @Query("SELECT COUNT(*) FROM biometric_settings WHERE fingerprintEnabled = 1")
    int countRegistered();
}
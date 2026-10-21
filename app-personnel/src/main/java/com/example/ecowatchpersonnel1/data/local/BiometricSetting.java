package com.example.ecowatchpersonnel1.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One cleaner's biometric registration on this device.
 *
 * <p>Keyed by personnel id rather than by "the current user", which is what keeps
 * one account's registration from bleeding into another's: a field phone is shared,
 * and a row keyed by the session would be overwritten by whoever signed in next,
 * so cleaner B's fingerprint could unlock cleaner A's session.
 *
 * <p>The row records that a fingerprint was registered. It deliberately does
 * <b>not</b> hold the password or the JWT: this app keeps neither on disk, and a
 * biometric row that carried a credential would turn a stolen phone into a
 * signed-in one. Signing back in after signing out is a password job; the
 * fingerprint only reopens the session that is already stored.
 *
 * <p>Rows outlive sign-out on purpose. A cleaner who registers a fingerprint and
 * signs out should not have to register it again on the next shift.
 */
@Entity(tableName = "biometric_settings", indices = {@Index("fingerprintEnabled")})
public class BiometricSetting {

    /** The cleaner this registration belongs to. The primary key, by design. */
    @PrimaryKey
    @NonNull
    public String personnelId = "";

    /** Shown by the login screen so the button can say whose account it opens. */
    public String username;

    /**
     * Whether a fingerprint was registered for this account on this device.
     *
     * <p>Never cleared by sign-out. It answers "may this account be unlocked with a
     * fingerprint", not "is this account signed in"; the session answers the
     * second question separately.
     */
    public boolean fingerprintEnabled;

    /** When the row was first written, so a registration can be audited. */
    public long createdAt;

    /** When the flag last changed. */
    public long updatedAt;

    public BiometricSetting() {
    }

    public BiometricSetting(@NonNull String personnelId, String username) {
        this.personnelId = personnelId;
        this.username = username;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    /** True when this row may be used to unlock the app. */
    public boolean isFingerprintRegistered() {
        return fingerprintEnabled;
    }

    /** A name for the account, never null so a row written before this existed is safe. */
    @NonNull
    public String displayUsername() {
        if (username != null && !username.trim().isEmpty()) return username.trim();
        return personnelId;
    }
}
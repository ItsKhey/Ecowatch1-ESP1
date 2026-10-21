package com.example.myecowatch1.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Remembers an in-flight registration between app launches.
 *
 * <p>This exists for one specific reason: a resident registers, leaves the app to
 * open the verification email, and Android is free to kill the process while they
 * are away. Without this, coming back to the "I have verified" screen would find
 * no registrationId and the account could never be activated.
 *
 * <p>Only the two values the backend needs are held. No password and no bcrypt
 * hash is ever written here, and the entry is cleared the moment the account is
 * created.
 */
public class PendingRegistrationStore {

    private static final String PREF_NAME = "EcoWatchPendingRegistration";
    private static final String KEY_REGISTRATION_ID = "registration_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_SENT_AT = "sent_at";

    private final SharedPreferences preferences;

    public PendingRegistrationStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Records a registration that is waiting on its verification link.
     *
     * <p>{@code committed} is deliberate: this has to survive the process being
     * killed, which an asynchronous apply is not guaranteed to do.
     */
    public void save(@NonNull String registrationId, @NonNull String email,
                     @Nullable String username) {
        preferences.edit()
                .putString(KEY_REGISTRATION_ID, registrationId)
                .putString(KEY_EMAIL, email)
                .putString(KEY_USERNAME, username)
                .putLong(KEY_SENT_AT, System.currentTimeMillis())
                .commit();
    }

    @Nullable
    public String getRegistrationId() {
        return preferences.getString(KEY_REGISTRATION_ID, null);
    }

    @Nullable
    public String getEmail() {
        return preferences.getString(KEY_EMAIL, null);
    }

    @Nullable
    public String getUsername() {
        return preferences.getString(KEY_USERNAME, null);
    }

    /** True only when both halves of the handle are present. */
    public boolean hasPendingRegistration() {
        String registrationId = getRegistrationId();
        String email = getEmail();
        return registrationId != null && !registrationId.isEmpty()
                && email != null && !email.isEmpty();
    }

    /** Called once the account exists, so no stale handle can be replayed. */
    public void clear() {
        preferences.edit().clear().commit();
    }
}

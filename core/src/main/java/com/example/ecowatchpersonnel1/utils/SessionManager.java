package com.example.ecowatchpersonnel1.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Device-local session for the signed-in cleaner.
 *
 * <p>Holds only what the app needs to survive a cold start: the JWT, the
 * personnel id it belongs to, and the display preferences. No password is ever
 * persisted here; the server remains the only authority that issues a session.
 */
public class SessionManager {

    private static final String PREF_NAME = "EcoWatchPersonnelSession";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    private static final String KEY_PERSONNEL_ID = "personnel_id";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_LAST_ACTIVE = "last_active";
    private static final String KEY_USER_USERNAME = "user_username";
    private static final String KEY_THEME_MODE = "theme_mode";

    private static final long SESSION_TIMEOUT = 30L * 24 * 60 * 60 * 1000; // 30 days

    private final SharedPreferences sharedPreferences;

    public SessionManager(Context context) {
        sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Persists the full authenticated session atomically. */
    public synchronized void saveSession(String token, String personnelId) {
        if (token == null || token.trim().isEmpty()
                || personnelId == null || personnelId.trim().isEmpty()) {
            clearSession();
            return;
        }
        // The username is written separately by the caller (the login response, or
        // the account the fingerprint vault opened). Writing the personnel id here
        // used to overwrite the real username after a fingerprint login, which made
        // the account chooser treat the same person as two different rows.
        sharedPreferences.edit()
                .putString(KEY_JWT_TOKEN, token)
                .putString(KEY_PERSONNEL_ID, personnelId)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .commit();
    }

    public synchronized void saveAuthToken(String token) {
        sharedPreferences.edit()
                .putString(KEY_JWT_TOKEN, token)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .apply();
    }

    public synchronized void savePersonnelId(String personnelId) {
        sharedPreferences.edit().putString(KEY_PERSONNEL_ID, personnelId).apply();
    }

    public String fetchPersonnelId() {
        return sharedPreferences.getString(KEY_PERSONNEL_ID, null);
    }

    public String fetchAuthToken() {
        return sharedPreferences.getString(KEY_JWT_TOKEN, null);
    }

    public boolean isLoggedIn() {
        String token = fetchAuthToken();
        String personnelId = fetchPersonnelId();
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
                && token != null && !token.trim().isEmpty()
                && personnelId != null && !personnelId.trim().isEmpty();
    }

    public void rememberUsername(String username) {
        if (username != null && !username.trim().isEmpty()) {
            sharedPreferences.edit().putString(KEY_USER_USERNAME, username.trim()).apply();
        }
    }

    public String fetchUsername() {
        return sharedPreferences.getString(KEY_USER_USERNAME, "");
    }

    public void updateLastActive() {
        sharedPreferences.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply();
    }

    public boolean isSessionExpired() {
        if (!isLoggedIn()) return false;
        long lastActive = sharedPreferences.getLong(KEY_LAST_ACTIVE, 0);
        if (lastActive == 0) {
            updateLastActive();
            return false;
        }
        return (System.currentTimeMillis() - lastActive) > SESSION_TIMEOUT;
    }

    public synchronized void clearSession() {
        // The biometric registration is deliberately not cleared here. It lives in
        // the database keyed by personnel id, it holds no credential, and a
        // cleaner who registered a fingerprint should not have to do it again on
        // their next shift. It grants nothing on its own: unlocking still needs
        // this session's token, which is what the lines above remove.
        sharedPreferences.edit()
                .remove(KEY_JWT_TOKEN)
                .remove(KEY_PERSONNEL_ID)
                .remove(KEY_LAST_ACTIVE)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .commit();
    }

    public void setThemeMode(int mode) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public int getThemeMode() {
        // -1 is AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM.
        return sharedPreferences.getInt(KEY_THEME_MODE, -1);
    }

    }

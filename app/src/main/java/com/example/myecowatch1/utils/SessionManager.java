package com.example.myecowatch1.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.myecowatch1.data.local.AppDatabase;
import com.example.myecowatch1.data.local.BiometricDao;
import com.example.myecowatch1.data.models.BiometricSetting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionManager {
    private static final String PREF_NAME = "EcoWatchSession";
    private static final String KEY_JWT_TOKEN = "jwt_token";
    private static final String KEY_RESIDENT_ID = "resident_id";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_LAST_ACTIVE = "last_active";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PASSWORD = "user_password";
    private static final String KEY_USER_USERNAME = "user_username";
    private static final String KEY_LAST_USED_RESIDENT_ID = "last_used_resident_id";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final long SESSION_TIMEOUT = 30L * 24 * 60 * 60 * 1000; // 30 days

    /*
     * ---------------------------------------------------------------------------
     * "Remember me on this device"
     * ---------------------------------------------------------------------------
     * Unchecked means the session belongs to the running process only. The token
     * is then held in these static fields and NEVER written to disk, so closing
     * the app ends it and the resident signs in again next launch.
     *
     * Held in memory rather than deleted on a lifecycle callback because Android
     * gives no reliable "the process is about to die" signal: onStop runs long
     * before a kill and does not run at all if the system reclaims the process
     * without warning. Memory-only storage gets this right by construction,
     * because the value disappears exactly when and only when the process does.
     *
     * Static rather than per-instance because SessionManager is constructed
     * ad-hoc throughout the app (repositories, workers, activities) and every one
     * of them must agree about whether a session exists.
     */
    private static volatile String volatileAuthToken;
    private static volatile String volatileResidentId;

    private SharedPreferences sharedPreferences;
    private BiometricDao biometricDao;
    private ExecutorService executorService;
    private Handler mainHandler;

    public SessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        biometricDao = AppDatabase.getInstance(context).biometricDao();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public synchronized void saveAuthToken(String token) {
        sharedPreferences.edit()
                .putString(KEY_JWT_TOKEN, token)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .apply();
    }

    public synchronized void saveResidentId(String residentId) {
        sharedPreferences.edit()
                .putString(KEY_RESIDENT_ID, residentId)
                .putString(KEY_LAST_USED_RESIDENT_ID, residentId)
                .apply();
    }

    /**
     * Save the authenticated identity atomically for a successful login.
     *
     * <p>Honours the "remember me on this device" choice: when it is off the
     * identity is kept in memory only and nothing about the session is written
     * to disk, so it cannot outlive the process.
     */
    public synchronized void saveSession(String token, String residentId) {
        if (token == null || token.trim().isEmpty()
                || residentId == null || residentId.trim().isEmpty()) {
            clearSession();
            return;
        }
        clearVolatileSession();

        if (!isRememberMe()) {
            volatileAuthToken = token;
            volatileResidentId = residentId;
            // Any session left on disk from an earlier remembered login has to
            // go, or the next launch would silently resurrect it and defeat the
            // choice the resident just made.
            clearPersistedSession();
            return;
        }

        sharedPreferences.edit()
                .putString(KEY_JWT_TOKEN, token)
                .putString(KEY_RESIDENT_ID, residentId)
                .putString(KEY_LAST_USED_RESIDENT_ID, residentId)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .commit();
    }

    /**
     * Whether this device should keep a session between launches.
     *
     * <p>Defaults to true, so an existing install that predates the checkbox
     * keeps behaving exactly as it did before.
     */
    public boolean isRememberMe() {
        return sharedPreferences.getBoolean(KEY_REMEMBER_ME, true);
    }

    public void setRememberMe(boolean rememberMe) {
        sharedPreferences.edit().putBoolean(KEY_REMEMBER_ME, rememberMe).apply();
    }

    /** Drops an in-memory session, used when the resident signs out. */
    private synchronized void clearVolatileSession() {
        volatileAuthToken = null;
        volatileResidentId = null;
    }

    /**
     * Removes only the persisted session keys, leaving the device-level choices
     * (remember me, saved credentials, theme) intact.
     */
    private void clearPersistedSession() {
        sharedPreferences.edit()
                .remove(KEY_JWT_TOKEN)
                .remove(KEY_RESIDENT_ID)
                .remove(KEY_LAST_ACTIVE)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .commit();
    }

    public String fetchResidentId() {
        if (volatileResidentId != null) {
            return volatileResidentId;
        }
        return sharedPreferences.getString(KEY_RESIDENT_ID, null);
    }

    public void updateLastActive() {
        sharedPreferences.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply();
    }

    public boolean isSessionExpired() {
        if (!isLoggedIn()) return false;
        // A session that is only in memory cannot outlive the process, so it can
        // never have aged past the timeout. Returning false here also keeps
        // this from restarting the clock on every check.
        if (volatileAuthToken != null) return false;
        long lastActive = sharedPreferences.getLong(KEY_LAST_ACTIVE, 0);
        if (lastActive == 0) {
            updateLastActive();
            return false;
        }
        return (System.currentTimeMillis() - lastActive) > SESSION_TIMEOUT;
    }

    public String fetchAuthToken() {
        if (volatileAuthToken != null) {
            return volatileAuthToken;
        }
        return sharedPreferences.getString(KEY_JWT_TOKEN, null);
    }

    public boolean isLoggedIn() {
        String token = fetchAuthToken();
        String residentId = fetchResidentId();
        if (token == null || token.trim().isEmpty()
                || residentId == null || residentId.trim().isEmpty()) {
            return false;
        }
        // A memory-only session is authoritative on its own; there is no
        // persisted is_logged_in flag for it, because writing one would be the
        // very thing the resident declined to do.
        if (volatileAuthToken != null) {
            return true;
        }
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public synchronized void clearSession() {
        clearVolatileSession();
        sharedPreferences.edit()
                .remove(KEY_JWT_TOKEN)
                .remove(KEY_RESIDENT_ID)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_PASSWORD)
                .remove(KEY_USER_USERNAME)
                .remove(KEY_LAST_ACTIVE)
                .commit();
    }

    public String getLastUsedResidentId() {
        return sharedPreferences.getString(KEY_LAST_USED_RESIDENT_ID, "");
    }

    public void setBiometricEnabled(String residentId, boolean enabled) {
        if (residentId == null || residentId.isEmpty()) return;
        
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) {
                setting = new BiometricSetting(residentId);
            }
            setting.setEnabled(enabled);
            // Also enable both if general is enabled? Or just keep it as legacy
            biometricDao.saveSetting(setting);
        });
    }

    public void setFingerprintEnabled(String residentId, boolean enabled) {
        if (residentId == null || residentId.isEmpty()) return;
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) setting = new BiometricSetting(residentId);
            setting.setFingerprintEnabled(enabled);
            biometricDao.saveSetting(setting);
        });
    }

    public void setFaceIDEnabled(String residentId, boolean enabled) {
        if (residentId == null || residentId.isEmpty()) return;
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) setting = new BiometricSetting(residentId);
            setting.setFaceIDEnabled(enabled);
            biometricDao.saveSetting(setting);
        });
    }

    public void isBiometricEnabledAsync(String residentId, BiometricCheckCallback callback) {
        if (residentId == null || residentId.isEmpty()) {
            callback.onResult(false);
            return;
        }

        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            boolean enabled = setting != null && (setting.isEnabled() || setting.isFingerprintEnabled() || setting.isFaceIDEnabled());
            mainHandler.post(() -> callback.onResult(enabled));
        });
    }

    public void getBiometricSettingsAsync(String residentId, BiometricSettingsCallback callback) {
        if (residentId == null || residentId.isEmpty()) {
            callback.onResult(null);
            return;
        }
        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            mainHandler.post(() -> callback.onResult(setting));
        });
    }

    public interface BiometricSettingsCallback {
        void onResult(BiometricSetting setting);
    }

    public void hasAnyEnabledBiometricsAsync(BiometricCheckCallback callback) {
        executorService.execute(() -> {
            java.util.List<BiometricSetting> enabled = biometricDao.getAllEnabledSettings();
            boolean exists = enabled != null && !enabled.isEmpty();
            mainHandler.post(() -> callback.onResult(exists));
        });
    }

    public interface BiometricCheckCallback {
        void onResult(boolean enabled);
    }

    public void saveUserCredentials(String residentId, String email, String username, String password) {
        sharedPreferences.edit()
                .putString(KEY_RESIDENT_ID, residentId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_USERNAME, username)
                .putString(KEY_USER_PASSWORD, password)
                .putString(KEY_LAST_USED_RESIDENT_ID, residentId)
                .apply();

        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(residentId);
            if (setting == null) {
                setting = new BiometricSetting(residentId);
            }
            setting.setSavedEmail(email);
            setting.setSavedUsername(username);
            setting.setSavedPassword(password);
            biometricDao.saveSetting(setting);
        });
    }

    public String fetchUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, "");
    }

    public String fetchUserUsername() {
        return sharedPreferences.getString(KEY_USER_USERNAME, "");
    }

    public String fetchUserPassword() {
        return sharedPreferences.getString(KEY_USER_PASSWORD, "");
    }

    public void setThemeMode(int mode) {
        sharedPreferences.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public void setNotificationsEnabled(boolean enabled) {
        sharedPreferences.edit().putBoolean("notifications_enabled", enabled).apply();
    }

    public boolean areNotificationsEnabled() {
        return sharedPreferences.getBoolean("notifications_enabled", false);
    }

    public int getThemeMode() {
        // Default to -1 (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        return sharedPreferences.getInt(KEY_THEME_MODE, -1);
    }

    public void setLanguage(String language) {
        sharedPreferences.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public String getLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE, "en"); // Default to English
    }

    public void syncBiometricsFromDb(String residentId, Runnable onComplete) {
        final String targetId = (residentId != null && !residentId.isEmpty()) ? residentId : fetchResidentId();
        
        if (targetId == null || targetId.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        executorService.execute(() -> {
            BiometricSetting setting = biometricDao.getSettingByResidentId(targetId);
            if (setting != null) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                
                String currentId = fetchResidentId();
                // Sync to session if it's the same user OR if we don't have a user session yet (login/startup)
                if (currentId == null || currentId.isEmpty() || targetId.equalsIgnoreCase(currentId)) {
                    if (setting.getSavedEmail() != null) editor.putString(KEY_USER_EMAIL, setting.getSavedEmail());
                    if (setting.getSavedUsername() != null) editor.putString(KEY_USER_USERNAME, setting.getSavedUsername());
                    if (setting.getSavedPassword() != null) editor.putString(KEY_USER_PASSWORD, setting.getSavedPassword());
                }
                
                editor.putString(KEY_LAST_USED_RESIDENT_ID, targetId);
                editor.apply();
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void getAllEnabledBiometrics(BiometricListCallback callback) {
        executorService.execute(() -> {
            java.util.List<BiometricSetting> settings = biometricDao.getAllEnabledSettings();
            if (callback != null) {
                callback.onResult(settings);
            }
        });
    }

    public interface BiometricListCallback {
        void onResult(java.util.List<BiometricSetting> settings);
    }
}

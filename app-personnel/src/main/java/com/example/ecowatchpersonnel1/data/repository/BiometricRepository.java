package com.example.ecowatchpersonnel1.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.ecowatchpersonnel1.data.local.BiometricDao;
import com.example.ecowatchpersonnel1.data.local.BiometricSetting;
import com.example.ecowatchpersonnel1.data.local.PersonnelDatabase;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The biometric registrations saved on this device.
 *
 * <p>The database is the record; preferences are not consulted for this at all.
 * Two screens depend on it — the switch in Security &amp; Privacy and the
 * fingerprint button on the login screen — and the one place they both read from
 * is what stops them disagreeing about whether a fingerprint exists.
 *
 * <p>Every method is scoped to a personnel id passed in by the caller, and the
 * caller is always the session's own id. That is what keeps accounts from
 * overlapping on a shared phone: cleaner B's registration is a different row, and
 * no query here can return cleaner A's.
 */
public class BiometricRepository {

    /** Told when a write finished, on the main thread. */
    public interface SaveCallback {
        void onSaved();
    }

    /** Told the answer to a registration question, on the main thread. */
    public interface CheckCallback {
        void onChecked(boolean registered);
    }

    /** Told how many registrations exist, on the main thread. */
    public interface CountCallback {
        void onCounted(int count);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private final BiometricDao dao;

    public BiometricRepository(Context context) {
        this.dao = PersonnelDatabase.getInstance(context.getApplicationContext()).biometricDao();
    }

    /**
     * Live view of one cleaner's registration.
     *
     * <p>Returns null's natural empty case as an empty stream: a cleaner who has
     * never registered a fingerprint has no row at all, and an observer still has
     * to be able to read the screen.
     */
    public LiveData<BiometricSetting> observe(@Nullable String personnelId) {
        return dao.observeByPersonnelId(keyOf(personnelId));
    }

    /**
     * Every account on this device with a fingerprint registered.
     *
     * <p>Observed by the login screen. Empty until a cleaner registers one, which
     * is why the button is hidden by default rather than shown and then failing.
     */
    public LiveData<List<BiometricSetting>> observeRegisteredAccounts() {
        return dao.observeRegisteredAccounts();
    }

    /**
     * Whether this account currently has a fingerprint registered.
     *
     * <p>Asynchronous, and there is no synchronous variant on purpose. Room refuses
     * to query on the main thread, and every caller here is a lifecycle callback or
     * a click listener, so a blocking accessor would either crash the activity or
     * tempt someone into disabling that check. The answer arrives on the main
     * thread through {@link CheckCallback}, where callers can touch views.
     */
    public void isRegistered(@Nullable String personnelId, @NonNull CheckCallback callback) {
        final String key = keyOf(personnelId);
        if (key.isEmpty()) {
            // Nothing to look up before sign-in, and an empty key can never match
            // a real row anyway. Answered without touching the database.
            MAIN.post(() -> callback.onChecked(false));
            return;
        }
        IO_EXECUTOR.execute(() -> {
            BiometricSetting setting = dao.findByPersonnelId(key);
            boolean registered = setting != null && setting.isFingerprintRegistered();
            MAIN.post(() -> callback.onChecked(registered));
        });
    }

    /**
     * Whether any account on this device has a fingerprint registered.
     *
     * <p>The login screen's question: is there anything for the button to open?
     * Answered per row rather than as a bare count so the caller cannot tell one
     * registration from several and assume the wrong thing about which account it
     * belongs to.
     */
    public void hasAnyRegisteredAccount(@NonNull CheckCallback callback) {
        IO_EXECUTOR.execute(() -> {
            List<BiometricSetting> registered = dao.findRegisteredAccounts();
            boolean any = registered != null && !registered.isEmpty();
            MAIN.post(() -> callback.onChecked(any));
        });
    }

    /**
     * Records that a fingerprint was registered for this account.
     *
     * <p>Called only after the server has accepted the account password and the
     * sensor has confirmed the scan, so what lands in the database is a decision
     * the app has already proved it is allowed to make.
     *
     * @param username shown by the login screen; may be null for an account whose
     *                 name was not available at registration time.
     */
    public void register(@Nullable String personnelId, @Nullable String username,
                         @NonNull SaveCallback callback) {
        final String key = keyOf(personnelId);
        if (key.isEmpty()) {
            MAIN.post(callback::onSaved);
            return;
        }

        IO_EXECUTOR.execute(() -> {
            BiometricSetting existing = dao.findByPersonnelId(key);
            BiometricSetting row;
            if (existing != null) {
                // Re-registering must not reset the original registration time.
                row = existing;
                row.username = username != null && !username.trim().isEmpty()
                        ? username.trim()
                        : existing.username;
            } else {
                row = new BiometricSetting(key, username);
            }
            row.fingerprintEnabled = true;
            row.updatedAt = System.currentTimeMillis();
            dao.save(row);

            MAIN.post(callback::onSaved);
        });
    }

    /**
     * Turns the registration off without forgetting the account.
     *
     * <p>The row stays, so turning the lock back on does not need the account to be
     * rediscovered first, and so the login screen stops offering the button
     * immediately.
     */
    public void unregister(@Nullable String personnelId, @NonNull SaveCallback callback) {
        final String key = keyOf(personnelId);
        if (key.isEmpty()) {
            MAIN.post(callback::onSaved);
            return;
        }

        IO_EXECUTOR.execute(() -> {
            BiometricSetting existing = dao.findByPersonnelId(key);
            if (existing != null) {
                dao.setEnabled(key, false, System.currentTimeMillis());
            }
            MAIN.post(callback::onSaved);
        });
    }

    /** Forgets an account completely, used when its account is removed from the device. */
    public void forget(@Nullable String personnelId) {
        final String key = keyOf(personnelId);
        if (key.isEmpty()) return;
        IO_EXECUTOR.execute(() -> dao.delete(key));
    }

    /**
     * How many accounts on this device have a fingerprint.
     *
     * <p>Asynchronous, for the same reason {@link #isRegistered} is. Only used for
     * diagnostics.
     */
    public void countRegistered(@NonNull CountCallback callback) {
        IO_EXECUTOR.execute(() -> {
            int total = dao.countRegistered();
            MAIN.post(() -> callback.onCounted(total));
        });
    }

    /**
     * The personnel id used as a database key.
     *
     * <p>Blank rather than null when there is no session: the column is NOT NULL,
     * and an empty key can never match a real row, so a lookup before sign-in
     * returns nothing instead of another account's.
     */
    private static String keyOf(@Nullable String personnelId) {
        if (personnelId == null) return "";
        String trimmed = personnelId.trim();
        return trimmed.isEmpty() ? "" : trimmed.toUpperCase();
    }

    }
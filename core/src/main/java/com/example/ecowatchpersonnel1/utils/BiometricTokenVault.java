package com.example.ecowatchpersonnel1.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Holds each signed-in cleaner's token so a fingerprint can restore the session
 * after a sign-out.
 *
 * <p>This exists because signing out removes the token, and a fingerprint that
 * can only reopen an already-open session is no use to someone who just signed
 * out. Rather than store the password — which is what the resident app does, and
 * what this app has deliberately avoided — the token is sealed with a key in the
 * Android keystore that is <b>user-authentication required</b>: the key cannot
 * perform any operation until a biometric has been verified, so the ciphertext in
 * preferences is inert without the account holder's fingerprint.
 *
 * <p>Entries are stored <b>per personnel id</b>. A shared field phone can have
 * more than one account with a fingerprint registered, and sealing account B must
 * not overwrite the token that lets account A sign back in. The keystore key is
 * shared by all entries because adding a new fingerprint invalidates it for the
 * whole device by design; at that point every sealed token is dead together.
 *
 * <p>Consequences worth knowing:
 * <ul>
 *   <li>Enrolling a new fingerprint invalidates the key, by design. The vault then
 *       fails to open and the affected rows are dropped, so the cleaner signs in
 *       with their password once and re-registers. The alternative would let
 *       anyone who can add a finger to the phone inherit a session.</li>
 *   <li>The vault is not cleared on sign-out. That is the point: it is what the
 *       fingerprint restores. It is cleared for an account when that cleaner
 *       turns the setting off.</li>
 *   <li>Nothing readable is stored. The personnel id and username alongside the
 *       ciphertext are identifiers, not credentials.</li>
 * </ul>
 */
public final class BiometricTokenVault {

    private static final String PREFS = "ecowatch_biometric_vault";

    /** Index of account keys that currently have an entry. */
    private static final String KEY_ACCOUNTS = "accounts";

    /** Per-account entry keys, prefixed so they can never collide with the index. */
    private static final String ENTRY_PREFIX = "entry:";
    private static final String SUFFIX_CIPHERTEXT = ":ciphertext";
    private static final String SUFFIX_IV = ":iv";
    private static final String SUFFIX_PERSONNEL_ID = ":personnel_id";
    private static final String SUFFIX_USERNAME = ":username";

    /**
     * Keys written by the old single-account vault. Read once and migrated so an
     * update does not silently cost an existing cleaner their fingerprint login.
     */
    private static final String LEGACY_CIPHERTEXT = "token_ciphertext";
    private static final String LEGACY_IV = "token_iv";
    private static final String LEGACY_PERSONNEL_ID = "personnel_id";
    private static final String LEGACY_USERNAME = "username";

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "ecowatch_personnel_biometric_token";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private BiometricTokenVault() {
    }

    /**
     * True when a sealed token is stored for an account.
     *
     * <p>Preferences only, so this is safe to call from the main thread. It answers
     * "is there something a fingerprint could open", not "would opening it work":
     * the keystore key may since have been invalidated, which only shows up when
     * the cipher is actually built.
     *
     * @param personnelId the account to check, or null/blank to ask whether
     *                    <b>any</b> account has a sealed token
     */
    public static boolean hasStoredSession(@NonNull Context context,
                                           @Nullable String personnelId) {
        SharedPreferences prefs = prefsOf(context);
        migrateLegacy(prefs);

        if (isBlank(personnelId)) {
            for (String account : accounts(prefs)) {
                if (hasEntry(prefs, account)) return true;
            }
            return false;
        }

        return hasEntry(prefs, normalize(personnelId));
    }

    /**
     * Every account with a sealed token, most recently stored last.
     *
     * <p>Used when a caller needs to know which account a vault belongs to without
     * having been handed the row. The ids are the original, un-normalized values
     * written at enrollment.
     */
    @NonNull
    public static List<String> storedPersonnelIds(@NonNull Context context) {
        SharedPreferences prefs = prefsOf(context);
        migrateLegacy(prefs);

        List<String> ids = new ArrayList<>();
        for (String account : accounts(prefs)) {
            if (!hasEntry(prefs, account)) continue;
            String id = prefs.getString(personnelIdKey(account), null);
            ids.add(isBlank(id) ? account : id.trim());
        }
        return ids;
    }

    /** The username to greet the cleaner with, for the login screen's prompt. */
    @Nullable
    public static String storedUsername(@NonNull Context context,
                                        @Nullable String personnelId) {
        SharedPreferences prefs = prefsOf(context);
        migrateLegacy(prefs);

        if (isBlank(personnelId)) return null;
        String username = prefs.getString(usernameKey(normalize(personnelId)), null);
        return isBlank(username) ? null : username.trim();
    }

    /**
     * A cipher that seals the token, for use as the enrollment scan's
     * {@code CryptoObject}.
     *
     * <p>Must be passed to {@link androidx.biometric.BiometricPrompt}; the key is
     * user-authentication required, so it cannot encrypt anything until a scan has
     * been verified. Sealing therefore happens inside the success callback.
     *
     * <p>If the key was invalidated by a new fingerprint enrollment, every sealed
     * token on the device is already unrecoverable. The vault is cleared and a
     * fresh key is made, so re-enrolling does not stay stuck on a dead key.
     *
     * @return null when the keystore is unavailable on this device, in which case
     *         the app falls back to a plain, session-only fingerprint check.
     */
    @Nullable
    public static Cipher createSealingCipher(@NonNull Context context) {
        try {
            return newSealingCipher();
        } catch (KeyPermanentlyInvalidatedException invalidated) {
            clear(context);
            try {
                return newSealingCipher();
            } catch (GeneralSecurityException | java.io.IOException | RuntimeException error) {
                return null;
            }
        } catch (GeneralSecurityException | java.io.IOException | RuntimeException error) {
            return null;
        }
    }

    private static Cipher newSealingCipher()
            throws GeneralSecurityException, java.io.IOException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey(true));
        return cipher;
    }

    /** Seals the token with the cipher from {@link #createSealingCipher(Context)}. */
    public static void seal(@NonNull Context context, @NonNull Cipher cipher,
                            @NonNull String personnelId, @Nullable String username,
                            @NonNull String token) {
        String account = normalize(personnelId);
        if (account.isEmpty()) return;

        SharedPreferences prefs = prefsOf(context);
        migrateLegacy(prefs);

        try {
            byte[] plaintext = token.getBytes(StandardCharsets.UTF_8);
            byte[] sealed = cipher.doFinal(plaintext);

            // Re-read the index immediately before writing so sealing one account
            // cannot drop the entries that are already there for another.
            Set<String> accounts = accounts(prefs);
            accounts.add(account);

            prefs.edit()
                    .putString(ciphertextKey(account),
                            Base64.encodeToString(sealed, Base64.NO_WRAP))
                    .putString(ivKey(account),
                            Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(personnelIdKey(account), personnelId.trim())
                    .putString(usernameKey(account), username == null ? "" : username.trim())
                    .putStringSet(KEY_ACCOUNTS, accounts)
                    .apply();
        } catch (GeneralSecurityException | RuntimeException error) {
            // A token that cannot be sealed simply is not stored for this account;
            // the cleaner signs in with their password next time. Nothing was
            // written, so any entry that was already there is left untouched.
        }
    }

    /**
     * A cipher that opens the sealed token, for the login scan's
     * {@code CryptoObject}.
     *
     * @return null when nothing is sealed for this account, or when the key has
     *         been invalidated by a new fingerprint being enrolled. A null here is
     *         the signal to fall back to the password form.
     */
    @Nullable
    public static Cipher createOpeningCipher(@NonNull Context context,
                                             @Nullable String personnelId) {
        if (isBlank(personnelId)) return null;

        SharedPreferences prefs = prefsOf(context);
        migrateLegacy(prefs);

        String account = normalize(personnelId);
        String encoded = prefs.getString(ciphertextKey(account), null);
        String encodedIv = prefs.getString(ivKey(account), null);
        if (encoded == null || encoded.isEmpty() || encodedIv == null) return null;

        try {
            byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(false),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher;
        } catch (KeyPermanentlyInvalidatedException invalidated) {
            // A new fingerprint was enrolled: every account's sealed token is dead,
            // not just this one. Drop them together and remove the dead key so the
            // next enrollment can create a working one.
            clear(context);
            return null;
        } catch (GeneralSecurityException | java.io.IOException | RuntimeException error) {
            return null;
        }
    }

    /**
     * Opens the token with the cipher from {@link #createOpeningCipher}.
     *
     * <p>Called only after a successful scan, which is what authorises the key.
     */
    @Nullable
    public static String open(@NonNull Context context, @NonNull Cipher cipher,
                              @NonNull String personnelId) {
        String account = normalize(personnelId);
        String encoded = prefsOf(context).getString(ciphertextKey(account), null);
        if (encoded == null) return null;

        try {
            byte[] sealed = Base64.decode(encoded, Base64.NO_WRAP);
            byte[] plaintext = cipher.doFinal(sealed);
            String token = new String(plaintext, StandardCharsets.UTF_8);
            return token.trim().isEmpty() ? null : token;
        } catch (GeneralSecurityException | RuntimeException error) {
            return null;
        }
    }

    /**
     * Forgets every sealed token and destroys the keystore key.
     *
     * <p>Used when the whole vault is being reset. Turning one account's setting
     * off must use {@link #clear(Context, String)} instead, so the other accounts
     * on a shared phone keep working.
     */
    public static void clear(@NonNull Context context) {
        prefsOf(context).edit().clear().apply();
        deleteKey();
    }

    /**
     * Forgets one account's sealed token.
     *
     * <p>The keystore key is deleted only when this was the last account. Deleting
     * it while another account still has ciphertext would make that account's
     * fingerprint login fail for no reason.
     */
    public static void clear(@NonNull Context context, @Nullable String personnelId) {
        if (isBlank(personnelId)) return;

        SharedPreferences prefs = prefsOf(context);
        migrateLegacy(prefs);
        removeEntry(prefs, normalize(personnelId), true);
    }

    /**
     * Drops every vault entry that can no longer be opened, without leaving a dead
     * key behind. Used when the keystore rejects the key as invalidated.
     */
    public static void discardUnopenable(@NonNull Context context) {
        clear(context);
    }

    /**
     * Drops one account's vault entry, without touching the shared key.
     *
     * <p>Used when this account's ciphertext will not open. Other accounts are
     * left alone: their ciphertext may still be perfectly good.
     */
    public static void discardUnopenable(@NonNull Context context,
                                         @Nullable String personnelId) {
        if (isBlank(personnelId)) return;

        SharedPreferences prefs = prefsOf(context);
        migrateLegacy(prefs);
        removeEntry(prefs, normalize(personnelId), false);
    }

    // ------------------------------------------------------------- keystore ---

    /**
     * The sealing key, created on first use.
     *
     * @param create when false, an existing key is required and a missing one is
     *               reported as failure rather than silently replaced. Creating a
     *               fresh key while stale ciphertext is still stored would produce
     *               a key that can never open it.
     */
    @Nullable
    private static SecretKey obtainKey(boolean create)
            throws GeneralSecurityException, java.io.IOException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);

        KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        }
        if (!create) return null;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // The whole point: no operation without a verified biometric.
                .setUserAuthenticationRequired(true)
                // A new fingerprint must not inherit the sealed token.
                .setInvalidatedByBiometricEnrollment(true);

        generator.init(applyUserAuthenticationPolicy(builder).build());
        return generator.generateKey();
    }

    private static void deleteKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (GeneralSecurityException | java.io.IOException | RuntimeException ignored) {
            // Best effort. The ciphertext is cleared regardless, and a key with no
            // ciphertext behind it cannot open anything.
        }
    }

    /**
     * Per-use authentication, spelled the way the running API expects it.
     *
     * <p>API 30 replaced the validity-seconds call with an explicit
     * authenticator list. Zero seconds means every single use needs a fresh scan.
     */
    private static KeyGenParameterSpec.Builder applyUserAuthenticationPolicy(
            KeyGenParameterSpec.Builder builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return builder.setUserAuthenticationParameters(
                    0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        }
        return builder.setUserAuthenticationValidityDurationSeconds(-1);
    }

    // -------------------------------------------------------- account entries ---

    /**
     * The one spelling used for every preference key.
     *
     * <p>Matches {@code BiometricRepository}'s database key so a lookup after
     * login uses the same account the row was written for.
     */
    @NonNull
    private static String normalize(@Nullable String personnelId) {
        if (personnelId == null) return "";
        String trimmed = personnelId.trim();
        return trimmed.isEmpty() ? "" : trimmed.toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean hasEntry(@NonNull SharedPreferences prefs, @NonNull String account) {
        String ciphertext = prefs.getString(ciphertextKey(account), null);
        String iv = prefs.getString(ivKey(account), null);
        return ciphertext != null && !ciphertext.isEmpty() && iv != null && !iv.isEmpty();
    }

    @NonNull
    private static Set<String> accounts(@NonNull SharedPreferences prefs) {
        Set<String> stored = prefs.getStringSet(KEY_ACCOUNTS, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    @NonNull
    private static String ciphertextKey(@NonNull String account) {
        return ENTRY_PREFIX + account + SUFFIX_CIPHERTEXT;
    }

    @NonNull
    private static String ivKey(@NonNull String account) {
        return ENTRY_PREFIX + account + SUFFIX_IV;
    }

    @NonNull
    private static String personnelIdKey(@NonNull String account) {
        return ENTRY_PREFIX + account + SUFFIX_PERSONNEL_ID;
    }

    @NonNull
    private static String usernameKey(@NonNull String account) {
        return ENTRY_PREFIX + account + SUFFIX_USERNAME;
    }

    /**
     * Removes one account's entry and, when asked, the key if nothing is left.
     *
     * @param deleteKeyWhenEmpty true for a deliberate "turn it off", false when
     *                           merely dropping unreadable ciphertext
     */
    private static void removeEntry(@NonNull SharedPreferences prefs,
                                    @NonNull String account,
                                    boolean deleteKeyWhenEmpty) {
        Set<String> accounts = accounts(prefs);
        accounts.remove(account);

        SharedPreferences.Editor editor = prefs.edit()
                .remove(ciphertextKey(account))
                .remove(ivKey(account))
                .remove(personnelIdKey(account))
                .remove(usernameKey(account));

        boolean empty = accounts.isEmpty();
        if (empty) {
            // Also clears any partial legacy keys that were never migrated.
            editor.clear();
        } else {
            editor.putStringSet(KEY_ACCOUNTS, accounts);
        }
        editor.apply();

        if (empty && deleteKeyWhenEmpty) {
            deleteKey();
        }
    }

    /**
     * Moves the old single-account entry into the per-account layout.
     *
     * <p>Called lazily on every read. Doing it here rather than in an activity
     * means an update works even if the cleaner never opens the login screen
     * before the next fingerprint attempt.
     */
    private static void migrateLegacy(@NonNull SharedPreferences prefs) {
        String legacyId = prefs.getString(LEGACY_PERSONNEL_ID, null);
        String legacyCiphertext = prefs.getString(LEGACY_CIPHERTEXT, null);
        String legacyIv = prefs.getString(LEGACY_IV, null);

        if (legacyId == null && legacyCiphertext == null && legacyIv == null) {
            return;
        }

        String account = normalize(legacyId);
        SharedPreferences.Editor editor = prefs.edit();

        if (!account.isEmpty() && legacyCiphertext != null && !legacyCiphertext.isEmpty()
                && legacyIv != null && !legacyIv.isEmpty()
                && !prefs.contains(ciphertextKey(account))) {
            Set<String> accounts = accounts(prefs);
            accounts.add(account);
            editor.putString(ciphertextKey(account), legacyCiphertext)
                    .putString(ivKey(account), legacyIv)
                    .putString(personnelIdKey(account), legacyId.trim())
                    .putString(usernameKey(account),
                            prefs.getString(LEGACY_USERNAME, ""))
                    .putStringSet(KEY_ACCOUNTS, accounts);
        }

        editor.remove(LEGACY_CIPHERTEXT)
                .remove(LEGACY_IV)
                .remove(LEGACY_PERSONNEL_ID)
                .remove(LEGACY_USERNAME)
                .apply();
    }

    /**
     * Shared preferences for the sealed tokens.
     *
     * <p>Always the application context: this is read from lifecycle callbacks and
     * click handlers alike, and holding an activity here would leak it.
     */
    private static SharedPreferences prefsOf(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

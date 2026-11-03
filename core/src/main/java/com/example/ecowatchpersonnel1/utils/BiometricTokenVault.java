package com.example.ecowatchpersonnel1.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Holds the signed-in cleaner's token so a fingerprint can restore the session
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
 * <p>Consequences worth knowing:
 * <ul>
 *   <li>Enrolling a new fingerprint invalidates the key, by design. The vault then
 *       fails to open and the row is dropped, so the cleaner signs in with their
 *       password once and re-registers. The alternative would let anyone who can
 *       add a finger to the phone inherit the session.</li>
 *   <li>The vault is not cleared on sign-out. That is the point: it is what the
 *       fingerprint restores. It is cleared when the cleaner turns the setting
 *       off.</li>
 *   <li>Nothing readable is stored. The personnel id and username alongside the
 *       ciphertext are identifiers, not credentials.</li>
 * </ul>
 */
public final class BiometricTokenVault {

    private static final String PREFS = "ecowatch_biometric_vault";
    private static final String KEY_CIPHERTEXT = "token_ciphertext";
    private static final String KEY_IV = "token_iv";
    private static final String KEY_PERSONNEL_ID = "personnel_id";
    private static final String KEY_USERNAME = "username";

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
     */
    public static boolean hasStoredSession(@NonNull Context context,
                                           @Nullable String personnelId) {
        SharedPreferences prefs = prefsOf(context);
        String owner = prefs.getString(KEY_PERSONNEL_ID, null);
        String sealed = prefs.getString(KEY_CIPHERTEXT, null);

        if (owner == null || owner.trim().isEmpty()
                || sealed == null || sealed.isEmpty()) {
            return false;
        }
        // With no account named, any stored vault counts: the login screen offers
        // the one account it holds.
        if (personnelId == null || personnelId.trim().isEmpty()) return true;

        return owner.trim().equalsIgnoreCase(personnelId.trim());
    }

    /** The account the vault belongs to, or null when nothing is sealed. */
    @Nullable
    public static String storedPersonnelId(@NonNull Context context) {
        return prefsOf(context).getString(KEY_PERSONNEL_ID, null);
    }

    /** The username to greet the cleaner with, for the login screen's prompt. */
    @Nullable
    public static String storedUsername(@NonNull Context context) {
        return prefsOf(context).getString(KEY_USERNAME, null);
    }

    /**
     * A cipher that seals the token, for use as the enrollment scan's
     * {@code CryptoObject}.
     *
     * <p>Must be passed to {@link androidx.biometric.BiometricPrompt}; the key is
     * user-authentication required, so it cannot encrypt anything until a scan has
     * been verified. Sealing therefore happens inside the success callback.
     *
     * @return null when the keystore is unavailable on this device, in which case
     *         the app falls back to a plain, session-only fingerprint check.
     */
    @Nullable
    public static Cipher createSealingCipher() {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, obtainKey(true));
            return cipher;
        } catch (GeneralSecurityException | java.io.IOException | RuntimeException error) {
            return null;
        }
    }

    /** Seals the token with the cipher from {@link #createSealingCipher()}. */
    public static void seal(@NonNull Context context, @NonNull Cipher cipher,
                            @NonNull String personnelId, @Nullable String username,
                            @NonNull String token) {
        try {
            byte[] plaintext = token.getBytes(StandardCharsets.UTF_8);
            byte[] sealed = cipher.doFinal(plaintext);

            prefsOf(context).edit()
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(sealed, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(KEY_PERSONNEL_ID, personnelId.trim())
                    .putString(KEY_USERNAME, username == null ? "" : username.trim())
                    .apply();
        } catch (GeneralSecurityException | RuntimeException error) {
            // A token that cannot be sealed simply is not stored; the cleaner
            // signs in with their password next time. Not fatal.
            clear(context);
        }
    }

    /**
     * A cipher that opens the sealed token, for the login scan's
     * {@code CryptoObject}.
     *
     * @return null when nothing is sealed, or when the key has been invalidated by
     *         a new fingerprint being enrolled. A null here is the signal to fall
     *         back to the password form.
     */
    @Nullable
    public static Cipher createOpeningCipher(@NonNull Context context) {
        SharedPreferences prefs = prefsOf(context);
        String encoded = prefs.getString(KEY_CIPHERTEXT, null);
        String encodedIv = prefs.getString(KEY_IV, null);
        if (encoded == null || encoded.isEmpty() || encodedIv == null) return null;

        try {
            byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(false), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher;
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
    public static String open(@NonNull Context context, @NonNull Cipher cipher) {
        String encoded = prefsOf(context).getString(KEY_CIPHERTEXT, null);
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
     * Forgets the sealed token and destroys the keystore key.
     *
     * <p>Called when the cleaner turns the setting off, so turning it back on
     * needs a fresh password rather than reusing a key that outlived the decision
     * to disable it.
     */
    public static void clear(@NonNull Context context) {
        prefsOf(context).edit().clear().apply();
        deleteKey();
    }

    /**
     * Drops a vault that can no longer be opened, without touching the key.
     *
     * <p>Used when the key has been invalidated: the ciphertext is unrecoverable
     * either way, so keeping it would only make the login screen offer a button
     * that can never succeed.
     */
    public static void discardUnopenable(@NonNull Context context) {
        prefsOf(context).edit()
                .remove(KEY_CIPHERTEXT)
                .remove(KEY_IV)
                .remove(KEY_PERSONNEL_ID)
                .remove(KEY_USERNAME)
                .apply();
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

    /**
     * Shared preferences for the sealed token.
     *
     * <p>Always the application context: this is read from lifecycle callbacks and
     * click handlers alike, and holding an activity here would leak it.
     */
    private static SharedPreferences prefsOf(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
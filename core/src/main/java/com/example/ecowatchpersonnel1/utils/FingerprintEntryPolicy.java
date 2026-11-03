package com.example.ecowatchpersonnel1.utils;

/**
 * When the login screen may offer fingerprint entry.
 *
 * <p>Extracted because the rule was wrong once already: the button was gated on a
 * live session, which meant signing out hid it, so a cleaner who had just signed
 * out was shown nothing to reach for. The rule is now that a fingerprint can offer
 * <b>either</b> a session to reopen <b>or</b> a sealed token to unlock into one.
 *
 * <p>Pure and static so it can be pinned down without a device.
 */
public final class FingerprintEntryPolicy {

    private FingerprintEntryPolicy() {
    }

    /**
     * Whether the fingerprint button should be visible.
     *
     * @param registrationExists the account registered a fingerprint on this device
     * @param sessionAlive      a session token is on disk right now
     * @param sealedTokenExists the vault holds a token a scan can unlock
     */
    public static boolean shouldShowButton(boolean registrationExists,
                                           boolean sessionAlive,
                                           boolean sealedTokenExists) {
        // The registration is the source of truth for "this device has a
        // fingerprint for this account", and the dashboard's lock is keyed off the
        // same row. Offering entry without it would hand back a session that is
        // then not protected, which is worse than offering nothing.
        if (!registrationExists) return false;

        // Either half is enough on its own: a session to reopen, or a sealed token
        // to unlock into one. Requiring the session here is what hid the button
        // from a cleaner who had just signed out.
        return sessionAlive || sealedTokenExists;
    }

    /**
     * Whether tapping the button needs the vault rather than a plain scan.
     *
     * <p>True only after a sign-out. With a session open there is nothing to
     * unseal, and skipping the keystore keeps the common unlock off it.
     */
    public static boolean needsVaultUnlock(boolean sessionAlive, boolean sealedTokenExists) {
        return !sessionAlive && sealedTokenExists;
    }
}
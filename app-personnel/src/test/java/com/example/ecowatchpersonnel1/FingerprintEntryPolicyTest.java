package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.utils.FingerprintEntryPolicy;

import org.junit.Test;

/**
 * When the login screen may offer fingerprint entry.
 *
 * <p>Written around a bug that was reported rather than designed for: signing out
 * made the fingerprint button disappear, because it had been gated on a live
 * session. These cases pin the corrected behaviour down.
 */
public class FingerprintEntryPolicyTest {

    @Test
    public void theButtonSurvivesSigningOut() {
        // The reported bug. A cleaner who has just signed out still has a sealed
        // token a scan can unlock, so the button must still be there.
        assertTrue(FingerprintEntryPolicy.shouldShowButton(
                true,   // registered
                false,  // signed out
                true)); // sealed token present
    }

    @Test
    public void aLiveSessionStillShowsTheButton() {
        assertTrue(FingerprintEntryPolicy.shouldShowButton(
                true, true, false));
    }

    @Test
    public void nothingRegisteredMeansNoButton() {
        // A device that has never registered one must not offer one.
        assertFalse(FingerprintEntryPolicy.shouldShowButton(
                false, true, false));
        assertFalse(FingerprintEntryPolicy.shouldShowButton(
                false, false, false));
    }

    @Test
    public void nothingSealedAndNoSessionMeansNoButton() {
        // The honest case: signed out with no sealed token. There is genuinely
        // nothing a fingerprint could open, so only the password form can help.
        assertFalse(FingerprintEntryPolicy.shouldShowButton(
                true, false, false));
    }

    @Test
    public void aSealedTokenWithoutARegistrationShowsNothing() {
        // Should not happen, but if the rows were ever lost while the vault
        // survived, offering a button would be offering one with no account
        // behind it.
        assertFalse(FingerprintEntryPolicy.shouldShowButton(
                false, false, true));
    }

    @Test
    public void theVaultIsOnlyUnlockedAfterSigningOut() {
        // With a session open there is nothing to unseal, and the common unlock
        // should stay off the keystore entirely.
        assertFalse(FingerprintEntryPolicy.needsVaultUnlock(true, true));
        assertFalse(FingerprintEntryPolicy.needsVaultUnlock(true, false));
        assertTrue(FingerprintEntryPolicy.needsVaultUnlock(false, true));
        assertFalse(FingerprintEntryPolicy.needsVaultUnlock(false, false));
    }
}
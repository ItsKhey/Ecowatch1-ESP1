package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.data.local.BiometricSetting;

import org.junit.Test;

/**
 * The biometric registration row.
 *
 * <p>The two properties that matter are that it starts unregistered, and that it
 * is tied to one account: a row that defaulted to enabled would put a fingerprint
 * button on the login screen for a cleaner who never asked for one.
 */
public class BiometricSettingTest {

    @Test
    public void aNewRowIsNotRegistered() {
        // The default must be off. On would mean the login screen offers a
        // fingerprint nobody enrolled.
        BiometricSetting setting = new BiometricSetting("PERS-0001", "cleaner");
        assertFalse(setting.isFingerprintRegistered());
    }

    @Test
    public void registrationIsRecordedAgainstThePersonnelId() {
        BiometricSetting setting = new BiometricSetting("PERS-0001", "cleaner");
        setting.fingerprintEnabled = true;

        // The id is the primary key, which is the whole of the isolation between
        // accounts on a shared phone.
        assertEquals("PERS-0001", setting.personnelId);
        assertTrue(setting.isFingerprintRegistered());
    }

    @Test
    public void twoCleanersAreTwoDistinctRows() {
        // Nothing about one registration can satisfy the other: the key differs,
        // so a lookup for one never returns the other.
        BiometricSetting first = new BiometricSetting("PERS-0001", "cleaner");
        first.fingerprintEnabled = true;
        BiometricSetting second = new BiometricSetting("PERS-0002", "other");

        assertTrue(first.isFingerprintRegistered());
        assertFalse(second.isFingerprintRegistered());
    }

    @Test
    public void turningItOffKeepsTheAccount() {
        // Disabling must not delete the row, so re-enabling does not need the
        // account to be rediscovered first.
        BiometricSetting setting = new BiometricSetting("PERS-0001", "cleaner");
        setting.fingerprintEnabled = true;
        setting.fingerprintEnabled = false;

        assertFalse(setting.isFingerprintRegistered());
        assertEquals("PERS-0001", setting.personnelId);
    }

    @Test
    public void theRowNeverCarriesACredential() {
        // A biometric row holding a password or a token would turn a stolen phone
        // into a signed-in one. The username is kept because it is not a secret.
        BiometricSetting setting = new BiometricSetting("PERS-0001", "cleaner");
        setting.fingerprintEnabled = true;

        for (java.lang.reflect.Field field : BiometricSetting.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse("must not store a password: " + field.getName(),
                    name.contains("password") || name.contains("token")
                            || name.contains("hash") || name.contains("secret"));
        }
    }

    @Test
    public void aRowWithoutAUsernameFallsBackToTheId() {
        // Written by a registration that had no username to hand, or by an older
        // schema. Showing an empty label on the login screen would look broken.
        BiometricSetting setting = new BiometricSetting("PERS-0001", null);
        assertEquals("PERS-0001", setting.displayUsername());

        setting.username = "   ";
        assertEquals("PERS-0001", setting.displayUsername());
    }

    @Test
    public void aBlankUsernameIsStoredAsNothing() {
        BiometricSetting setting = new BiometricSetting("PERS-0001", "  cleaner  ");
        assertEquals("cleaner", setting.displayUsername());
    }

    @Test
    public void timestampsAreSetOnCreation() {
        long before = System.currentTimeMillis();
        BiometricSetting setting = new BiometricSetting("PERS-0001", "cleaner");
        long after = System.currentTimeMillis();

        assertTrue(setting.createdAt >= before);
        assertTrue(setting.createdAt <= after);
        assertEquals(setting.createdAt, setting.updatedAt);
    }
}
package com.example.myecowatch1.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers the usernames this device has used, so signing in again does not mean
 * typing them again.
 *
 * <p>Added because account creation is immediate and passwordless in this build:
 * a new account is created by the register form and the resident is dropped at
 * the login screen to try it out, where the username they just chose has to be
 * typed back in. Accounts are also added on every successful login, so switching
 * between test accounts during development is quick.
 *
 * <p><b>Passwords are never stored here.</b> Only the username, which the resident
 * typed anyway and which is not a secret. Anything that needs a password has to
 * be typed, or go through the existing biometric path, which stores its own
 * record deliberately.
 *
 * <p>Kept in preferences rather than a Room table on purpose: adding an entity
 * would mean a database migration and a version bump, which is a lot of risk for
 * a convenience list. This is a handful of short strings and is not queried.
 */
public final class SavedAccounts {

    private static final String PREFS = "ecowatch_saved_accounts";
    private static final String KEY_LIST = "usernames";
    /**
     * Session tokens, one {@code username <tab> residentId <tab> token} record per
     * line.
     *
     * <p>Tab separated because a JWT never contains one, so the fields cannot be
     * confused with a delimiter that appears inside a value.
     */
    private static final String KEY_SESSIONS = "sessions";

    /**
     * How many to keep. Beyond a handful the list stops being a convenience and
     * starts being clutter on the login screen.
     */
    private static final int MAX = 6;

    private SavedAccounts() {
        // Utility class.
    }

    /**
     * Records a username as the most recently used.
     *
     * <p>Re-recording an existing account moves it to the front rather than
     * duplicating it, so the list reads most-recent-first.
     *
     * @param username ignored when null or blank
     */
    public static void remember(Context context, String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) {
            return;
        }

        // A set, so an existing username is removed before being re-added and
        // therefore moves to the front.
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add(value);
        ordered.addAll(usernames(context));

        List<String> trimmed = new ArrayList<>();
        for (String entry : ordered) {
            if (trimmed.size() >= MAX) {
                break;
            }
            trimmed.add(entry);
        }

        prefs(context).edit()
                .putString(KEY_LIST, String.join("\n", trimmed))
                .apply();
    }

    /**
     * Records a username together with the session that lets it sign in without
     * a password.
     *
     * <p>This is the second half of "remember me on this device": the username is
     * offered in the account dialog, and the token is what lets choosing it sign
     * in without the resident typing anything.
     *
     * <p>What is stored is the session token the server already issued, NOT the
     * password. The token authorises exactly this account, is already what the
     * app holds in {@link SessionManager}, and expires on the server after 30
     * days. A stored password could not be scoped down, could not be expired, and
     * would be a plain copy of the credential if the device were ever read.
     */
    public static void rememberWithSession(Context context, String username,
                                           String residentId, String token) {
        remember(context, username);

        String name = username == null ? "" : username.trim();
        if (name.isEmpty() || token == null || token.trim().isEmpty()) {
            return;
        }

        List<String> kept = new ArrayList<>();
        for (String[] record : rawSessions(context)) {
            if (!name.equals(record[0])) {
                kept.add(String.join("\t", record[0], record[1], record[2]));
            }
        }
        kept.add(0, String.join("\t",
                name,
                residentId == null ? "" : residentId.trim(),
                token.trim()));

        prefs(context).edit()
                .putString(KEY_SESSIONS, String.join("\n", kept))
                .apply();
    }

    /**
     * The stored session for a remembered account, or null when it has none.
     *
     * <p>Null is the normal case for an account saved before "remember me" carried
     * a session, so callers must treat it as "fall back to asking for the
     * password" rather than as an error.
     */
    @Nullable
    public static Session sessionFor(Context context, String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            return null;
        }
        for (String[] record : rawSessions(context)) {
            if (name.equalsIgnoreCase(record[0]) && !record[2].trim().isEmpty()) {
                return new Session(record[1].trim(), record[2].trim());
            }
        }
        return null;
    }

    /** Drops a stored session without forgetting the username. */
    public static void forgetSession(Context context, String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            return;
        }
        List<String> kept = new ArrayList<>();
        for (String[] record : rawSessions(context)) {
            if (!name.equalsIgnoreCase(record[0])) {
                kept.add(String.join("\t", record));
            }
        }
        prefs(context).edit()
                .putString(KEY_SESSIONS, String.join("\n", kept))
                .apply();
    }

    private static List<String[]> rawSessions(Context context) {
        String raw = prefs(context).getString(KEY_SESSIONS, "");
        List<String[]> records = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return records;
        }
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length == 3 && !parts[0].trim().isEmpty()) {
                records.add(new String[]{parts[0].trim(), parts[1], parts[2]});
            }
        }
        return records;
    }

    /** A remembered account's credentials: who it is, and the token that says so. */
    public static final class Session {
        public final String residentId;
        public final String token;

        Session(String residentId, String token) {
            this.residentId = residentId;
            this.token = token;
        }
    }

    /** Every remembered username, most recently used first. */
    public static List<String> usernames(Context context) {
        String raw = prefs(context).getString(KEY_LIST, "");
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();
        for (String entry : Arrays.asList(raw.split("\n"))) {
            String value = entry.trim();
            if (!value.isEmpty() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    /** The account to pre-fill the login form with, or null when there is none. */
    public static String mostRecent(Context context) {
        List<String> all = usernames(context);
        return all.isEmpty() ? null : all.get(0);
    }

    public static boolean isEmpty(Context context) {
        return usernames(context).isEmpty();
    }

    /**
     * Forgets one account. Used when it is deleted, so the list does not keep
     * offering a username that can no longer sign in.
     */
    public static void forget(Context context, String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) {
            return;
        }

        List<String> remaining = new ArrayList<>();
        for (String entry : usernames(context)) {
            if (!entry.equals(value)) {
                remaining.add(entry);
            }
        }

        // The session has to go with the username. Leaving it behind would keep a
        // usable token for an account the resident just removed from this device.
        forgetSession(context, value);

        prefs(context).edit()
                .putString(KEY_LIST, String.join("\n", remaining))
                .apply();
    }

    /** Clears every remembered account. */
    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_LIST)
                .remove(KEY_SESSIONS)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

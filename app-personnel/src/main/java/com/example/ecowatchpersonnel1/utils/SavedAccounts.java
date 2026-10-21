package com.example.ecowatchpersonnel1.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers the usernames this device has used, so signing in again does not mean
 * typing them again.
 *
 * <p>Personnel accounts are created by an administrator, so there is no
 * self-registration to pre-fill from. The username is still remembered on every
 * successful login, which makes switching between field accounts during
 * development quick. The login screen pre-fills the most recent one.
 *
 * <p><b>Passwords are never stored here.</b> Only the username, which the user
 * typed anyway and which is not a secret. Anything that needs a password has to
 * be typed.
 *
 * <p>Kept in preferences rather than a Room table on purpose: adding an entity
 * would mean a database migration and a version bump, which is a lot of risk for
 * a convenience list. This is a handful of short strings and is not queried.
 */
public final class SavedAccounts {

    private static final String PREFS = "ecowatch_saved_accounts";
    private static final String KEY_LIST = "usernames";

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

        prefs(context).edit()
                .putString(KEY_LIST, String.join("\n", remaining))
                .apply();
    }

    /** Clears every remembered account. */
    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_LIST).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

package com.example.ecowatchpersonnel1.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the Personnel app is allowed to alert this cleaner about.
 *
 * <p>Device-local: the personnel API has no preferences route, so these are the
 * app's own choices rather than anything the server enforces. That is why they are
 * read at the moment a notification is about to be shown rather than trusted once
 * at startup.
 *
 * <p>{@link #allowsAlert} fails open, deliberately, and does so for the same reason
 * the backend's own {@code allowsPush} does: a notification type this build has
 * never heard of, or a preferences file that has gone missing, must not silently
 * swallow an assignment. The master switch is the one setting allowed to silence
 * everything, because that is exactly what turning it off means.
 */
public final class NotificationPreferences {

    private static final String PREFS = "ecowatch_personnel_notification_prefs";

    private static final String KEY_MASTER = "master_enabled";
    private static final String KEY_TASK_ASSIGNED = "task_assigned";
    private static final String KEY_TASK_COMPLETED = "task_completed";
    private static final String KEY_TASK_REVISION = "task_revision";
    private static final String KEY_ANNOUNCEMENTS = "announcements";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_SOUND_URI = "sound_uri";
    private static final String KEY_VIBRATION = "vibration_enabled";

    private NotificationPreferences() {
    }

    // ------------------------------------------------------------- settings ---

    public static boolean isEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_MASTER, true);
    }

    public static void setEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MASTER, enabled).apply();
    }

    public static boolean isTaskAssignedEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TASK_ASSIGNED, true);
    }

    public static void setTaskAssignedEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TASK_ASSIGNED, enabled).apply();
    }

    public static boolean isTaskCompletedEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TASK_COMPLETED, true);
    }

    public static void setTaskCompletedEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TASK_COMPLETED, enabled).apply();
    }

    public static boolean isTaskRevisionEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TASK_REVISION, true);
    }

    public static void setTaskRevisionEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TASK_REVISION, enabled).apply();
    }

    public static boolean isAnnouncementsEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ANNOUNCEMENTS, true);
    }

    public static void setAnnouncementsEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ANNOUNCEMENTS, enabled).apply();
    }

    public static boolean isSoundEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SOUND, true);
    }

    public static void setSoundEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SOUND, enabled).apply();
    }

    public static boolean isVibrationEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_VIBRATION, true);
    }

    public static void setVibrationEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply();
    }

    /** The sound the cleaner chose, or null for the system default. */
    @Nullable
    public static Uri soundUri(@NonNull Context context) {
        String raw = prefs(context).getString(KEY_SOUND_URI, null);
        if (raw == null || raw.trim().isEmpty()) return null;
        return Uri.parse(raw.trim());
    }

    public static void setSoundUri(@NonNull Context context, @Nullable Uri uri) {
        prefs(context).edit()
                .putString(KEY_SOUND_URI, uri == null ? null : uri.toString())
                .apply();
    }

    // -------------------------------------------------------------- gating ---

    /**
     * Whether this notification may raise a system alert.
     *
     * <p>Only governs the alert. The in-app row is recorded either way, so
     * switching a category off silences the device without erasing the record of
     * what happened.
     *
     * @param type the server's notification type, may be null or unrecognised
     */
    public static boolean allowsAlert(@NonNull Context context, @Nullable String type) {
        return !NotificationCategory.isSuppressed(
                type,
                isEnabled(context),
                isTaskAssignedEnabled(context),
                isTaskCompletedEnabled(context),
                isTaskRevisionEnabled(context));
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

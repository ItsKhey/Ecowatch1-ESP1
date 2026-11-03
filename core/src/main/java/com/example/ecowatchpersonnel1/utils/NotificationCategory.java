package com.example.ecowatchpersonnel1.utils;

import androidx.annotation.Nullable;

/**
 * Which toggle on the notification settings screen governs which notification.
 *
 * <p>Separated from {@link NotificationPreferences} because the mapping is a
 * property of the wire contract rather than of the stored preferences, and it is
 * the part that has to be checked without a device. The two halves are joined in
 * {@link NotificationPreferences#allowsAlert}.
 *
 * <p>Fails open throughout. {@link #forType} returns null for a type this build
 * does not recognise, and {@link #isSuppressed} then allows it: the backend can add
 * an assignment type at any time, and a client that quietly drops the ones it
 * cannot classify would hide real work from the cleaner it exists to serve.
 */
public final class NotificationCategory {

    public static final String TASK_ASSIGNED = "taskAssigned";
    public static final String TASK_COMPLETED = "taskCompleted";
    public static final String TASK_REVISION = "taskRevision";

    private NotificationCategory() {
    }

    /**
     * The category governing a server-side notification type.
     *
     * <p>Spelled exactly as {@code PERSONNEL_NOTIFICATION_TYPES} in
     * personnelNotificationService.js.
     *
     * @return the category, or null when no toggle governs this type
     */
    @Nullable
    public static String forType(@Nullable String type) {
        if (type == null) return null;

        switch (type.trim().toUpperCase()) {
            case "ASSIGNMENT_ASSIGNED":
                return TASK_ASSIGNED;
            case "ASSIGNMENT_APPROVED":
                return TASK_COMPLETED;
            case "ASSIGNMENT_UNASSIGNED":
            case "ASSIGNMENT_REVISION_REQUESTED":
                return TASK_REVISION;
            default:
                return null;
        }
    }

    /**
     * Whether this notification should be silenced.
     *
     * <p>Only ever about raising an alert. The in-app row is recorded either way,
     * so switching a category off hides the interruption without erasing the record
     * of the assignment.
     *
     * @param masterEnabled    the Allow Notifications switch
     * @param taskAssigned     new assignments
     * @param taskCompleted    approvals
     * @param taskRevision     removals and revision requests
     */
    public static boolean isSuppressed(@Nullable String type,
                                       boolean masterEnabled,
                                       boolean taskAssigned,
                                       boolean taskCompleted,
                                       boolean taskRevision) {
        // The master switch is the one setting allowed to silence everything, since
        // that is precisely what turning it off means.
        if (!masterEnabled) return true;

        String category = forType(type);
        if (category == null) return false;

        switch (category) {
            case TASK_ASSIGNED:
                return !taskAssigned;
            case TASK_COMPLETED:
                return !taskCompleted;
            case TASK_REVISION:
                return !taskRevision;
            default:
                return false;
        }
    }
}
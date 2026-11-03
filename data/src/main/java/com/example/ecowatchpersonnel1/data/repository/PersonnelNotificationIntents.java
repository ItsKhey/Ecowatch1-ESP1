package com.example.ecowatchpersonnel1.data.repository;

import android.content.Context;
import android.content.Intent;

/**
 * The intent that opens a task from a tapped notification.
 *
 * <p>These values are part of the contract with the backend and with the
 * manifest, so they live in the data layer rather than on the dashboard
 * activity. Keeping them here lets the notification repository build the intent
 * without depending on the UI module; the dashboard reads the same constants
 * back out of the intent it receives.
 */
public final class PersonnelNotificationIntents {

    /** Must match {@code PERSONNEL_CLICK_ACTION} on the backend and the manifest. */
    public static final String ACTION_OPEN_NOTIFICATION =
            "com.example.ecowatchpersonnel1.action.OPEN_NOTIFICATION";

    // These names are the FCM data payload keys verbatim, so extras survive the
    // trip from the system tray untouched.
    public static final String EXTRA_NOTIFICATION_ID = "notificationId";
    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";
    public static final String EXTRA_REPORT_ID = "reportId";

    /**
     * Fully qualified dashboard class name.
     *
     * <p>Held as a string so this module does not need a compile-time dependency
     * on the app module. The class itself is merged into the APK.
     */
    private static final String DASHBOARD_ACTIVITY =
            "com.example.ecowatchpersonnel1.ui.home.DashboardActivity";

    private PersonnelNotificationIntents() {
    }

    /** Builds the tap target for a personnel notification. */
    public static Intent openNotification(Context context,
                                          String notificationId,
                                          String assignmentId,
                                          String reportId) {
        return new Intent()
                .setClassName(context, DASHBOARD_ACTIVITY)
                .setAction(ACTION_OPEN_NOTIFICATION)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_ASSIGNMENT_ID, assignmentId)
                .putExtra(EXTRA_REPORT_ID, reportId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}

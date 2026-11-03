package com.example.ecowatchpersonnel1.data.repository;

import com.example.ecowatchpersonnel1.data.local.PersonnelNotificationEntity;
import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;

import java.util.List;

/**
 * The one place that decides which id addresses a personnel notification.
 *
 * <p>The payload carries two candidate fields and the list and push paths do not
 * agree on which is populated, so both are consulted and the result is trimmed.
 * The UI, the sync and the delete calls must all resolve a row the same way; when
 * they disagree, a delete lands on the server while the local row survives (or
 * worse, the reverse), which is exactly the bug this class exists to prevent.
 */
public final class PersonnelNotificationIds {

    private PersonnelNotificationIds() {
    }

    /** The server id for one notification, or null when it carries neither field. */
    public static String resolve(PersonnelNotification notification) {
        return notification == null ? null : resolve(notification.notificationId, notification.id);
    }

    /** The server id from the two raw fields, or null when neither is usable. */
    public static String resolve(String notificationId, String id) {
        if (!isBlank(notificationId)) return notificationId.trim();
        return isBlank(id) ? null : id.trim();
    }

    /** The server id for a cached row. */
    public static String resolve(PersonnelNotificationEntity entity) {
        return entity == null ? null : resolve(entity.notificationId, null);
    }

    /**
     * The server ids for a list, in order, skipping rows that carry neither
     * field. Used by the bulk delete so one unaddressable row cannot quietly
     * cancel the whole batch.
     */
    public static List<String> resolveAll(List<PersonnelNotification> notifications) {
        if (notifications == null) return null;
        List<String> ids = new java.util.ArrayList<>();
        for (PersonnelNotification notification : notifications) {
            String id = resolve(notification);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

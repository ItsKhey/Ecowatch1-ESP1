package com.example.ecowatchpersonnel1.data.models;

/**
 * One row from {@code GET /api/personnel/notifications}.
 *
 * <p>Personnel notifications are their own table on the backend; they are never
 * the resident's report notifications. Tapping an assignment-related row opens
 * the task it belongs to.
 */
public class PersonnelNotification {

    public String notificationId;
    public String id;
    public String assignmentId;
    public String reportId;
    public String title;
    public String message;
    public String type;
    public boolean isRead;
    public String createdAt;

    public static final String TYPE_ASSIGNED = "ASSIGNMENT_ASSIGNED";
    public static final String TYPE_UNASSIGNED = "ASSIGNMENT_UNASSIGNED";
    public static final String TYPE_APPROVED = "ASSIGNMENT_APPROVED";

    /**
     * The administrator reviewed the finished work and sent it back. The task is
     * reopened as IN_PROGRESS, so the row still opens the same task.
     */
    public static final String TYPE_REVISION_REQUESTED = "ASSIGNMENT_REVISION_REQUESTED";

    /** True when the row points at a task that can still be opened. */
    public boolean opensTask() {
        return assignmentId != null && !assignmentId.trim().isEmpty();
    }
}

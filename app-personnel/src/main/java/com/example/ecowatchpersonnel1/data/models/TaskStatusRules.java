package com.example.ecowatchpersonnel1.data.models;

/**
 * The status rules shared by the tasks list and the task history.
 *
 * <p>Extracted because both screens filter on them and must agree. If the tasks
 * tab said a completed task was "Completed" while history said "Under Approval"
 * for the same row, a cleaner would have no way to tell which one was right.
 *
 * <p>Every method takes the raw strings rather than a model so it works on a live
 * {@link PersonnelTask} and on a saved {@code TaskHistoryEntity} alike, which is
 * the whole point: the two are separate types with the same shape.
 */
public final class TaskStatusRules {

    /** Not a server status. Stands for completed-but-not-yet-approved work. */
    public static final String FILTER_UNDER_APPROVAL = "UNDER_APPROVAL";

    /** The report status an admin leaves while reviewing a cleaner finished a task. */
    private static final String REPORT_STATUS_UNDER_REVIEW = "Under Review";

    private TaskStatusRules() {
    }

    /** True for work still in this cleaner's hands: assigned, accepted or in progress. */
    public static boolean isActive(String status) {
        return PersonnelTask.STATUS_ASSIGNED.equals(status)
                || PersonnelTask.STATUS_ACCEPTED.equals(status)
                || PersonnelTask.STATUS_IN_PROGRESS.equals(status);
    }

    /**
     * True when the work is finished but an administrator has not signed it off.
     *
     * <p>There is no {@code UNDER_APPROVAL} assignment status, so this is derived
     * from the report: the assignment is COMPLETED while the report it cleans is
     * still Under Review. Both spellings of the status are accepted because the
     * list and the single-task response can carry it from different layers.
     */
    public static boolean isAwaitingApproval(String assignmentStatus, String reportStatus) {
        if (PersonnelTask.STATUS_UNDER_APPROVAL.equalsIgnoreCase(assignmentStatus)
                || "UNDER APPROVAL".equalsIgnoreCase(assignmentStatus)) {
            return true;
        }
        if (!PersonnelTask.STATUS_COMPLETED.equals(assignmentStatus) || reportStatus == null) {
            return false;
        }
        return REPORT_STATUS_UNDER_REVIEW.equalsIgnoreCase(reportStatus.trim());
    }

    /** The chip label for one saved status value. */
    public static String label(String status) {
        if (status == null) return "Unknown";
        switch (status) {
            case PersonnelTask.STATUS_ASSIGNED:
                return "Assigned";
            case PersonnelTask.STATUS_ACCEPTED:
                return "Accepted";
            case PersonnelTask.STATUS_IN_PROGRESS:
                return "In Progress";
            case PersonnelTask.STATUS_UNDER_APPROVAL:
                return "Under Approval";
            case PersonnelTask.STATUS_COMPLETED:
                return "Completed";
            case PersonnelTask.STATUS_CANCELLED:
                return "Cancelled";
            default:
                return status;
        }
    }
}
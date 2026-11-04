package com.example.ecowatchpersonnel1.data.models;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * One cleaning assignment, as returned by every {@code /api/personnel/tasks}
 * endpoint.
 *
 * <p>The same shape is used for the current task, the task list, and a single
 * task, so the UI has exactly one model to render and one place to change.
 */
public class PersonnelTask {

    public String assignmentId;
    public String reportId;
    public String personnelId;
    public String clusterId;
    public String status;
    public String assignedByAdminId;
    public String assignedAt;
    public String acceptedAt;
    public String startedAt;
    public String completedAt;
    public String completionNotes;
    public String createdAt;
    public String updatedAt;
    public TaskReport report;
    public Evidence evidence;
    public int beforeCount;
    public int afterCount;
    public TaskPermissions permissions;
    public boolean isTeamTask;
    public boolean isTeamLeader;

    /**
     * True while the current assignment cycle includes an administrator
     * revision. Boxed so an older server that sends only the report timeline
     * falls through to {@link #latestRevisionAt()}.
     */
    @SerializedName(value = "revisionRequested", alternate = {"revision_requested"})
    public Boolean revisionRequested;

    /**
     * How big the job is, as the server spells it: "Small-Scale Waste",
     * "Medium-Scale Waste" or "Large-Scale Waste".
     *
     * <p>This is the size the resident filed the report under, and it is what
     * decided the shape of the dispatch. A cleaner on a Large-Scale Waste needs
     * to know a truck is coming, and a cleaner on a Medium-Scale Waste needs to
     * know they are one of a crew rather than the only one on site.
     */
    public String wasteScale;

    /** The cleaning crew that was dispatched for this task, if any. */
    public String assignedTeamId;
    public String assignedTeamName;

    /** The collection truck that was dispatched for this task, if any. */
    public String assignedVehicleId;
    public String assignedVehiclePlateNumber;
    public String assignedVehicleType;

    /**
     * The people who were actually dispatched, with their names.
     *
     * <p>A crew is only worth naming for Medium/Large-Scale Waste. The server
     * resolves it from the cleaning team, falling back to ad-hoc personnel ids.
     */
    @SerializedName(value = "assignedCrew", alternate = {
            "assigned_crew", "assignedCrewMembers", "assigned_crew_members",
            "teamMembers", "team_members", "crew", "crewMembers", "crew_members", "members"
    })
    public List<CrewMember> assignedCrew = new ArrayList<>();

    /** The before/after evidence buckets the backend returns together. */
    public static class Evidence {
        public List<PersonnelEvidence> before = new ArrayList<>();
        public List<PersonnelEvidence> after = new ArrayList<>();
    }

    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_UNDER_APPROVAL = "UNDER_APPROVAL";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * The report status the admin reviews before it is closed out. Spelled as
     * the API spells it; compared case-insensitively because the list and the
     * single-task response can carry the value from different layers.
     */
    private static final String REPORT_STATUS_UNDER_REVIEW = "Under Review";

    /** A short, human label for the status chip. */
    public String statusLabel() {
        if (awaitingApproval()
                || STATUS_UNDER_APPROVAL.equalsIgnoreCase(status)
                || "UNDER APPROVAL".equalsIgnoreCase(status)) {
            return "Under Approval";
        }
        if (status == null) return "Unknown";
        switch (status) {
            case STATUS_ASSIGNED:
                return "Assigned";
            case STATUS_ACCEPTED:
                return "Accepted";
            case STATUS_IN_PROGRESS:
                return "In Progress";
            case STATUS_UNDER_APPROVAL:
                return "Under Approval";
            case STATUS_COMPLETED:
                return "Completed";
            case STATUS_CANCELLED:
                return "Cancelled";
            default:
                return status;
        }
    }

    public boolean isActive() {
        return STATUS_ASSIGNED.equals(status)
                || STATUS_ACCEPTED.equals(status)
                || STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /**
     * True when the cleaner has finished the work but an admin has not signed
     * it off yet.
     *
     * <p>There is no {@code AWAITING_APPROVAL} assignment status, so this is
     * derived: the assignment is {@link #STATUS_COMPLETED} while the report it
     * cleans is still under review. Once the admin approves, the report leaves
     * that status and the task drops out of this list, which is exactly what a
     * cleaner checking "did anyone look at my work yet?" wants to see.
     */
    public boolean awaitingApproval() {
        if (STATUS_UNDER_APPROVAL.equalsIgnoreCase(status)
                || "UNDER APPROVAL".equalsIgnoreCase(status)) {
            return true;
        }
        if (!isCompleted() || report == null || report.status == null) return false;
        return REPORT_STATUS_UNDER_REVIEW.equalsIgnoreCase(report.status.trim());
    }

    /**
     * True when this assignment was sent back for revision.
     *
     * <p>Prefers the server's current-cycle flag. When an older server sends no
     * flag, the presence of a revision event on the report timeline is the best
     * evidence available.
     */
    public boolean isRevisionRequested() {
        if (revisionRequested != null) return revisionRequested;
        if (report != null && report.revisionRequested != null) {
            return report.revisionRequested;
        }
        return latestRevisionAt() != null;
    }

    /**
     * The timestamp of the most recent revision request, or null.
     *
     * <p>Read from the report timeline so the detail screen can say when the
     * work was sent back instead of leaving it to the notification that has
     * already been dismissed.
     */
    public String latestRevisionAt() {
        if (report == null || report.timeline == null) return null;

        String latest = null;
        for (TaskTimelineEvent event : report.timeline) {
            if (event == null
                    || !"REVISION_REQUESTED".equalsIgnoreCase(event.action)
                    || event.timestamp == null
                    || event.timestamp.trim().isEmpty()) {
                continue;
            }
            String timestamp = event.timestamp.trim();
            if (latest == null || timestamp.compareTo(latest) >= 0) {
                latest = timestamp;
            }
        }
        return latest;
    }

    public String reportTitle() {
        if (report != null && report.title != null && !report.title.trim().isEmpty()) {
            return report.title.trim();
        }
        return reportId != null ? reportId : "Cleaning task";
    }

    /**
     * The crew members dispatched for this task.
     */
    public List<CrewMember> getAssignedCrew() {
        return assignedCrew == null ? new ArrayList<>() : assignedCrew;
    }

    public void setAssignedCrew(List<CrewMember> assignedCrew) {
        this.assignedCrew = assignedCrew == null ? new ArrayList<>() : assignedCrew;
    }

    /**
     * The scale of the job, folded onto the canonical vocabulary.
     *
     * <p>Prefers the task's own scale and falls back to the report's, because
     * the two are the same fact seen from two sides: the assignment records
     * what the administrator dispatched, and the report records what the
     * resident filed. Older rows carry it in one place only.
     */
    public String getWasteScale() {
        if (wasteScale != null && !wasteScale.trim().isEmpty()) {
            return WasteScale.canonical(wasteScale);
        }
        if (report != null && report.wasteScale != null && !report.wasteScale.trim().isEmpty()) {
            return WasteScale.canonical(report.wasteScale);
        }
        return WasteScale.SMALL;
    }

    /**
     * The crew that was dispatched, preferring the name the server joined on.
     *
     * <p>Falls back to the id when the name is missing, which happens for a team
     * that was deleted after it was dispatched. Showing the id is honest.
     */
    public String getAssignedTeamLabel() {
        if (assignedTeamName != null && !assignedTeamName.trim().isEmpty()) {
            return assignedTeamName.trim();
        }
        if (assignedTeamId != null && !assignedTeamId.trim().isEmpty()) {
            return assignedTeamId.trim();
        }
        return null;
    }

    /**
     * The truck that was dispatched, preferring the plate number.
     *
     * <p>The plate is what the crew and the resident actually recognise, so it
     * comes first; the type is kept as a fallback for a vehicle record that
     * never carried a plate.
     */
    public String getAssignedVehicleLabel() {
        if (assignedVehiclePlateNumber != null && !assignedVehiclePlateNumber.trim().isEmpty()) {
            String type = assignedVehicleType == null || assignedVehicleType.trim().isEmpty()
                    ? null : assignedVehicleType.trim();
            return type == null
                    ? assignedVehiclePlateNumber.trim()
                    : assignedVehiclePlateNumber.trim() + " (" + type + ")";
        }
        if (assignedVehicleType != null && !assignedVehicleType.trim().isEmpty()) {
            return assignedVehicleType.trim();
        }
        if (assignedVehicleId != null && !assignedVehicleId.trim().isEmpty()) {
            return assignedVehicleId.trim();
        }
        return null;
    }
}

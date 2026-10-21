package com.example.ecowatchpersonnel1.data.models;

/**
 * The resident report a task is cleaning, flattened by the backend into the
 * assignment payload so the app never has to make a second call.
 */
public class TaskReport {

    public String reportId;
    public String title;
    public String category;
    public String description;
    public Double latitude;
    public Double longitude;
    public String imageUrl;
    public String status;
    public String priority;
    /**
     * The official answer the resident reads. Not the same thing as
     * {@link #personnelNote}: on assign this is the message the admin writes
     * for the resident, so a cleaner has no reason to quote it as if it were
     * addressed to them.
     */
    public String adminResponse;
    /**
     * The administrator's private note for the assigned cleaner (assignment
     * instructions and revision reasons). Never leaves the admin/personnel
     * side of the app.
     */
    public String personnelNote;
    public String clusterId;
    public String createdAt;
    public String resolvedAt;
    /**
     * The size the resident filed the report under, as the server spells it.
     *
     * <p>The same fact the assignment carries as {@link PersonnelTask#wasteScale},
     * seen from the report side. Kept on both because the two can arrive from
     * different layers, and a task that only came back with its report should
     * still be able to say how big the job is.
     */
    public String wasteScale;

    /** The scale of the report, folded onto the canonical vocabulary. */
    public String getWasteScale() {
        return WasteScale.canonical(wasteScale);
    }
}

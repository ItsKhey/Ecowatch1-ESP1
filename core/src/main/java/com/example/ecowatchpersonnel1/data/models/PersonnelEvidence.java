package com.example.ecowatchpersonnel1.data.models;

/**
 * One before-or-after cleaning photo recorded against an assignment.
 *
 * <p>{@link #phase} is {@code BEFORE} or {@code AFTER}; the backend decides which
 * phases are legal for the task's current status and only ever returns those.
 */
public class PersonnelEvidence {

    public String evidenceId;
    public String assignmentId;
    public String reportId;
    public String phase;
    public String imageUrl;
    public String uploadedAt;

    public boolean isBefore() {
        return "BEFORE".equalsIgnoreCase(phase);
    }
}

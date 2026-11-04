package com.example.ecowatchpersonnel1.data.models;

/**
 * One cleaning event from a report's audit trail.
 *
 * <p>The server sends this on the report so a task detail screen can show the
 * real sequence of work: cleaning started, evidence submitted, completed and,
 * when it happens, a revision requested. Only the personnel side receives it,
 * because the administrator's revision note is not resident-facing.
 */
public class TaskTimelineEvent {

    /** The server action, for example {@code REVISION_REQUESTED}. */
    public String action;

    /** A human-readable description written by the server. */
    public String description;

    /** The administrator who caused the event, when one did. */
    public String adminId;

    /** ISO-8601 timestamp, exactly as the server sent it. */
    public String timestamp;
}

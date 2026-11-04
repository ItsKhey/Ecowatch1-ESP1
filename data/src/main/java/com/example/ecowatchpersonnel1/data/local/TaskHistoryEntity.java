package com.example.ecowatchpersonnel1.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.ecowatchpersonnel1.data.models.PersonnelTask;

/**
 * One cleaning assignment as this device has seen it.
 *
 * <p>The server owns the truth about a task; this table is the device's copy so
 * Task History has something to render the moment the screen opens, and still has
 * something to render with no connection at all. Rows are keyed by
 * {@code assignmentId} and written with REPLACE, so re-syncing updates a row in
 * place instead of piling up duplicates.
 *
 * <p>The report the task cleans is flattened onto the row rather than left as a
 * separate table: history always needs the title and category next to the
 * status, and always reads them together, so a join would buy nothing.
 */
@Entity(
        tableName = "task_history",
        indices = {
                @Index(value = {"personnelId", "status"}),
                @Index(value = {"personnelId", "completedAt"})
        }
)
public class TaskHistoryEntity {

    /**
     * The assignment id. This is the server's identifier for the work, so it is
     * the natural key: a task keeps the same row for its whole life.
     */
    @PrimaryKey
    @NonNull
    public String assignmentId = "";

    /** Which cleaner the row belongs to, so one device can hold several accounts. */
    @ColumnInfo(name = "personnelId")
    public String personnelId;

    @ColumnInfo(name = "reportId")
    public String reportId;

    @ColumnInfo(name = "clusterId")
    public String clusterId;

    /** ASSIGNMENT, ACCEPTED, IN_PROGRESS, COMPLETED or CANCELLED, as the server spells it. */
    @ColumnInfo(name = "status")
    public String status;

    /** The status of the resident report this task cleans, when the server sent one. */
    @ColumnInfo(name = "reportStatus")
    public String reportStatus;

    /**
     * True while this assignment cycle includes an administrator revision.
     *
     * <p>Saved rather than derived from the report status: a task sent back for
     * revision is IN_PROGRESS again, which is also what a brand-new task looks
     * like. Without the server's flag the history tab could not tell them apart.
     */
    @ColumnInfo(name = "revisionRequested")
    public boolean revisionRequested;

    /** When the administrator last sent this task back, or null. */
    @ColumnInfo(name = "revisionRequestedAt")
    public String revisionRequestedAt;

    @ColumnInfo(name = "reportTitle")
    public String reportTitle;

    @ColumnInfo(name = "reportCategory")
    public String reportCategory;

    @ColumnInfo(name = "reportDescription")
    public String reportDescription;

    @ColumnInfo(name = "reportPriority")
    public String reportPriority;

    @ColumnInfo(name = "reportLatitude")
    public Double reportLatitude;

    @ColumnInfo(name = "reportLongitude")
    public Double reportLongitude;

    @ColumnInfo(name = "reportImageUrl")
    public String reportImageUrl;

    @ColumnInfo(name = "beforeCount")
    public int beforeCount;

    @ColumnInfo(name = "afterCount")
    public int afterCount;

    /** ISO-8601 timestamps exactly as the server sent them, so no parsing is lost. */
    @ColumnInfo(name = "assignedAt")
    public String assignedAt;

    @ColumnInfo(name = "acceptedAt")
    public String acceptedAt;

    @ColumnInfo(name = "startedAt")
    public String startedAt;

    @ColumnInfo(name = "completedAt")
    public String completedAt;

    @ColumnInfo(name = "createdAt")
    public String createdAt;

    @ColumnInfo(name = "updatedAt")
    public String updatedAt;

    @ColumnInfo(name = "completionNotes")
    public String completionNotes;

    /**
     * The size of the job, as the server spells it.
     *
     * <p>Saved with the rest of the row so the history list can show it without
     * a connection, the same way it shows the title and the category. The column
     * was added after the table already shipped, so rows written before the
     * upgrade carry nothing here and read as the smallest scale.
     */
    @ColumnInfo(name = "wasteScale")
    public String wasteScale;

    /** When this device last wrote the row, so a stale cache can be recognised. */
    @ColumnInfo(name = "syncedAt")
    public long syncedAt;

    /**
     * Flattens a server task into a storable row.
     *
     * <p>Null-safe throughout: the task list, the single-task response and the
     * current-task response do not all carry the same fields, and a missing one
     * must not stop the row from being saved.
     */
    public static TaskHistoryEntity from(@NonNull PersonnelTask task, String personnelId) {
        TaskHistoryEntity entity = new TaskHistoryEntity();
        entity.assignmentId = task.assignmentId == null ? "" : task.assignmentId;
        entity.personnelId = personnelId;
        entity.reportId = task.reportId;
        entity.clusterId = task.clusterId;
        entity.status = task.status;
        entity.revisionRequested = task.isRevisionRequested();
        entity.revisionRequestedAt = task.latestRevisionAt();
        entity.beforeCount = task.beforeCount;
        entity.afterCount = task.afterCount;
        entity.assignedAt = task.assignedAt;
        entity.acceptedAt = task.acceptedAt;
        entity.startedAt = task.startedAt;
        entity.completedAt = task.completedAt;
        entity.createdAt = task.createdAt;
        entity.updatedAt = task.updatedAt;
        entity.completionNotes = task.completionNotes;
        entity.wasteScale = task.getWasteScale();
        entity.syncedAt = System.currentTimeMillis();

        if (task.report != null) {
            entity.reportStatus = task.report.status;
            entity.reportTitle = task.report.title;
            entity.reportCategory = task.report.category;
            entity.reportDescription = task.report.description;
            entity.reportPriority = task.report.priority;
            entity.reportLatitude = task.report.latitude;
            entity.reportLongitude = task.report.longitude;
            entity.reportImageUrl = task.report.imageUrl;
        }
        return entity;
    }
}
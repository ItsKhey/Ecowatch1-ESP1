package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.data.local.TaskHistoryEntity;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.models.TaskReport;

import org.junit.Test;

/**
 * Flattening a server task into the row Task History stores.
 *
 * <p>The mapper is the only place the two shapes meet, and a dropped field here
 * is a permanently missing column in the saved history rather than a visible bug.
 */
public class TaskHistoryEntityTest {

    @Test
    public void assignmentFieldsAreCopied() {
        PersonnelTask task = new PersonnelTask();
        task.assignmentId = "PERS-0001";
        task.reportId = "RPT-0001";
        task.clusterId = "CL-1";
        task.status = PersonnelTask.STATUS_COMPLETED;
        task.beforeCount = 2;
        task.afterCount = 3;
        task.assignedAt = "2026-03-01T08:00:00Z";
        task.acceptedAt = "2026-03-01T09:00:00Z";
        task.startedAt = "2026-03-02T07:00:00Z";
        task.completedAt = "2026-03-03T10:00:00Z";
        task.completionNotes = "Cleared the drain.";

        TaskHistoryEntity row = TaskHistoryEntity.from(task, "PERS-0001");

        assertEquals("PERS-0001", row.assignmentId);
        assertEquals("PERS-0001", row.personnelId);
        assertEquals("RPT-0001", row.reportId);
        assertEquals("CL-1", row.clusterId);
        assertEquals(PersonnelTask.STATUS_COMPLETED, row.status);
        assertEquals(2, row.beforeCount);
        assertEquals(3, row.afterCount);
        assertEquals("2026-03-01T08:00:00Z", row.assignedAt);
        assertEquals("2026-03-01T09:00:00Z", row.acceptedAt);
        assertEquals("2026-03-02T07:00:00Z", row.startedAt);
        assertEquals("2026-03-03T10:00:00Z", row.completedAt);
        assertEquals("Cleared the drain.", row.completionNotes);
    }

    @Test
    public void reportIsFlattenedOntoTheRow() {
        // History always shows the title and category next to the status, so the
        // report has to travel with the row rather than behind a second query.
        PersonnelTask task = new PersonnelTask();
        task.assignmentId = "PERS-0002";

        TaskReport report = new TaskReport();
        report.status = "Under Review";
        report.title = "Overflowing garbage";
        report.category = "Plastic Waste";
        report.description = "Bottles beside the footpath.";
        report.priority = "HIGH";
        report.latitude = 14.5995;
        report.longitude = 120.9842;
        report.imageUrl = "/uploads/report.jpg";
        task.report = report;

        TaskHistoryEntity row = TaskHistoryEntity.from(task, "PERS-0002");

        assertEquals("Under Review", row.reportStatus);
        assertEquals("Overflowing garbage", row.reportTitle);
        assertEquals("Plastic Waste", row.reportCategory);
        assertEquals("Bottles beside the footpath.", row.reportDescription);
        assertEquals("HIGH", row.reportPriority);
        assertEquals(14.5995, row.reportLatitude, 0.0001);
        assertEquals(120.9842, row.reportLongitude, 0.0001);
        assertEquals("/uploads/report.jpg", row.reportImageUrl);
    }

    @Test
    public void taskWithoutAReportStillProducesARow() {
        // The single-task and current-task responses can arrive without the
        // report joined on. That must not throw and lose the status change.
        PersonnelTask task = new PersonnelTask();
        task.assignmentId = "PERS-0003";
        task.status = PersonnelTask.STATUS_ACCEPTED;

        TaskHistoryEntity row = TaskHistoryEntity.from(task, "PERS-0003");

        assertNotNull(row);
        assertEquals(PersonnelTask.STATUS_ACCEPTED, row.status);
        assertNull(row.reportTitle);
        assertNull(row.reportCategory);
    }

    @Test
    public void missingAssignmentIdBecomesAnEmptyKeyRatherThanNull() {
        // assignmentId is the primary key and Room rejects a null one, so an
        // unexpected payload must still land as a row the sync can prune.
        PersonnelTask task = new PersonnelTask();
        task.status = PersonnelTask.STATUS_ASSIGNED;

        TaskHistoryEntity row = TaskHistoryEntity.from(task, "PERS-0004");

        assertEquals("", row.assignmentId);
    }

    @Test
    public void syncTimeIsStamped() {
        PersonnelTask task = new PersonnelTask();
        task.assignmentId = "PERS-0005";

        long before = System.currentTimeMillis();
        TaskHistoryEntity row = TaskHistoryEntity.from(task, "PERS-0005");
        long after = System.currentTimeMillis();

        assertTrue(row.syncedAt >= before);
        assertTrue(row.syncedAt <= after);
    }
}
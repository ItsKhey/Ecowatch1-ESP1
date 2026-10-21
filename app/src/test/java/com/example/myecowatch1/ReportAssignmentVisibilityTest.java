package com.example.myecowatch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.myecowatch1.data.models.Report;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;

/**
 * The report details dialog only shows the Cleanup Assignment card when a crew
 * has actually been put on the report.
 *
 * <p>The card must never appear as an empty heading above a blank space, which
 * means the decision has to be driven by real assignment data rather than by the
 * report merely having reached some status. The server writes the assignment
 * columns independently, so any one of them is enough to prove a crew was put on
 * the job, and their absence has to be treated as "not assigned" even when the
 * rest of the payload is complete.
 */
public class ReportAssignmentVisibilityTest {

    private final Gson gson = new Gson();

    private Report reportFrom(String json) {
        return gson.fromJson(json, Report.class);
    }

    /** An untouched report: everything the server sends, with no assignment on it. */
    private static final String UNASSIGNED = "{"
            + "\"reportId\":\"REP-ABC123\","
            + "\"title\":\"Pile of plastic near the gate\","
            + "\"category\":\"Plastic\","
            + "\"status\":\"Pending\","
            + "\"adminResponse\":\"Crew is on the way.\","
            + "\"assignedPersonnelId\":null,"
            + "\"assignedPersonnelIds\":[],"
            + "\"assignedAt\":null,"
            + "\"handledById\":null,"
            + "\"resolvedAt\":null,"
            + "\"personnelResponse\":null"
            + "}";

    @Test
    public void aReportWithNoAssignmentHidesTheCard() {
        Report report = reportFrom(UNASSIGNED);

        assertFalse("no crew means no assignment card", report.isAssigned());
    }

    @Test
    public void anAssignedReportShowsTheCard() {
        Report report = reportFrom("{"
                + "\"reportId\":\"REP-ABC123\","
                + "\"status\":\"In Progress\","
                + "\"assignedPersonnelId\":\"PERS-0001\","
                + "\"assignedAt\":\"2026-10-09T07:15:00.000Z\""
                + "}");

        assertTrue(report.isAssigned());
        assertEquals("PERS-0001", report.getAssignedPersonnelId());
        assertEquals("2026-10-09T07:15:00.000Z", report.getAssignedAt());
    }

    /** A timestamp alone is proof enough: the identifiers are written separately. */
    @Test
    public void anAssignmentTimestampAloneCounts() {
        Report report = reportFrom("{\"assignedAt\":\"2026-10-09T07:15:00.000Z\"}");

        assertTrue(report.isAssigned());
    }

    @Test
    public void aPersonnelListAloneCounts() {
        Report report = reportFrom("{\"assignedPersonnelIds\":[\"PERS-0001\",\"PERS-0002\"]}");

        assertTrue(report.isAssigned());
        assertEquals(2, report.getAssignedPersonnelIds().size());
    }

    /**
     * Blank strings are not an assignment. The server normalises empties to "" in
     * places, and treating that as "assigned" would bring the empty card back.
     */
    @Test
    public void blankAssignmentFieldsDoNotCount() {
        Report report = reportFrom("{"
                + "\"assignedPersonnelId\":\"   \","
                + "\"assignedAt\":\"\","
                + "\"assignedPersonnelIds\":[]"
                + "}");

        assertFalse(report.isAssigned());
    }

    /** Postgres can hand back a blank instead of null on an unassigned column. */
    @Test
    public void anAllBlankPayloadStillReadsAsUnassigned() {
        Report report = reportFrom("{"
                + "\"assignedPersonnelId\":\"\","
                + "\"assignedAt\":\"\","
                + "\"handledById\":\"\","
                + "\"resolvedAt\":\"\""
                + "}");

        assertFalse(report.isAssigned());
    }

    /**
     * The backend sends camelCase to the app, but the snake_case aliases are what
     * the older payloads and the archived/trash routes use, so both must land on
     * the same fields.
     */
    @Test
    public void bothCasingConventionsReachTheSameFields() {
        Report camel = reportFrom("{\"assignedAt\":\"2026-10-09T07:15:00.000Z\",\"resolvedAt\":\"2026-10-09T09:40:00.000Z\"}");
        Report snake = reportFrom("{\"assigned_at\":\"2026-10-09T07:15:00.000Z\",\"resolved_at\":\"2026-10-09T09:40:00.000Z\"}");

        assertEquals(camel.getAssignedAt(), snake.getAssignedAt());
        assertEquals(camel.getResolvedAt(), snake.getResolvedAt());
        assertTrue(snake.isAssigned());
    }

    /** A completed cleanup still counts as assigned: the card reports the whole arc. */
    @Test
    public void aCompletedReportKeepsItsAssignment() {
        Report report = reportFrom("{"
                + "\"status\":\"Completed\","
                + "\"assignedPersonnelId\":\"PERS-0001\","
                + "\"assignedAt\":\"2026-10-09T07:15:00.000Z\","
                + "\"handledById\":\"PERS-0001\","
                + "\"resolvedAt\":\"2026-10-09T09:40:00.000Z\""
                + "}");

        assertTrue(report.isAssigned());
        assertEquals("2026-10-09T09:40:00.000Z", report.getResolvedAt());
    }

    /** The cleaner note and the admin answer are separate voices and both optional. */
    @Test
    public void aCleanerNoteIsIndependentOfTheAdminAnswer() {
        Report cleaned = reportFrom("{"
                + "\"adminResponse\":\"Crew dispatched.\","
                + "\"personnelResponse\":\"Cleared the pile and swept the area.\""
                + "}");
        Report adminOnly = reportFrom("{\"adminResponse\":\"Crew dispatched.\"}");

        assertEquals("Cleared the pile and swept the area.", cleaned.getPersonnelResponse());
        assertEquals(null, adminOnly.getPersonnelResponse());
        assertFalse("no crew, so no assignment card", cleaned.isAssigned());
    }

    /** A null list must never blow up the getter the dialog reads. */
    @Test
    public void aNullPersonnelListReadsAsEmpty() {
        Report report = reportFrom(UNASSIGNED);
        report.setAssignedPersonnelIds(null);

        assertTrue(report.getAssignedPersonnelIds().isEmpty());
        assertFalse(report.isAssigned());
    }

    @Test
    public void theSetterNormalisesANullList() {
        Report report = reportFrom(UNASSIGNED);
        report.setAssignedPersonnelIds(null);

        report.setAssignedPersonnelIds(Arrays.asList("PERS-0003"));
        assertEquals(1, report.getAssignedPersonnelIds().size());
        assertTrue(report.isAssigned());
    }
}

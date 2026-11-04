package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.models.TaskStatusRules;

import org.junit.Test;

/**
 * The status rules the tasks list and the task history both filter on.
 *
 * <p>These are the rules most likely to rot quietly: they are derived rather than
 * stored, and a change to either screen would not fail a build if only one of the
 * two were updated.
 */
public class TaskStatusRulesTest {

    @Test
    public void assignedAcceptedAndInProgressAreActive() {
        assertTrue(TaskStatusRules.isActive(PersonnelTask.STATUS_ASSIGNED));
        assertTrue(TaskStatusRules.isActive(PersonnelTask.STATUS_ACCEPTED));
        assertTrue(TaskStatusRules.isActive(PersonnelTask.STATUS_IN_PROGRESS));
    }

    @Test
    public void finishedAndCancelledWorkIsNotActive() {
        assertFalse(TaskStatusRules.isActive(PersonnelTask.STATUS_COMPLETED));
        assertFalse(TaskStatusRules.isActive(PersonnelTask.STATUS_CANCELLED));
        assertFalse(TaskStatusRules.isActive(null));
    }

    @Test
    public void completedTaskWhoseReportIsUnderReviewIsAwaitingApproval() {
        // The case the derivation exists for: the cleaner is done, but nobody has
        // signed it off yet, so "Completed" would be a misleading chip.
        assertTrue(TaskStatusRules.isAwaitingApproval(
                PersonnelTask.STATUS_COMPLETED, "Under Review"));
    }

    @Test
    public void reviewCheckIgnoresCaseAndPadding() {
        assertTrue(TaskStatusRules.isAwaitingApproval(
                PersonnelTask.STATUS_COMPLETED, "  under review  "));
    }

    @Test
    public void completedTaskIsNotAwaitingApprovalOnceTheReportIsResolved() {
        assertFalse(TaskStatusRules.isAwaitingApproval(
                PersonnelTask.STATUS_COMPLETED, "Resolved"));
        assertFalse(TaskStatusRules.isAwaitingApproval(
                PersonnelTask.STATUS_COMPLETED, null));
    }

    @Test
    public void workThatIsNotCompleteIsNeverAwaitingApproval() {
        // An in-progress task must not be pulled into the Under Approval bucket
        // by a report that happens to be under review for another reason.
        assertFalse(TaskStatusRules.isAwaitingApproval(
                PersonnelTask.STATUS_IN_PROGRESS, "Under Review"));
        assertFalse(TaskStatusRules.isAwaitingApproval(
                PersonnelTask.STATUS_ASSIGNED, "Under Review"));
    }

    @Test
    public void bothSpellingsOfTheUnderApprovalStatusAreAccepted() {
        assertTrue(TaskStatusRules.isAwaitingApproval("UNDER_APPROVAL", null));
        assertTrue(TaskStatusRules.isAwaitingApproval("under approval", null));
    }

    @Test
    public void revisionIsDetectedOnEitherStatus() {
        assertTrue(TaskStatusRules.isRevision("REVISION", null));
        assertTrue(TaskStatusRules.isRevision("FOR_REVISION", null));
        assertTrue(TaskStatusRules.isRevision(
                PersonnelTask.STATUS_COMPLETED, "Needs Revision"));
    }

    @Test
    public void revisionCheckIgnoresCaseAndPadding() {
        assertTrue(TaskStatusRules.isRevision("  needs revision  ", null));
        assertTrue(TaskStatusRules.isRevision(null, " for revision "));
    }

    @Test
    public void ordinaryStatusesAreNotRevisions() {
        assertFalse(TaskStatusRules.isRevision(PersonnelTask.STATUS_COMPLETED, "Under Review"));
        assertFalse(TaskStatusRules.isRevision(PersonnelTask.STATUS_IN_PROGRESS, "Resolved"));
        assertFalse(TaskStatusRules.isRevision(null, null));
    }

    @Test
    public void labelsCoverEveryServerStatus() {
        assertEquals("Assigned", TaskStatusRules.label(PersonnelTask.STATUS_ASSIGNED));
        assertEquals("Accepted", TaskStatusRules.label(PersonnelTask.STATUS_ACCEPTED));
        assertEquals("In Progress", TaskStatusRules.label(PersonnelTask.STATUS_IN_PROGRESS));
        assertEquals("Under Approval", TaskStatusRules.label(PersonnelTask.STATUS_UNDER_APPROVAL));
        assertEquals("Completed", TaskStatusRules.label(PersonnelTask.STATUS_COMPLETED));
        assertEquals("Cancelled", TaskStatusRules.label(PersonnelTask.STATUS_CANCELLED));
    }

    @Test
    public void unknownStatusIsShownRatherThanHidden() {
        // A status the app does not recognise is passed through: hiding the row
        // would make a task look like it was never assigned.
        assertEquals("SOMETHING_NEW", TaskStatusRules.label("SOMETHING_NEW"));
        assertEquals("Unknown", TaskStatusRules.label(null));
    }
}
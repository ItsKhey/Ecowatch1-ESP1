package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.utils.NotificationCategory;

import org.junit.Test;

/**
 * Which notification types each toggle on the settings screen governs.
 *
 * <p>Kept as a plain function so the mapping can be checked without a device. The
 * mapping itself has to agree with {@code PERSONNEL_NOTIFICATION_TYPES} in the
 * backend's personnelNotificationService.js, and the fail-open behaviour is the
 * part most worth pinning down: a type this build does not recognise must not be
 * silently swallowed, because it may be an assignment.
 */
public class NotificationCategoryTest {

    @Test
    public void anAssignmentAnswersToTheAssignedToggle() {
        assertEquals("taskAssigned", NotificationCategory.forType("ASSIGNMENT_ASSIGNED"));
    }

    @Test
    public void anApprovalAnswersToTheCompletedToggle() {
        assertEquals("taskCompleted", NotificationCategory.forType("ASSIGNMENT_APPROVED"));
    }

    @Test
    public void removalAndRevisionShareTheRevisionToggle() {
        // Both mean the work just changed shape, so they are the same decision to
        // make and are silenced together.
        assertEquals("taskRevision", NotificationCategory.forType("ASSIGNMENT_UNASSIGNED"));
        assertEquals("taskRevision",
                NotificationCategory.forType("ASSIGNMENT_REVISION_REQUESTED"));
    }

    @Test
    public void caseAndPaddingDoNotChangeTheAnswer() {
        // The value arrives from a JSON payload, so it is not under this app's
        // control and must be read leniently.
        assertEquals("taskAssigned", NotificationCategory.forType("  assignment_assigned "));
        assertEquals("taskCompleted", NotificationCategory.forType("Assignment_Approved"));
    }

    @Test
    public void anUnknownTypeIsNotGoverned() {
        assertEquals(null, NotificationCategory.forType("SOMETHING_NEW"));
    }

    @Test
    public void anAbsentTypeIsNotGoverned() {
        // Null has to be handled: the push payload's type field is optional.
        assertEquals(null, NotificationCategory.forType(null));
    }

    @Test
    public void anUngovernedTypeIsNeverSuppressed() {
        // The whole point of returning null rather than a category: a new
        // server-side type alerts by default instead of being dropped by a client
        // that has not been taught about it yet. Every category is off here, so
        // only the fail-open rule can be letting it through.
        assertFalse(NotificationCategory.isSuppressed(null, true, false, false, false));
        assertFalse(NotificationCategory.isSuppressed(null, true, true, true, true));
    }

    @Test
    public void theMasterSwitchSilencesEverything() {
        // Master off, every category on: the one setting allowed to stop
        // everything, which is what turning it off means.
        assertTrue(NotificationCategory.isSuppressed("ASSIGNMENT_ASSIGNED", false, true, true, true));
        assertTrue(NotificationCategory.isSuppressed("ASSIGNMENT_APPROVED", false, true, true, true));
        assertTrue(NotificationCategory.isSuppressed(null, false, true, true, true));
    }

    @Test
    public void aCategoryOffSilencesOnlyItsOwnTypes() {
        // Master on, assigned off, the rest on.
        assertTrue(NotificationCategory.isSuppressed(
                "ASSIGNMENT_ASSIGNED", true, false, true, true));
        assertFalse(NotificationCategory.isSuppressed(
                "ASSIGNMENT_APPROVED", true, false, true, true));
        assertFalse(NotificationCategory.isSuppressed(
                "ASSIGNMENT_UNASSIGNED", true, false, true, true));
    }

    @Test
    public void everythingOnLetsEverythingThrough() {
        assertFalse(NotificationCategory.isSuppressed(
                "ASSIGNMENT_ASSIGNED", true, true, true, true));
        assertFalse(NotificationCategory.isSuppressed(
                "ASSIGNMENT_REVISION_REQUESTED", true, true, true, true));
    }

    @Test
    public void turningTheRevisionCategoryOffSilencesBothItsTypes() {
        // Removal and revision share one toggle, so they must be silenced together.
        assertTrue(NotificationCategory.isSuppressed(
                "ASSIGNMENT_UNASSIGNED", true, true, true, false));
        assertTrue(NotificationCategory.isSuppressed(
                "ASSIGNMENT_REVISION_REQUESTED", true, true, true, false));
        assertFalse(NotificationCategory.isSuppressed(
                "ASSIGNMENT_APPROVED", true, true, true, false));
    }
}
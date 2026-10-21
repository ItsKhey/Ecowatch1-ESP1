package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.models.TaskReport;
import com.example.ecowatchpersonnel1.data.models.WasteScale;

import org.junit.Test;

/**
 * The waste scale a task carries, and the dispatch that goes with it.
 *
 * <p>A cleaner reads these to work out what to expect on site: whether they are
 * walking in alone, arriving as half of a crew, or arriving with a truck. The
 * values travel from the server, so the tests here pin down what the app does
 * with a value it does not recognise — it must never invent a size bigger than
 * the one that was dispatched.
 */
public class WasteScaleTest {

    @Test
    public void canonicalValuesAreUnchanged() {
        assertEquals(WasteScale.SMALL, WasteScale.canonical(WasteScale.SMALL));
        assertEquals(WasteScale.MEDIUM, WasteScale.canonical(WasteScale.MEDIUM));
        assertEquals(WasteScale.LARGE, WasteScale.canonical(WasteScale.LARGE));
    }

    @Test
    public void surroundingWhitespaceIsIgnored() {
        assertEquals(WasteScale.LARGE, WasteScale.canonical("  Large-Scale Waste  "));
    }

    @Test
    public void alternateSpellingsFoldOntoTheCanonicalVocabulary() {
        // The server normalises these, but an app and a server can be a release
        // or two apart, so the app folds them too rather than reading a crew
        // job as the smallest one.
        assertEquals(WasteScale.MEDIUM, WasteScale.canonical("medium"));
        assertEquals(WasteScale.MEDIUM, WasteScale.canonical("Medium-Scale Waste"));
        assertEquals(WasteScale.MEDIUM, WasteScale.canonical("medium_scale_waste"));
        assertEquals(WasteScale.LARGE, WasteScale.canonical("large"));
        assertEquals(WasteScale.LARGE, WasteScale.canonical("LARGE-SCALE WASTE"));
    }

    @Test
    public void aMissingScaleReadsAsTheSmallestOne() {
        // Reports filed before the field existed were all small jobs, so that is
        // the only safe reading of a payload that carries no scale.
        assertEquals(WasteScale.SMALL, WasteScale.canonical(null));
        assertEquals(WasteScale.SMALL, WasteScale.canonical(""));
        assertEquals(WasteScale.SMALL, WasteScale.canonical("   "));
    }

    @Test
    public void anUnknownScaleReadsAsTheSmallestOne() {
        // Anything unrecognised must not be read as a bigger job, which would
        // tell a cleaner to expect a crew and a truck that are not coming.
        assertEquals(WasteScale.SMALL, WasteScale.canonical("Gigantic-Scale Waste"));
    }

    @Test
    public void onlyTheBiggerScalesNeedACrew() {
        assertFalse(WasteScale.requiresTeam(WasteScale.SMALL));
        assertTrue(WasteScale.requiresTeam(WasteScale.MEDIUM));
        assertTrue(WasteScale.requiresTeam(WasteScale.LARGE));
    }

    @Test
    public void onlyTheLargestScaleNeedsAVehicle() {
        assertFalse(WasteScale.requiresVehicle(WasteScale.SMALL));
        assertFalse(WasteScale.requiresVehicle(WasteScale.MEDIUM));
        assertTrue(WasteScale.requiresVehicle(WasteScale.LARGE));
    }

    @Test
    public void taskPrefersItsOwnScaleOverTheReports() {
        PersonnelTask task = new PersonnelTask();
        task.wasteScale = WasteScale.LARGE;

        TaskReport report = new TaskReport();
        report.wasteScale = WasteScale.SMALL;
        task.report = report;

        assertEquals(WasteScale.LARGE, task.getWasteScale());
    }

    @Test
    public void taskFallsBackToTheReportsScale() {
        // The assignment records what was dispatched and the report records what
        // the resident filed. They are the same fact, but a payload can carry
        // only one of them.
        PersonnelTask task = new PersonnelTask();

        TaskReport report = new TaskReport();
        report.wasteScale = WasteScale.MEDIUM;
        task.report = report;

        assertEquals(WasteScale.MEDIUM, task.getWasteScale());
    }

    @Test
    public void taskWithoutAnyScaleReadsAsTheSmallestOne() {
        PersonnelTask task = new PersonnelTask();
        assertEquals(WasteScale.SMALL, task.getWasteScale());
    }

    @Test
    public void teamLabelPrefersTheNameOverTheId() {
        PersonnelTask task = new PersonnelTask();
        task.assignedTeamId = "TEAM-0001";
        task.assignedTeamName = "Northside Crew";

        assertEquals("Northside Crew", task.getAssignedTeamLabel());
    }

    @Test
    public void teamLabelFallsBackToTheIdWhenTheNameIsMissing() {
        // A team deleted after it was dispatched still has an id, and showing the
        // id is honest.
        PersonnelTask task = new PersonnelTask();
        task.assignedTeamId = "TEAM-0001";

        assertEquals("TEAM-0001", task.getAssignedTeamLabel());
    }

    @Test
    public void teamLabelIsNullWhenNoTeamWasDispatched() {
        assertNull(new PersonnelTask().getAssignedTeamLabel());
    }

    @Test
    public void vehicleLabelPrefersThePlateAndKeepsTheType() {
        PersonnelTask task = new PersonnelTask();
        task.assignedVehicleId = "VEH-0001";
        task.assignedVehiclePlateNumber = "ABC-123";
        task.assignedVehicleType = "Garbage Truck";

        assertEquals("ABC-123 (Garbage Truck)", task.getAssignedVehicleLabel());
    }

    @Test
    public void vehicleLabelFallsBackToTheTypeWhenThereIsNoPlate() {
        PersonnelTask task = new PersonnelTask();
        task.assignedVehicleId = "VEH-0001";
        task.assignedVehicleType = "Garbage Truck";

        assertEquals("Garbage Truck", task.getAssignedVehicleLabel());
    }

    @Test
    public void vehicleLabelIsNullWhenNoVehicleWasDispatched() {
        assertNull(new PersonnelTask().getAssignedVehicleLabel());
    }
}

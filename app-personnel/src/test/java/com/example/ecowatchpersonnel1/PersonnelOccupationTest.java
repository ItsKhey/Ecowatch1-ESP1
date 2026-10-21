package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ecowatchpersonnel1.data.models.Personnel;

import org.junit.Test;

/**
 * A cleaner either walks the route or drives one of the trucks.
 *
 * <p>The ESP never writes an occupation, so everything here is about reading one
 * correctly. Two things have to hold: a Regular must never be shown a vehicle,
 * because there is no truck to show, and a Driver must never be shown "—" for a
 * truck that is genuinely missing, because that reads as a bug rather than as
 * "nobody has assigned one yet".
 *
 * <p>The server sends both the canonical field and a shorter alias for the vehicle
 * ({@code assignedVehicleId} and {@code vehicleId}), so these also pin which
 * spelling wins when only one of the pair is present.
 */
public class PersonnelOccupationTest {

    /** Mirrors the driver the admin app creates with a truck attached. */
    private Personnel driver() {
        Personnel personnel = new Personnel();
        personnel.personnelId = "PERS-0007";
        personnel.occupation = "Driver";
        personnel.assignedVehicleId = "VEH-0007";
        personnel.assignedVehiclePlateNumber = "ABC-123";
        return personnel;
    }

    @Test
    public void aRegularCleanerIsNotADriver() {
        Personnel personnel = new Personnel();
        personnel.occupation = "Regular";

        assertFalse(personnel.isDriver());
        assertFalse(personnel.hasAssignedVehicle());
        assertNull(personnel.assignedVehicleId());
    }

    /**
     * An account stored before the occupation column existed arrives with nothing
     * here, and every such account is a cleaner. Reading it as a third unknown
     * occupation would leave the profile row blank for no reason.
     */
    @Test
    public void anAbsentOccupationReadsAsRegular() {
        Personnel personnel = new Personnel();
        personnel.personnelId = "PERS-0001";

        assertFalse(personnel.isDriver());
    }

    /**
     * Case is not a distinction the server guarantees, and a driver being shown
     * as a Regular because of capitalisation would be a lie about their job.
     */
    @Test
    public void occupationMatchingIgnoresCase() {
        Personnel personnel = new Personnel();
        personnel.occupation = "driver";

        assertTrue(personnel.isDriver());
    }

    /**
     * A driver hired before any truck was free is a real state: the account is a
     * Driver with nothing to show yet, which is what tells the administrator to go
     * and assign one.
     */
    @Test
    public void aDriverWithNoTruckIsStillADriver() {
        Personnel personnel = new Personnel();
        personnel.occupation = "Driver";

        assertTrue(personnel.isDriver());
        assertFalse(personnel.hasAssignedVehicle());
    }

    @Test
    public void aDriverKeepsTheTruckTheyWereGiven() {
        Personnel personnel = driver();

        assertTrue(personnel.isDriver());
        assertTrue(personnel.hasAssignedVehicle());
        assertEquals("VEH-0007", personnel.assignedVehicleId());
        assertEquals("ABC-123", personnel.assignedVehiclePlate());
    }

    /** The id is enough to claim a truck, even without the plate. */
    @Test
    public void aTruckWithoutAPlateStillCounts() {
        Personnel personnel = new Personnel();
        personnel.occupation = "Driver";
        personnel.assignedVehicleId = "VEH-0008";

        assertTrue(personnel.hasAssignedVehicle());
        assertNull(personnel.assignedVehiclePlate());
    }

    /**
     * A deployment that sends only the shorter spelling still paints the card, so
     * the two names on the wire cannot leave half the app showing a dash.
     */
    @Test
    public void theShorterVehicleSpellingIsAccepted() {
        Personnel personnel = new Personnel();
        personnel.occupationType = "Driver";
        personnel.vehicleId = "VEH-0009";
        personnel.vehiclePlateNumber = "XYZ-789";

        assertTrue(personnel.isDriver());
        assertTrue(personnel.hasAssignedVehicle());
        assertEquals("VEH-0009", personnel.assignedVehicleId());
        assertEquals("XYZ-789", personnel.assignedVehiclePlate());
    }

    /** A blank value is no vehicle, not a vehicle with an empty name. */
    @Test
    public void blankValuesAreTreatedAsAbsent() {
        Personnel personnel = new Personnel();
        personnel.occupation = "  ";
        personnel.assignedVehicleId = "   ";

        assertFalse(personnel.isDriver());
        assertFalse(personnel.hasAssignedVehicle());
        assertNull(personnel.assignedVehicleId());
        assertNull(personnel.assignedVehiclePlate());
    }
}
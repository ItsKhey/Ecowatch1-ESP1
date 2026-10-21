package com.example.ecowatchpersonnel1.data.models;

/**
 * The authenticated cleaner's account, as returned by
 * {@code POST /api/personnel/login} and {@code GET /api/personnel/me}.
 *
 * <p>Read-only on the client: personnel accounts are created by an administrator
 * and the app never edits them, so this mirrors the server payload without any
 * persistence of its own.
 */
public class Personnel {

    public String personnelId;
    public String serverId;
    public String firstName;
    public String lastName;
    public String fullName;
    public String username;
    public String email;
    public String gmail;
    public String profileImage;
    public String profileImageUrl;
    public String avatarUrl;
    public String role;
    public String status;
    public boolean online;
    public boolean cleaning;
    public boolean isActive;
    public boolean isDisabled;
    public boolean isDeleted;
    public String lastActive;
    public String phoneNumber;
    public String messengerLink;
    public String currentAssignmentText;
    public String currentAssignmentReportId;

    // What this account does, and the truck it drives when it is a Driver.
    //
    // Both are administrator-owned: an ESP can neither change them nor be given
    // one here, so the app only ever reads them. That is why there are no
    // OCCUPATION_* constants to go with these fields — nothing on this side of
    // the wire ever writes an occupation, so the server's vocabulary is never
    // this app's to reproduce. The UI asks isDriver() and picks a localised label.
    public String occupation;
    public String occupationType;
    public String assignedVehicleId;
    public String vehicleId;
    public String assignedVehiclePlateNumber;
    public String vehiclePlateNumber;

    /**
     * True when this cleaner drives one of the trucks.
     *
     * <p>A Regular cleaner walks the route; a Driver also drives one, which is
     * what makes an assigned vehicle part of the account rather than a separate
     * mapping the app would have to look up by hand.
     *
     * <p>Matched by substring rather than by equality because the server may spell
     * it "Driver" or "driver" depending on which column answered, and a cleaner
     * being told they are a Regular because of capitalisation would be a lie.
     */
    public boolean isDriver() {
        String value = firstNonBlank(occupation, occupationType);
        return value != null && value.toLowerCase().contains("driver");
    }

    /**
     * True when there is a truck to name on this account.
     *
     * <p>The id is enough: the plate is carried for display, but a server that
     * returns only the id still leaves the card something real to show.
     */
    public boolean hasAssignedVehicle() {
        return firstNonBlank(assignedVehicleId, vehicleId) != null;
    }

    /** The vehicle id under whichever spelling the server sent it. */
    public String assignedVehicleId() {
        return firstNonBlank(assignedVehicleId, vehicleId);
    }

    /** The plate under whichever spelling the server sent it, or null. */
    public String assignedVehiclePlate() {
        return firstNonBlank(assignedVehiclePlateNumber, vehiclePlateNumber);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    /** A stable label for headers, falling back to the username when unnamed. */
    public String displayName() {
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName.trim();
        }
        StringBuilder builder = new StringBuilder();
        if (firstName != null) builder.append(firstName.trim());
        if (lastName != null && !lastName.trim().isEmpty()) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(lastName.trim());
        }
        if (builder.length() > 0) {
            return builder.toString();
        }
        return username != null ? username : "Personnel";
    }

    /** True when the account is available to take work. */
    public boolean isAvailable() {
        return isActive && !isDeleted;
    }
}

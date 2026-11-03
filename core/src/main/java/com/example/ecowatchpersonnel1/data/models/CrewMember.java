package com.example.ecowatchpersonnel1.data.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * One member of the cleanup crew that was dispatched for a report.
 *
 * <p>A Medium- or Large-Scale Waste report is not handled by one person: a crew
 * turns out, and a truck goes with them. The resident is entitled to see who
 * turned out, so the server resolves the crew into this small record per
 * person rather than leaving the resident with an identifier.
 *
 * <p>Only the identity is carried. Nothing about a cleaner's account, status
 * or current assignment is shared with the resident.
 */
public class CrewMember implements Serializable {

    @SerializedName(value = "personnelId", alternate = {"personnel_id"})
    private String personnelId;

    /** The person's full name, e.g. "Juan Dela Cruz". */
    @SerializedName(value = "fullName", alternate = {"full_name"})
    private String fullName;

    /**
     * The crew member's picture, as a site relative path or an absolute URL.
     * Null when the account has no picture, in which case the card falls back
     * to the shipped placeholder.
     */
    @SerializedName(value = "profileImage", alternate = {"profile_image"})
    private String profileImage;

    /**
     * The plate of the truck this member is normally assigned to. Null when
     * the member has no vehicle of their own; the truck dispatched for the
     * report is carried on the report itself, not on the member.
     */
    @SerializedName(value = "assignedVehiclePlateNumber",
            alternate = {"assigned_vehicle_plate_number"})
    private String assignedVehiclePlateNumber;

    @SerializedName(value = "isTeamLeader", alternate = {
            "is_team_leader", "teamLeader", "team_leader", "leader", "isLeader", "is_leader"
    })
    private boolean teamLeader;

    @SerializedName(value = "role", alternate = {"roleName", "position"})
    private String role;

    public CrewMember() {
    }

    public CrewMember(String personnelId, String fullName, String profileImage,
                      String assignedVehiclePlateNumber) {
        this.personnelId = personnelId;
        this.fullName = fullName;
        this.profileImage = profileImage;
        this.assignedVehiclePlateNumber = assignedVehiclePlateNumber;
    }

    public String getPersonnelId() { return personnelId; }
    public void setPersonnelId(String personnelId) { this.personnelId = personnelId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getAssignedVehiclePlateNumber() { return assignedVehiclePlateNumber; }
    public void setAssignedVehiclePlateNumber(String assignedVehiclePlateNumber) {
        this.assignedVehiclePlateNumber = assignedVehiclePlateNumber;
    }

    public boolean isTeamLeader() {
        if (teamLeader) return true;
        if (role != null && role.toLowerCase().contains("leader")) return true;
        return false;
    }

    public void setTeamLeader(boolean teamLeader) {
        this.teamLeader = teamLeader;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    /**
     * The name to print on the card.
     *
     * <p>Falls back to the identifier when the server sent a crew without
     * names, so a card is never blank.
     */
    public String getDisplayName() {
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName.trim();
        }
        if (personnelId != null && !personnelId.trim().isEmpty()) {
            return personnelId.trim();
        }
        return "Crew member";
    }
}


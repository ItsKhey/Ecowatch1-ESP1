package com.example.myecowatch1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * The notification toggles as the server stores them.
 *
 * <p>Every field defaults to {@code true} so a client that has never spoken to
 * the backend behaves like a resident who has never touched their settings, and
 * an absent value can never be read as "off".
 */
public class NotificationPreferences {

    @SerializedName("masterEnabled")
    private boolean masterEnabled = true;

    @SerializedName("reportStatusUpdates")
    private boolean reportStatusUpdates = true;

    @SerializedName("reportCompleted")
    private boolean reportCompleted = true;

    @SerializedName("reportDenied")
    private boolean reportDenied = true;

    @SerializedName("cenroAnnouncements")
    private boolean cenroAnnouncements = true;

    @SerializedName("securityAlerts")
    private boolean securityAlerts = true;

    @SerializedName("soundEnabled")
    private boolean soundEnabled = true;

    @SerializedName("vibrationEnabled")
    private boolean vibrationEnabled = true;

    /**
     * Custom notification ringtone. Null means the device default.
     *
     * <p>Previously device-local, so a chosen sound was lost on reinstall and
     * could differ across devices on one account.
     */
    @SerializedName("soundUri")
    private String soundUri;

    public boolean isMasterEnabled() { return masterEnabled; }
    public void setMasterEnabled(boolean value) { this.masterEnabled = value; }

    public boolean isReportStatusUpdates() { return reportStatusUpdates; }
    public void setReportStatusUpdates(boolean value) { this.reportStatusUpdates = value; }

    public boolean isReportCompleted() { return reportCompleted; }
    public void setReportCompleted(boolean value) { this.reportCompleted = value; }

    public boolean isReportDenied() { return reportDenied; }
    public void setReportDenied(boolean value) { this.reportDenied = value; }

    public boolean isCenroAnnouncements() { return cenroAnnouncements; }
    public void setCenroAnnouncements(boolean value) { this.cenroAnnouncements = value; }

    public boolean isSecurityAlerts() { return securityAlerts; }
    public void setSecurityAlerts(boolean value) { this.securityAlerts = value; }

    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean value) { this.soundEnabled = value; }

    public boolean isVibrationEnabled() { return vibrationEnabled; }
    public void setVibrationEnabled(boolean value) { this.vibrationEnabled = value; }

    public String getSoundUri() { return soundUri; }
    public void setSoundUri(String value) { this.soundUri = value; }
}

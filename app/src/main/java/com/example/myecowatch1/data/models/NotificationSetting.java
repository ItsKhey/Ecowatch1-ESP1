package com.example.myecowatch1.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notification_settings")
public class NotificationSetting {
    @PrimaryKey
    @NonNull
    private String residentId;

    private boolean reportStatusUpdates = true;
    private boolean reportCompleted = true;
    private boolean reportDenied = true;
    private boolean cenroAnnouncements = true;
    private boolean securityAlerts = true;
    private boolean soundEnabled = true;
    private String soundUri = ""; // Path to custom sound
    private boolean vibrationEnabled = true;
    private boolean masterEnabled = false;

    public NotificationSetting(@NonNull String residentId) {
        this.residentId = residentId;
    }

    @NonNull
    public String getResidentId() { return residentId; }
    public void setResidentId(@NonNull String residentId) { this.residentId = residentId; }

    public boolean isMasterEnabled() { return masterEnabled; }
    public void setMasterEnabled(boolean masterEnabled) { this.masterEnabled = masterEnabled; }

    public boolean isReportStatusUpdates() { return reportStatusUpdates; }
    public void setReportStatusUpdates(boolean reportStatusUpdates) { this.reportStatusUpdates = reportStatusUpdates; }

    public boolean isReportCompleted() { return reportCompleted; }
    public void setReportCompleted(boolean reportCompleted) { this.reportCompleted = reportCompleted; }

    public boolean isReportDenied() { return reportDenied; }
    public void setReportDenied(boolean reportDenied) { this.reportDenied = reportDenied; }

    public boolean isCenroAnnouncements() { return cenroAnnouncements; }
    public void setCenroAnnouncements(boolean cenroAnnouncements) { this.cenroAnnouncements = cenroAnnouncements; }

    public boolean isSecurityAlerts() { return securityAlerts; }
    public void setSecurityAlerts(boolean securityAlerts) { this.securityAlerts = securityAlerts; }

    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }

    public String getSoundUri() { return soundUri; }
    public void setSoundUri(String soundUri) { this.soundUri = soundUri; }

    public boolean isVibrationEnabled() { return vibrationEnabled; }
    public void setVibrationEnabled(boolean vibrationEnabled) { this.vibrationEnabled = vibrationEnabled; }
}

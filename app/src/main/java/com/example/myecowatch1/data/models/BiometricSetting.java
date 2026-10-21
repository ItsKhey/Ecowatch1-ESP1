package com.example.myecowatch1.data.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "biometric_settings")
public class BiometricSetting {
    @PrimaryKey
    @NonNull
    private String residentId;
    
    private String savedEmail;
    private String savedUsername;
    private boolean isEnabled; // Legacy/General
    private boolean isFingerprintEnabled;
    private boolean isFaceIDEnabled;
    private String savedPassword;

    public BiometricSetting(@NonNull String residentId) {
        this.residentId = residentId;
        this.isEnabled = false;
        this.isFingerprintEnabled = false;
        this.isFaceIDEnabled = false;
        this.savedEmail = "";
        this.savedUsername = "";
        this.savedPassword = "";
    }

    @NonNull
    public String getResidentId() { return residentId; }
    public void setResidentId(@NonNull String residentId) { this.residentId = residentId; }

    public String getSavedEmail() { return savedEmail; }
    public void setSavedEmail(String savedEmail) { this.savedEmail = savedEmail; }

    public String getSavedUsername() { return savedUsername; }
    public void setSavedUsername(String savedUsername) { this.savedUsername = savedUsername; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public boolean isFingerprintEnabled() { return isFingerprintEnabled; }
    public void setFingerprintEnabled(boolean fingerprintEnabled) { isFingerprintEnabled = fingerprintEnabled; }

    public boolean isFaceIDEnabled() { return isFaceIDEnabled; }
    public void setFaceIDEnabled(boolean faceIDEnabled) { isFaceIDEnabled = faceIDEnabled; }

    public String getSavedPassword() { return savedPassword; }
    public void setSavedPassword(String savedPassword) { this.savedPassword = savedPassword; }
}

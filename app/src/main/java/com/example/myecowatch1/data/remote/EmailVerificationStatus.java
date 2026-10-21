package com.example.myecowatch1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Server response for {@code GET auth/check-verification}.
 *
 * <p>The backend creates the resident account on the transition to verified, so
 * a {@code verified} of true also carries the new account's identifier. That is
 * what lets the app send the resident straight to the login screen with the
 * username already filled in.
 */
public class EmailVerificationStatus {

    @SerializedName("success")
    private boolean success;

    @SerializedName("verified")
    private boolean verified;

    /** True when the account was created by this very call, false when a previous call already had. */
    @SerializedName("created")
    private boolean created;

    /**
     * True when the account already existed because an earlier check finalized it.
     * This is the expected result after a rotation or a dropped response, so the
     * UI treats it as success rather than as an error.
     */
    @SerializedName("alreadyFinalized")
    private boolean alreadyFinalized;

    @SerializedName("residentId")
    private String residentId;

    @SerializedName("username")
    private String username;

    /** Only meaningful while {@code verified} is false. */
    @SerializedName("canResend")
    private boolean canResend;

    /** Seconds the resident must wait before another link can be requested. */
    @SerializedName("resendAvailableInSeconds")
    private int resendAvailableInSeconds;

    @SerializedName("message")
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isCreated() {
        return created;
    }

    public boolean isAlreadyFinalized() {
        return alreadyFinalized;
    }

    public String getResidentId() {
        return residentId == null ? "" : residentId;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public boolean canResend() {
        return canResend;
    }

    public int getResendAvailableInSeconds() {
        return resendAvailableInSeconds;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }
}

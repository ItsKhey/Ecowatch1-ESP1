package com.example.myecowatch1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Server response for {@code POST auth/reset-password} on the Firebase path.
 *
 * <p>{@code username} comes back so the app can prefill the login form. The
 * resident still signs in with their username and the new password; this is
 * convenience, not a second credential.
 */
public class PasswordResetResult {

    @SerializedName("success")
    private boolean success;

    @SerializedName("username")
    private String username;

    /**
     * False only when the EcoWatch password was changed but the Firebase identity
     * could not be updated. The reset still succeeded and the new password
     * already works, so this is reported as a warning rather than a failure.
     */
    @SerializedName("firebaseSynced")
    private boolean firebaseSynced;

    @SerializedName("warning")
    private String warning;

    @SerializedName("message")
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public String getUsername() {
        return username == null ? "" : username;
    }

    public boolean isFirebaseSynced() {
        return firebaseSynced;
    }

    public String getWarning() {
        return warning == null ? "" : warning;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }
}

package com.example.myecowatch1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Envelope for {@code GET/PUT /api/notifications/preferences}.
 *
 * <p>The backend answers with the authoritative set after a write, so the client
 * reconciles against the response rather than assuming its own values stuck.
 */
public class NotificationPreferencesResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("preferences")
    private NotificationPreferences preferences;

    public boolean isSuccess() {
        return success;
    }

    /**
     * The stored choices. Never null: a response that omitted the field falls
     * back to an all-enabled set, matching the backend's fail-open default.
     */
    public NotificationPreferences getPreferences() {
        return preferences == null ? new NotificationPreferences() : preferences;
    }
}

package com.example.myecowatch1.data.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Body for {@code PUT /api/notifications/preferences}.
 *
 * <p>Only the toggles the user actually changed are sent, so a client that
 * tracks one category cannot reset the others to a stale value. The backend
 * merges unspecified fields with what is already stored.
 */
public class NotificationPreferencesRequest {

    @SerializedName("masterEnabled")
    private Boolean masterEnabled;

    @SerializedName("reportStatusUpdates")
    private Boolean reportStatusUpdates;

    @SerializedName("reportCompleted")
    private Boolean reportCompleted;

    @SerializedName("reportDenied")
    private Boolean reportDenied;

    @SerializedName("cenroAnnouncements")
    private Boolean cenroAnnouncements;

    @SerializedName("securityAlerts")
    private Boolean securityAlerts;

    @SerializedName("soundEnabled")
    private Boolean soundEnabled;

    @SerializedName("vibrationEnabled")
    private Boolean vibrationEnabled;

    @SerializedName("soundUri")
    private String soundUri;

    private NotificationPreferencesRequest() {
    }

    /** Builds a request carrying a single changed toggle. */
    public static NotificationPreferencesRequest forCenroAnnouncements(boolean enabled) {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest();
        request.cenroAnnouncements = enabled;
        return request;
    }

    public static NotificationPreferencesRequest forMasterEnabled(boolean enabled) {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest();
        request.masterEnabled = enabled;
        return request;
    }

    public static NotificationPreferencesRequest forReportStatusUpdates(boolean enabled) {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest();
        request.reportStatusUpdates = enabled;
        return request;
    }

    public static NotificationPreferencesRequest forReportCompleted(boolean enabled) {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest();
        request.reportCompleted = enabled;
        return request;
    }

    public static NotificationPreferencesRequest forReportDenied(boolean enabled) {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest();
        request.reportDenied = enabled;
        return request;
    }

    public static NotificationPreferencesRequest forSecurityAlerts(boolean enabled) {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest();
        request.securityAlerts = enabled;
        return request;
    }

    /**
     * Builds a request carrying only the chosen ringtone.
     *
     * <p>A null or blank value clears it, which is how a resident returns to the
     * device default.
     */
    public static NotificationPreferencesRequest forSoundUri(String uri) {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest();
        request.soundUri = (uri == null || uri.trim().isEmpty()) ? null : uri.trim();
        return request;
    }
}

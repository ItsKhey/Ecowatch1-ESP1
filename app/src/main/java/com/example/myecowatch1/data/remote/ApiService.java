package com.example.myecowatch1.data.remote;

import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.models.History;
import com.example.myecowatch1.data.models.Notification;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.PATCH;
import retrofit2.http.Part;
import retrofit2.http.PartMap;

import java.util.List;

public interface ApiService {

    /**
     * Registers a resident account and sends a Firebase verification link.
     *
     * <p>{@code verificationMethod = "EMAIL_LINK"} is what tells the backend to
     * use the email-link flow. Without it the backend serves the legacy
     * 4-digit OTP flow, which is still what the Admin app uses.
     */
    @POST("auth/register")
    Call<Map<String, Object>> register(@Body Map<String, String> body);

    @POST("auth/verify-otp")
    Call<Map<String, Object>> verifyOTP(@Body Map<String, String> body);

    /**
     * Confirms a registration using the Firebase session obtained after the
     * resident opened their verification email.
     *
     * <p>POST, not GET, because the body carries a Firebase ID token. That token
     * is proof about one email address and nothing else, so it must never be
     * placed in a URL where it could be logged or cached.
     *
     * <p>The backend creates the resident account only when Firebase confirms the
     * address behind this token was actually verified.
     */
    @POST("auth/check-verification")
    Call<EmailVerificationStatus> checkVerification(@Body Map<String, String> body);

    @POST("auth/forgot-password")
    Call<Map<String, Object>> forgotPassword(@Body Map<String, String> body);

    @POST("auth/verify-reset-otp")
    Call<Map<String, Object>> verifyResetOTP(@Body Map<String, String> body);

    /**
     * Sets a new password from a Firebase reset link.
     *
     * <p>Present only to keep the legacy path reachable from this interface. The
     * Firebase flow is chosen by sending {@code oobCode} instead of {@code otp},
     * which is why both share one backend route.
     */
    @POST("auth/reset-password")
    Call<Map<String, Object>> resetPassword(@Body Map<String, String> body);

    /**
     * The same route, typed for the Firebase flow.
     *
     * <p>Retrofit builds a separate call for each interface method, so declaring
     * the route twice is safe: the legacy {@link #resetPassword} keeps its
     * untyped signature for the OTP code path while this one parses the Firebase
     * response. The backend picks the branch by the presence of {@code oobCode}.
     */
    @POST("auth/reset-password")
    Call<PasswordResetResult> resetPasswordWithFirebase(@Body Map<String, String> body);

    @POST("auth/login")
    Call<Map<String, Object>> login(@Body Map<String, String> body);

    @GET("auth/me")
    Call<Map<String, Object>> getProfile();

    @PATCH("auth/profile")
    Call<Map<String, Object>> updateProfile(@Body Map<String, String> body);

    @POST("auth/change-password")
    Call<Map<String, Object>> changePassword(@Body Map<String, String> body);

    @PATCH("auth/reset-logged-in")
    Call<Map<String, Object>> resetLoggedInPassword(@Body Map<String, String> body);

    @Multipart
    @PATCH("auth/profile/image")
    Call<Map<String, Object>> updateProfileImage(@Part MultipartBody.Part image);

    /**
     * Pauses the authenticated resident account. The backend preserves the
     * profile, reports, history and settings, and restores the same account on
     * the next successful login.
     */
    @POST("auth/disable-account")
    Call<Map<String, Object>> disableAccount();

    /** Permanently deletes the authenticated resident account and its data. */
    @retrofit2.http.DELETE("auth/account")
    Call<Map<String, Object>> deleteAccount();

    @GET("reports/my-reports")
    Call<List<Report>> getMyReports();

    @Multipart
    @POST("reports")
    Call<Map<String, Object>> createReport(
            @Part MultipartBody.Part image,
            @PartMap Map<String, RequestBody> fields
    );

    /**
     * Creates a report with multiple photos. Photos are sent as a multipart
     * array under the field name "photos". Falls back to the legacy single
     * "image" field when only one photo is provided.
     */
    @Multipart
    @POST("reports")
    Call<Map<String, Object>> createReportMultiple(
            @Part List<MultipartBody.Part> photos,
            @PartMap Map<String, RequestBody> fields
    );

    @retrofit2.http.DELETE("reports/{id}")
    Call<Map<String, Object>> deleteReport(@retrofit2.http.Path("id") String reportId);

    @Multipart
    @PATCH("reports/{id}")
    Call<Report> updateReport(
            @retrofit2.http.Path("id") String reportId,
            @Part MultipartBody.Part image,
            @PartMap Map<String, RequestBody> fields
    );

    @POST("reports/{id}/move-to-archive")
    Call<Map<String, Object>> moveToArchive(@retrofit2.http.Path("id") String reportId);

    @POST("reports/{id}/move-to-trash")
    Call<Map<String, Object>> moveToTrash(@retrofit2.http.Path("id") String reportId);

    @POST("reports/{id}/restore")
    Call<Map<String, Object>> restoreFromCollection(@retrofit2.http.Path("id") String reportId);

    @POST("reports/bulk-move-to-archive")
    Call<Map<String, Object>> bulkMoveToArchive(@Body Map<String, List<String>> body);

    @POST("reports/bulk-move-to-trash")
    Call<Map<String, Object>> bulkMoveToTrash(@Body Map<String, List<String>> body);

    @POST("reports/bulk-restore")
    Call<Map<String, Object>> bulkRestore(@Body Map<String, List<String>> body);

    @POST("reports/bulk-delete")
    Call<Map<String, Object>> bulkDelete(@Body Map<String, List<String>> body);

    /**
     * Announcements addressed to the signed-in resident. The audience is
     * resolved by the backend, so the client never decides who may read what.
     *
     * <p>Returns the backend's wrapper object rather than a bare list, matching
     * {@link AnnouncementFeedResponse}.
     */
    @GET("announcements/my-announcements")
    Call<AnnouncementFeedResponse> getMyAnnouncements();

    @GET("history/my-history")
    Call<List<History>> getMyHistory();

    @POST("history/bulk-soft-delete")
    Call<Map<String, Object>> bulkSoftDeleteHistory(@Body Map<String, List<String>> body);

    @POST("history/bulk-restore")
    Call<Map<String, Object>> bulkRestoreHistory(@Body Map<String, List<String>> body);

    @POST("history/bulk-delete")
    Call<Map<String, Object>> bulkDeleteHistory(@Body Map<String, List<String>> body);

    @GET("notifications/my-notifications")
    Call<List<Notification>> getMyNotifications();

    /**
     * Server-owned notification choices.
     *
     * <p>Read on launch so a reinstall or a second device picks up the same
     * choices, and written on every change so the backend can honour them when
     * it decides who to push to.
     */
    @GET("notifications/preferences")
    Call<NotificationPreferencesResponse> getNotificationPreferences();

    @PUT("notifications/preferences")
    Call<NotificationPreferencesResponse> updateNotificationPreferences(
            @Body NotificationPreferencesRequest preferences);

    @GET("notifications/trash")
    Call<List<Notification>> getMyTrashNotifications();

    @GET("notifications/unread-count")
    Call<UnreadCountResponse> getUnreadCount();

    @PATCH("notifications/{id}/read")
    Call<Map<String, Object>> markNotificationAsRead(
            @retrofit2.http.Path("id") String notificationId
    );

    @PATCH("notifications/mark-as-read")
    Call<Map<String, Object>> markAllNotificationsAsRead();

    @PUT("notifications/fcm-token")
    Call<Map<String, Object>> updateFcmToken(
            @Header("Authorization") String authorization,
            @Body FcmTokenRequest body
    );

    @retrofit2.http.DELETE("notifications/fcm-token")
    Call<Void> clearFcmToken(@Header("Authorization") String authorization);

    @POST("notifications/{id}/move-to-trash")
    Call<Map<String, Object>> moveNotificationToTrash(@retrofit2.http.Path("id") String mongoId);

    @POST("notifications/{id}/restore")
    Call<Map<String, Object>> restoreNotification(@retrofit2.http.Path("id") String mongoId);

    @retrofit2.http.DELETE("notifications/{id}")
    Call<Map<String, Object>> deleteNotification(@retrofit2.http.Path("id") String mongoId);
}

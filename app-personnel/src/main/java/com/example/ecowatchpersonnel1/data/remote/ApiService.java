package com.example.ecowatchpersonnel1.data.remote;

import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * The personnel half of the EcoWatch API.
 *
 * <p>Only the routes a cleaner is authorized for are declared here. Personnel
 * authentication is separate from the resident and admin flows: there is no
 * registration route, because accounts are created by an administrator, and the
 * identity is always derived server-side from the bearer token.
 */
public interface ApiService {

    /** Public. Exchanges a username (or gmail) and password for a personnel JWT. */
    @POST("personnel/login")
    Call<LoginResponse> login(@Body Map<String, String> body);

    @GET("personnel/me")
    Call<PersonnelResponse> getMe();

    /**
     * Checks the cleaner's current password without changing anything.
     *
     * <p>Called before registering a fingerprint, so enrolling a biometric
     * requires proving the password today rather than merely holding an unlocked
     * phone.
     */
    @POST("personnel/verify-password")
    Call<Map<String, Object>> verifyPassword(@Body Map<String, String> body);

    /**
     * Changes the signed-in cleaner's own password.
     *
     * <p>Separate from the administrator's reset route on purpose: this one
     * authenticates with the current password, so a shared phone with an open
     * session cannot be used to take the account over.
     */
    @PATCH("personnel/password")
    Call<Map<String, Object>> changePassword(@Body Map<String, String> body);

    @GET("personnel/tasks/current")
    Call<TaskResponse> getCurrentTask();

    /**
     * Server-counted task totals for the signed-in cleaner.
     *
     * <p>Counted in SQL from the assignment rows rather than in the app from the
     * task list, because the list is paged: a cleaner with more tasks than one
     * page holds would have their completed count under-reported. The admin app
     * reads the same numbers for the same cleaner, so the two agree.
     */
    @GET("personnel/task-counts")
    Call<TaskCountsResponse> getTaskCounts();

    @GET("personnel/tasks")
    Call<TaskListResponse> listTasks(
            @Query("status") String status,
            @Query("page") int page,
            @Query("limit") int limit
    );

    @GET("personnel/tasks/{id}")
    Call<TaskResponse> getTask(@Path("id") String assignmentId);

    @POST("personnel/tasks/{id}/accept")
    Call<TaskResponse> acceptTask(@Path("id") String assignmentId);

    @POST("personnel/tasks/{id}/start")
    Call<TaskResponse> startTask(@Path("id") String assignmentId);

    @POST("personnel/tasks/{id}/complete")
    Call<TaskResponse> completeTask(
            @Path("id") String assignmentId,
            @Body Map<String, String> body
    );

    /**
     * Uploads one BEFORE or AFTER photo. The phase travels as a plain multipart
     * field so the server can reject a phase the task's status does not allow.
     */
    @Multipart
    @POST("personnel/tasks/{id}/evidence")
    Call<EvidenceResponse> uploadEvidence(
            @Path("id") String assignmentId,
            @Part("phase") RequestBody phase,
            @Part MultipartBody.Part image
    );

    @DELETE("personnel/tasks/{id}/evidence/{evidenceId}")
    Call<TaskResponse> deleteEvidence(
            @Path("id") String assignmentId,
            @Path("evidenceId") String evidenceId
    );

    @GET("personnel/notifications")
    Call<PersonnelNotificationsResponse> getNotifications(@Query("limit") int limit);

    @GET("personnel/notifications/unread-count")
    Call<UnreadCountResponse> getUnreadCount();

    @PATCH("personnel/notifications/{id}/read")
    Call<Map<String, Object>> markNotificationAsRead(@Path("id") String notificationId);

    @PATCH("personnel/notifications/read-all")
    Call<Map<String, Object>> markAllNotificationsAsRead();

    @DELETE("personnel/notifications/{id}")
    Call<Map<String, Object>> deleteNotification(@Path("id") String notificationId);

    /** Binds this device to the authenticated personnel account. */
    @POST("personnel/fcm-token")
    Call<Map<String, Object>> registerFcmToken(@Body FcmTokenRequest body);

    @DELETE("personnel/fcm-token")
    Call<Void> clearFcmToken();
}

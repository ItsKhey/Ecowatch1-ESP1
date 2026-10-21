package com.example.myecowatch1.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.myecowatch1.data.local.AppDatabase;
import com.example.myecowatch1.data.local.BiometricDao;
import com.example.myecowatch1.data.local.ReportDao;
import com.example.myecowatch1.data.local.UserDao;
import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.remote.ApiService;
import com.example.myecowatch1.data.remote.EmailVerificationStatus;
import com.example.myecowatch1.data.remote.PasswordResetPreparation;
import com.example.myecowatch1.data.remote.PasswordResetResult;
import com.example.myecowatch1.data.remote.RetrofitClient;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private UserDao userDao;
    private ReportDao reportDao;
    private BiometricDao biometricDao;
    private ApiService apiService;
    private SessionManager sessionManager;
    private NotificationRepository notificationRepository;
    private ExecutorService executorService;

    public AuthRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        userDao = db.userDao();
        reportDao = db.reportDao();
        biometricDao = db.biometricDao();
        apiService = RetrofitClient.getApiService(application);
        sessionManager = new SessionManager(application);
        notificationRepository = NotificationRepository.getInstance(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    private String getErrorMessage(Response<?> response, String defaultMsg) {
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                JSONObject jsonObject = new JSONObject(errorBody.string());
                if (jsonObject.has("message")) {
                    return jsonObject.getString("message");
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return defaultMsg + ": " + response.message();
    }

    private String getStringFromMap(Map<String, Object> map, String primaryKey, String alternateKey) {
        if (map == null) return "";
        Object val = map.get(primaryKey);
        if (val != null) return val.toString();
        if (alternateKey != null) {
            val = map.get(alternateKey);
            if (val != null) return val.toString();
        }
        return "";
    }

    public LiveData<User> getUser() {
        return userDao.getUser();
    }

    /**
     * Outcome of a registration, which the server decides.
     *
     * <p>With verification turned off the account exists as soon as the request
     * succeeds, so there is no registrationId and no code to type. With
     * verification on, the account is staged behind a registrationId and the
     * emailed code still has to be entered.
     */
    public static final class RegistrationOutcome {
        private final String registrationId;
        private final boolean requiresVerification;

        RegistrationOutcome(String registrationId, boolean requiresVerification) {
            this.registrationId = registrationId;
            this.requiresVerification = requiresVerification;
        }

        /** Non-null only while the account is still pending verification. */
        public String getRegistrationId() {
            return registrationId;
        }

        public boolean requiresVerification() {
            return requiresVerification;
        }
    }

    /**
     * Registers a resident.
     *
     * <p>Which transport runs is decided by the build and by the server policy,
     * so the response is inspected rather than assumed. A request carrying
     * {@code verificationMethod} selects a transport explicitly; without one the
     * server may create the account outright.
     *
     * <p>When a code is required the account is uncreated until it is verified, so
     * the resident may leave the app to open their mail.
     */
    public void register(String firstName, String lastName, String username, String email, String phoneNumber, String password, ApiCallback<RegistrationOutcome> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("username", username);
        body.put("email", email);
        // Required by the server on every registration transport, so it is sent
        // whichever flow this build is pinned to.
        body.put("phoneNumber", phoneNumber);
        body.put("password", password);
        if (!ServerConfig.USES_LOCAL_OTP_REGISTRATION) {
            // Opts this request into the email-link transport. Omitting it lets the
            // server apply its own policy.
            body.put("verificationMethod", "EMAIL_LINK");
        }

        apiService.register(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String registrationId = (String) response.body().get("registrationId");
                    boolean requiresVerification = Boolean.TRUE.equals(
                            response.body().get("requiresVerification"));
                    callback.onSuccess(new RegistrationOutcome(registrationId, requiresVerification));
                } else {
                    callback.onError(getErrorMessage(response, "Registration failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error occurred.");
            }
        });
    }

    public void verifyOTP(String registrationId, String otp, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("registrationId", registrationId);
        body.put("otp", otp);

        apiService.verifyOTP(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("residentId"));
                } else {
                    callback.onError(getErrorMessage(response, "Verification failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error occurred.");
            }
        });
    }

    // =======================================================================
    // Firebase email verification + password reset
    //
    // None of these sign the resident in. The username + password login above
    // is the only way into the app, and it is untouched.
    // =======================================================================

    /**
     * Confirms a registration with the backend using the Firebase session the app
     * holds after the resident opened their verification email.
     *
     * <p>The token proves one thing only: that the Firebase account behind it
     * belongs to a verified email address. It is never used to log in, and the
     * resident still signs in on the ordinary username + password screen
     * afterwards.
     */
    public void checkEmailVerification(String registrationId, String idToken,
                                       ApiCallback<EmailVerificationStatus> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("registrationId", registrationId);
        body.put("idToken", idToken);

        apiService.checkVerification(body)
                .enqueue(new Callback<EmailVerificationStatus>() {
                    @Override
                    public void onResponse(Call<EmailVerificationStatus> call,
                                           Response<EmailVerificationStatus> response) {
                        EmailVerificationStatus status = response.body();

                        if (response.isSuccessful() && status != null && status.isSuccess()) {
                            callback.onSuccess(status);
                        } else {
                            callback.onError(getErrorMessage(response,
                                    "Could not confirm your email verification"));
                        }
                    }

                    @Override
                    public void onFailure(Call<EmailVerificationStatus> call, Throwable t) {
                        callback.onError(t.getMessage() != null
                                ? t.getMessage()
                                : "Network error occurred.");
                    }
                });
    }

    /**
     * Asks the backend to prepare a password reset.
     *
     * <p>This does not send anything. It makes sure a Firebase identity exists for
     * the address, because Firebase will not send a reset email for an address it
     * has never seen, which is the case for accounts that predate the Firebase
     * integration. The caller then asks Firebase to send the email itself.
     *
     * <p>The response never reveals whether the address is registered, so it
     * cannot be used to discover which addresses have an EcoWatch account. The
     * {@code canRequestFirebaseEmail} flag tells the app whether to attempt the
     * send without the message giving anything away.
     */
    public void preparePasswordReset(String email, ApiCallback<PasswordResetPreparation> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);

        apiService.forgotPassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> responseBody = response.body();

                if (response.isSuccessful() && responseBody != null) {
                    Object can = responseBody.get("canRequestFirebaseEmail");
                    int retry = 0;
                    Object retryValue = responseBody.get("retryAfterSeconds");
                    if (retryValue instanceof Number) {
                        retry = ((Number) retryValue).intValue();
                    }

                    callback.onSuccess(new PasswordResetPreparation(
                            can instanceof Boolean && (Boolean) can,
                            retry,
                            getStringFromMap(responseBody, "message", null)));
                } else {
                    callback.onError(getErrorMessage(response, "Could not start the password reset"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error occurred.");
            }
        });
    }

    /**
     * Sets the new password using the {@code oobCode} from the emailed link.
     *
     * <p>The oobCode is the only credential sent. The backend validates it against
     * Firebase, works out which account it belongs to, and writes the new bcrypt
     * hash. This call never authenticates the resident.
     */
    public void resetPasswordWithFirebase(String oobCode, String newPassword,
                                          ApiCallback<PasswordResetResult> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("oobCode", oobCode);
        body.put("password", newPassword);

        apiService.resetPasswordWithFirebase(body)
                .enqueue(new Callback<PasswordResetResult>() {
                    @Override
                    public void onResponse(Call<PasswordResetResult> call,
                                           Response<PasswordResetResult> response) {
                        PasswordResetResult result = response.body();

                        if (response.isSuccessful() && result != null && result.isSuccess()) {
                            callback.onSuccess(result);
                        } else {
                            callback.onError(getErrorMessage(response, "Password reset failed"));
                        }
                    }

                    @Override
                    public void onFailure(Call<PasswordResetResult> call, Throwable t) {
                        callback.onError(t.getMessage() != null ? t.getMessage() : "Network error occurred.");
                    }
                });
    }

    public void forgotPassword(String username, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        
        apiService.forgotPassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Failed to send reset code"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void verifyResetOTP(String username, String otp, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("otp", otp);

        apiService.verifyResetOTP(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "OTP verification failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void resetPassword(String username, String otp, String newPassword, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("otp", otp);
        body.put("password", newPassword);

        apiService.resetPassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Password reset failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void changePassword(String oldPassword, String newPassword, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("oldPassword", oldPassword);
        body.put("newPassword", newPassword);

        apiService.changePassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Password update failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void resetLoggedInPassword(String newPassword, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("password", newPassword);

        apiService.resetLoggedInPassword(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess((String) response.body().get("message"));
                } else {
                    callback.onError(getErrorMessage(response, "Password reset failed"));
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void login(String username, String password, ApiCallback<User> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);

        apiService.login(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> responseBody = response.body();
                Object residentValue = responseBody == null ? null : responseBody.get("resident");
                Map<String, Object> resData = residentValue instanceof Map
                        ? (Map<String, Object>) residentValue
                        : null;
                String token = getStringFromMap(responseBody, "token", null);
                String residentId = getStringFromMap(resData, "residentId", "resident_id");

                if (!response.isSuccessful() || responseBody == null
                        || token.isEmpty() || residentId.isEmpty() || resData == null) {
                    callback.onError(getErrorMessage(response, "Login failed"));
                    return;
                }

                User user = new User(
                        residentId,
                        getStringFromMap(resData, "firstName", "first_name"),
                        getStringFromMap(resData, "lastName", "last_name"),
                        getStringFromMap(resData, "username", null),
                        getStringFromMap(resData, "email", null),
                        getStringFromMap(resData, "address", null),
                        getStringFromMap(resData, "profileImage", "profile_image")
                );
                applyContactFields(user, resData);

                // Persist the token and resident ID as one session write before
                // exposing success, so the first protected request cannot see a
                // half-updated account.
                sessionManager.saveSession(token, residentId);

                executorService.execute(() -> {
                    userDao.logout();
                    userDao.insertUser(user);
                });

                callback.onSuccess(user);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error occurred.");
            }
        });
    }

    public void updateProfile(String firstName, String lastName, String username, String email, String address,
                              String phoneNumber, String messengerLink, ApiCallback<User> callback) {
        executorService.execute(() -> {
            Map<String, String> body = new HashMap<>();
            body.put("firstName", firstName);
            body.put("lastName", lastName);
            body.put("username", username);
            body.put("email", email);
            body.put("address", address != null ? address : "");
            // Always sent, including when blank: on the server an empty string
            // is an explicit instruction to clear the stored value rather than
            // "leave this field alone".
            body.put("phoneNumber", phoneNumber != null ? phoneNumber : "");
            body.put("messengerLink", messengerLink != null ? messengerLink : "");

            apiService.updateProfile(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> resData = (Map<String, Object>) response.body().get("resident");
                        User updatedUser = new User(
                                getStringFromMap(resData, "residentId", "resident_id"),
                                getStringFromMap(resData, "firstName", "first_name"),
                                getStringFromMap(resData, "lastName", "last_name"),
                                getStringFromMap(resData, "username", null),
                                getStringFromMap(resData, "email", null),
                                getStringFromMap(resData, "address", null),
                                getStringFromMap(resData, "profileImage", "profile_image")
                        );
                        // Read back rather than echoed from the request, so the
                        // cached row matches what the server actually stored.
                        applyContactFields(updatedUser, resData);
                        executorService.execute(() -> userDao.insertUser(updatedUser));
                        callback.onSuccess(updatedUser);
                    } else {
                        callback.onError(getErrorMessage(response, "Update failed"));
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    callback.onError(t.getMessage());
                }
            });
        });
    }

    public void updateProfileImage(byte[] imageBytes, ApiCallback<String> callback) {
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), imageBytes);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", "profile.jpg", requestFile);

        apiService.updateProfileImage(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String imageUrl = (String) response.body().get("profileImage");
                    if (imageUrl == null) imageUrl = (String) response.body().get("profile_image");
                    callback.onSuccess(imageUrl);
                    fetchMe(new ApiCallback<User>() {
                        @Override public void onSuccess(User result) {}
                        @Override public void onError(String error) {}
                    });
                } else {
                    callback.onError(getErrorMessage(response, "Image upload failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    /**
     * Copies the contact fields off a server response onto the cached user.
     *
     * <p>Shared by login, profile sync and profile update so all three keep the
     * same fields, and so the resident always has whatever they last pasted into
     * the account screen rather than a stale cached value.
     */
    private void applyContactFields(User user, Map<String, Object> resData) {
        if (user == null || resData == null) return;
        user.setPhoneNumber(getStringFromMap(resData, "phoneNumber", "phone_number"));
        user.setMessengerLink(getStringFromMap(resData, "messengerLink", "messenger_link"));
    }

    public void fetchMe(ApiCallback<User> callback) {
        apiService.getProfile().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> resData = (Map<String, Object>) response.body().get("resident");
                    User user = new User(
                            getStringFromMap(resData, "residentId", "resident_id"),
                            getStringFromMap(resData, "firstName", "first_name"),
                            getStringFromMap(resData, "lastName", "last_name"),
                            getStringFromMap(resData, "username", null),
                            getStringFromMap(resData, "email", null),
                            getStringFromMap(resData, "address", null),
                            getStringFromMap(resData, "profileImage", "profile_image")
                    );
                    applyContactFields(user, resData);
                    executorService.execute(() -> userDao.insertUser(user));
                    if (callback != null) callback.onSuccess(user);
                } else {
                    if (callback != null) callback.onError(getErrorMessage(response, "Profile sync failed"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (callback != null) callback.onError(t.getMessage());
            }
        });
    }

    /**
     * Restores a session that was remembered on this device.
     *
     * <p>The token is written into the session before the account is read, because
     * the Authorization header is attached from there. The profile call then
     * decides the outcome: a token the server still accepts signs the resident in
     * without a password, and one that has expired or been revoked comes back as a
     * failure so the caller can ask for the password instead.
     */
    public void restoreSession(String token, String residentId, ApiCallback<User> callback) {
        sessionManager.saveSession(token, residentId);
        fetchMe(new ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                callback.onSuccess(user);
            }

            @Override
            public void onError(String failure) {
                sessionManager.clearSession();
                callback.onError(failure);
            }
        });
    }

    /**
     * Forgets a restored session that turned out to be unusable, so the next tap
     * on that account goes straight to the password field rather than failing the
     * same way again.
     */
    public void clearStoredSession() {
        sessionManager.clearSession();
    }

    public boolean isLoggedIn() {
        return sessionManager.isLoggedIn();
    }

    /**
     * Pauses the account on the server. Nothing is deleted: the profile,
     * reports, history and settings are preserved server-side and the same
     * account is restored on the next successful login. The caller is
     * responsible for logging out once this succeeds, because the paused
     * account must not keep an active session on this device.
     */
    public void disableAccount(ApiCallback<String> callback) {
        apiService.disableAccount().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(readMessage(response.body(),
                            "Your account is disabled."));
                } else {
                    callback.onError(getErrorMessage(response, "Unable to disable the account"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null
                        ? t.getMessage()
                        : "Network error occurred.");
            }
        });
    }

    /**
     * Permanently deletes the account on the server, together with every row it
     * owns. The local session is not touched here; the caller logs out once this
     * succeeds so the login screen is never shown against a deleted account.
     */
    public void deleteAccount(ApiCallback<String> callback) {
        apiService.deleteAccount().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    // The account no longer exists, so its stored fingerprint
                    // credentials must stop being offered on the login screen.
                    // A paused account keeps them, so this runs only here.
                    String deletedResidentId = sessionManager.fetchResidentId();
                    if (deletedResidentId != null && !deletedResidentId.trim().isEmpty()) {
                        final String residentId = deletedResidentId;
                        executorService.execute(() -> biometricDao.deleteSetting(residentId));
                    }
                    callback.onSuccess(readMessage(response.body(),
                            "Your account was deleted."));
                } else {
                    callback.onError(getErrorMessage(response, "Unable to delete the account"));
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onError(t.getMessage() != null
                        ? t.getMessage()
                        : "Network error occurred.");
            }
        });
    }

    private String readMessage(Map<String, Object> body, String fallback) {
        Object message = body == null ? null : body.get("message");
        return message == null ? fallback : message.toString();
    }

    public void logout() {
        // Capture the authenticated token before clearing it. The API call
        // supplies that snapshot explicitly, so account switching cannot bind
        // or unbind the device token as the wrong resident.
        String residentId = sessionManager.fetchResidentId();
        notificationRepository.clearFcmTokenBestEffort();
        if (residentId != null) {
            notificationRepository.clearLocalDataForResident(residentId);
        }

        // Clear the session synchronously.  The database cleanup remains off
        // the main thread, but it is guarded so a rapid account switch cannot
        // let the old cleanup delete the newly logged-in user's cache.
        sessionManager.clearSession();
        executorService.execute(() -> {
            if (sessionManager.isLoggedIn()) {
                return;
            }
            String currentResidentId = sessionManager.fetchResidentId();
            if (currentResidentId != null && !currentResidentId.trim().isEmpty()) {
                return;
            }
            userDao.logout();
            reportDao.deleteAllReports();
        });
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
}

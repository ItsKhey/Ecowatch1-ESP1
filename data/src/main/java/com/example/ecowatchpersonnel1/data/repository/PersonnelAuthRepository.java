package com.example.ecowatchpersonnel1.data.repository;

import android.content.Context;

import com.example.ecowatchpersonnel1.data.models.Personnel;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.remote.ApiCalls;
import com.example.ecowatchpersonnel1.data.remote.ApiService;
import com.example.ecowatchpersonnel1.data.remote.LoginResponse;
import com.example.ecowatchpersonnel1.data.remote.PersonnelResponse;
import com.example.ecowatchpersonnel1.data.remote.RetrofitClient;
import com.example.ecowatchpersonnel1.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Personnel authentication and identity.
 *
 * <p>There is no sign-up: an administrator creates the account, and this
 * repository only exchanges credentials for a token and reads the profile back.
 */
public class PersonnelAuthRepository {

    private final Context appContext;
    private final ApiService api;
    private final SessionManager sessionManager;

    public PersonnelAuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.api = RetrofitClient.getApiService(appContext);
        this.sessionManager = new SessionManager(appContext);
    }

    /**
     * Signs in with a username (or gmail) and password. The password is never
     * stored on the device; only the returned token and personnel id survive.
     */
    public void login(String identifier, String password, ApiCallback<Personnel> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("username", identifier);
        body.put("password", password);

        ApiCalls.enqueue(api.login(body), new ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                if (result == null || result.token == null || result.personnel == null) {
                    callback.onError("The server returned an incomplete login response.");
                    return;
                }
                sessionManager.saveSession(result.token, result.personnel.personnelId);
                sessionManager.rememberUsername(result.personnel.username);
                callback.onSuccess(result.personnel);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** True while a locally stored token and personnel id are present. */
    public boolean isLoggedIn() {
        return sessionManager.isLoggedIn();
    }

    /**
     * Asks the server whether this is the account's real password.
     *
     * <p>Used to gate fingerprint enrolment on knowledge of the password. The
     * check happens on the server, which is the only place the hash lives; this
     * app never stores the password, so it could not verify it locally without
     * keeping a copy, which is exactly what it is avoiding.
     */
    public void verifyPassword(String password, ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("password", password);

        ApiCalls.enqueue(api.verifyPassword(body), new ApiCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> result) {
                callback.onSuccess("Password verified.");
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Changes the signed-in cleaner's own password.
     *
     * <p>The server requires the current password, so a borrowed phone with an
     * open session cannot be used to take the account over. Unlike the resident
     * app, no new password is written to this device: the personnel app never
     * stores the password at all, and the token is untouched by a change.
     */
    public void changePassword(String currentPassword, String newPassword,
                               ApiCallback<String> callback) {
        Map<String, String> body = new HashMap<>();
        body.put("oldPassword", currentPassword);
        body.put("newPassword", newPassword);

        ApiCalls.enqueue(api.changePassword(body), new ApiCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> result) {
                callback.onSuccess("Password updated successfully.");
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Ends the local session and asks the server to forget this device's token.
     *
     * <p>The token call is best-effort: signing out must always succeed on the
     * device even when the network is down, so its result is ignored.
     *
     * <p>The saved task history is cleared on the way out. Field devices are
     * shared, so leaving one cleaner's assignments on the phone for the next
     * person to sign in would show them work that is not theirs.
     */
    public void logout() {
        try {
            api.clearFcmToken().enqueue(new retrofit2.Callback<Void>() {
                @Override
                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                }

                @Override
                public void onFailure(retrofit2.Call<Void> call, Throwable throwable) {
                }
            });
        } catch (Exception ignored) {
            // The local session is cleared regardless.
        }

        String personnelId = sessionManager.fetchPersonnelId();
        sessionManager.clearSession();
        new TaskHistoryRepository(appContext).clear(personnelId);
        // Notifications and their delete history are cleared for the same reason:
        // the next person to sign in must not see this cleaner's alerts, nor the
        // record that they were trashed.
        PersonnelNotificationRepository.getInstance(appContext)
                .clearLocalDataForPersonnel(personnelId);
    }

    public void fetchMe(ApiCallback<Personnel> callback) {
        ApiCalls.enqueue(api.getMe(), new ApiCallback<PersonnelResponse>() {
            @Override
            public void onSuccess(PersonnelResponse result) {
                callback.onSuccess(result == null ? null : result.personnel);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    }

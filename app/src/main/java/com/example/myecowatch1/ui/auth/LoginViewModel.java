package com.example.myecowatch1.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.repository.AuthRepository;

public class LoginViewModel extends AndroidViewModel {

    private AuthRepository repository;
    private MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private MutableLiveData<User> loginSuccessLiveData = new MutableLiveData<>();

    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository(application);
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<User> getLoginSuccess() {
        return loginSuccessLiveData;
    }

    public void login(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            errorLiveData.setValue("Username and password are required.");
            return;
        }

        repository.login(username, password, new AuthRepository.ApiCallback<User>() {
            @Override
            public void onSuccess(User result) {
                loginSuccessLiveData.setValue(result);
            }

            @Override
            public void onError(String error) {
                errorLiveData.setValue(error);
            }
        });
    }

    public boolean isLoggedIn() {
        return repository.isLoggedIn();
    }

    /**
     * Signs in with a session that was remembered on this device, instead of a
     * password.
     *
     * <p>The token is put back into the session first and the account is then
     * re-read from the server with it. That round trip is the point: the token
     * carries a 30-day expiry, so asking the server is what turns a token that
     * has quietly run out into a failed attempt, instead of a dashboard that
     * looks signed in and fails on its first request.
     */
    public void loginWithStoredSession(String token, String residentId) {
        if (token == null || token.trim().isEmpty()
                || residentId == null || residentId.trim().isEmpty()) {
            errorLiveData.setValue("That remembered account can no longer sign in. "
                    + "Please enter your password.");
            return;
        }

        repository.restoreSession(token, residentId, new AuthRepository.ApiCallback<User>() {
            @Override
            public void onSuccess(User result) {
                loginSuccessLiveData.setValue(result);
            }

            @Override
            public void onError(String error) {
                // The stored session is dropped here rather than at the call site:
                // whatever the reason it failed, it will not start working later.
                repository.clearStoredSession();
                errorLiveData.setValue("That remembered account can no longer sign in. "
                        + "Please enter your password.");
            }
        });
    }
}
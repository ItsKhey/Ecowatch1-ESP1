package com.example.ecowatchpersonnel1.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchpersonnel1.data.models.Personnel;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.repository.PersonnelAuthRepository;

/** Sign-in state for one cleaner. Accounts are admin-created, so there is no registration. */
public class LoginViewModel extends AndroidViewModel {

    private final PersonnelAuthRepository repository;
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Personnel> loginSuccess = new MutableLiveData<>();

    public LoginViewModel(@NonNull Application application) {
        super(application);
        repository = new PersonnelAuthRepository(application);
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Personnel> getLoginSuccess() {
        return loginSuccess;
    }

    public void login(String identifier, String password) {
        if (identifier == null || identifier.trim().isEmpty()
                || password == null || password.isEmpty()) {
            error.setValue("Username and password are required.");
            return;
        }

        repository.login(identifier.trim(), password, new ApiCallback<Personnel>() {
            @Override
            public void onSuccess(Personnel result) {
                loginSuccess.setValue(result);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    public boolean isLoggedIn() {
        return repository.isLoggedIn();
    }
}

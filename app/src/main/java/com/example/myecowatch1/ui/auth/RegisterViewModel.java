package com.example.myecowatch1.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.utils.FirebaseEmailSession;
import com.example.myecowatch1.utils.UsernameValidator;

import java.util.regex.Pattern;

/**
 * Drives the sign-up form.
 *
 * <p>Two verification transports exist and the build picks one:
 *
 * <ul>
 *   <li><b>OTP</b> (the default) — the backend emails a 4-digit code and the
 *       resident types it in on this screen. No Firebase is contacted at any
 *       point, which is what a local-server setup needs.</li>
 *   <li><b>EMAIL_LINK</b> — the backend stages a Firebase verification identity
 *       and the resident follows a link in their mail. The observable exposed is
 *       a registrationId plus the email it belongs to, which the fragment hands
 *       to {@link EmailVerificationActivity}.</li>
 * </ul>
 *
 * <p>Nothing here signs the resident in. Verification produces an account, and
 * the account is then used on the ordinary login screen like any other.
 */
public class RegisterViewModel extends AndroidViewModel {

    /** Mirrors the server policy in utils/authValidation.js. */
    private static final int PASSWORD_MIN_LENGTH = 8;

    private static final Pattern HAS_LETTER = Pattern.compile(".*[a-zA-Z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_OTP = Pattern.compile(".*\\d{4}.*");

    private AuthRepository repository;
    private MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private MutableLiveData<String> registrationIdLiveData = new MutableLiveData<>();
    private MutableLiveData<String> registeredEmailLiveData = new MutableLiveData<>();
    private MutableLiveData<String> registeredUsernameLiveData = new MutableLiveData<>();
    private MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>();
    private MutableLiveData<Boolean> verifiedLiveData = new MutableLiveData<>();

    public RegisterViewModel(@NonNull Application application) {
        super(application);
        repository = new AuthRepository(application);
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public LiveData<String> getRegistrationId() {
        return registrationIdLiveData;
    }

    public LiveData<String> getRegisteredEmail() {
        return registeredEmailLiveData;
    }

    public LiveData<String> getRegisteredUsername() {
        return registeredUsernameLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    /** Fires once the account exists. Only used by the local OTP transport. */
    public LiveData<Boolean> getVerified() {
        return verifiedLiveData;
    }

    /**
     * Validates the form and starts the registration.
     *
     * <p>Every rule here is repeated on the server, which is the copy that
     * actually decides. This exists purely so a resident is told what is wrong
     * without a round trip.
     */
    public void register(String firstName, String lastName, String username, String email,
                         String phoneNumber, String password, String confirmPassword) {
        if (firstName.isEmpty() || lastName.isEmpty() || username.isEmpty()
                || email.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
            errorLiveData.setValue("All fields are required.");
            return;
        }

        if (!UsernameValidator.isValid(username)) {
            errorLiveData.setValue("Invalid username. Use 3-20 alphanumeric characters.");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorLiveData.setValue("Invalid email address.");
            return;
        }

        // Mirrors the server's phone constraint so the resident is told before a
        // round trip rather than after.
        if (!phoneNumber.matches("^[0-9+() .-]{7,25}$")) {
            errorLiveData.setValue("Invalid phone number.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            errorLiveData.setValue("Passwords do not match.");
            return;
        }

        String passwordProblem = describePasswordProblem(password);
        if (passwordProblem != null) {
            errorLiveData.setValue(passwordProblem);
            return;
        }

        loadingLiveData.setValue(true);

        repository.register(firstName, lastName, username, email, phoneNumber, password,
                new AuthRepository.ApiCallback<AuthRepository.RegistrationOutcome>() {
                    @Override
                    public void onSuccess(AuthRepository.RegistrationOutcome outcome) {
                        if (!outcome.requiresVerification()) {
                            // The account already exists, so there is no code to
                            // type and nothing to wait for.
                            loadingLiveData.postValue(false);
                            verifiedLiveData.postValue(true);
                            return;
                        }
                        onRegistrationStaged(outcome.getRegistrationId(), email, username, password);
                    }

                    @Override
                    public void onError(String error) {
                        loadingLiveData.postValue(false);
                        errorLiveData.postValue(error);
                    }
                });
    }

    /**
     * Completes a local OTP registration once the resident types the emailed
     * code. Unused by the EMAIL_LINK build, which verifies through Firebase.
     */
    public void verifyOTP(String registrationId, String otp) {
        if (registrationId == null || registrationId.isEmpty()) {
            errorLiveData.setValue("Start registration again.");
            return;
        }

        if (otp == null || !HAS_OTP.matcher(otp).matches()) {
            errorLiveData.setValue("Enter the 4-digit code from your email.");
            return;
        }

        loadingLiveData.setValue(true);

        repository.verifyOTP(registrationId, otp, new AuthRepository.ApiCallback<String>() {
            @Override
            public void onSuccess(String residentId) {
                loadingLiveData.postValue(false);
                verifiedLiveData.postValue(true);
            }

            @Override
            public void onError(String error) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue(error);
            }
        });
    }

    /**
     * The backend has staged the signup and emailed the code. The two transports
     * part ways here: the local flow is finished by {@link #verifyOTP}, while the
     * Firebase flow still has no session because the backend never signs in, so
     * the app creates the identity itself and the emailed link is what finishes
     * the job on {@link EmailVerificationActivity}.
     */
    private void onRegistrationStaged(String registrationId, String email, String username,
                                      String password) {
        if (ServerConfig.USES_LOCAL_OTP_REGISTRATION) {
            loadingLiveData.postValue(false);
            registeredEmailLiveData.postValue(email);
            registeredUsernameLiveData.postValue(username);
            registrationIdLiveData.postValue(registrationId);
            return;
        }

        // Firebase will not deliver a verification email without a signed-in
        // user, and the server never signs in. Creating that user here is the
        // only way the email can be sent, and the session is signed out as soon
        // as the EcoWatch account exists. The password is needed for exactly that
        // one call and is not retained.
        FirebaseEmailSession.createAndSendVerificationEmail(
                email, password,
                new FirebaseEmailSession.Callback() {
                    @Override
                    public void onSuccess() {
                        loadingLiveData.postValue(false);
                        registeredEmailLiveData.postValue(email);
                        registeredUsernameLiveData.postValue(username);
                        registrationIdLiveData.postValue(registrationId);
                    }

                    @Override
                    public void onError(String message, boolean keepSession) {
                        if (!keepSession) {
                            FirebaseEmailSession.signOutQuietly();
                        }
                        loadingLiveData.postValue(false);
                        errorLiveData.postValue(message);
                    }

                    @Override
                    public void onNotYetVerified() {
                        // Unreachable during registration.
                    }

                    @Override
                    public void onVerifiedToken(String idToken) {
                        // Unreachable during registration.
                    }
                });
    }

    /**
     * Returns the first thing wrong with a password, or null when it is fine.
     *
     * <p>Exposed so the register form can show the same guidance on the password
     * field that the server would give.
     */
    public static String describePasswordProblem(String password) {
        if (password.length() < PASSWORD_MIN_LENGTH) {
            return "Password must be at least " + PASSWORD_MIN_LENGTH + " characters.";
        }

        if (!HAS_LETTER.matcher(password).matches() || !HAS_DIGIT.matcher(password).matches()) {
            return "Password must contain at least one letter and one number.";
        }

        return null;
    }
}

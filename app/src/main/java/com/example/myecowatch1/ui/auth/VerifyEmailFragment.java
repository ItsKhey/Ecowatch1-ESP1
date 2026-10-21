package com.example.myecowatch1.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.remote.EmailVerificationStatus;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.notifications.FcmTokenSyncScheduler;
import com.example.myecowatch1.ui.home.DashboardActivity;
import com.example.myecowatch1.utils.FirebaseEmailSession;
import com.example.myecowatch1.utils.PendingRegistrationCredentials;
import com.example.myecowatch1.utils.PendingRegistrationStore;
import com.example.myecowatch1.utils.SavedAccounts;
import com.example.myecowatch1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * The "I have opened the link" step of registration.
 *
 * <p>There is no code to type. Proving the address is done by opening the link
 * that was emailed, and this screen's only job is to ask the backend whether
 * that has happened yet. The account is created at the moment the answer turns
 * yes, so the resident never has to press a second button to finish signing up.
 *
 * <p>The registrationId and email are read from {@link PendingRegistrationStore}
 * rather than passed as arguments, so the step still works after the process has
 * been killed while the resident was in their email app.
 */
public class VerifyEmailFragment extends Fragment {

    private AuthRepository authRepository;
    private PendingRegistrationStore pendingStore;

    private TextView verifyEmailText;
    private MaterialButton checkVerificationBtn;
    private MaterialButton resendVerificationBtn;
    private ProgressBar progressBar;

    private String registrationId;
    private String email;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_verify_email, container, false);

        authRepository = new AuthRepository(requireActivity().getApplication());
        pendingStore = new PendingRegistrationStore(requireContext());

        verifyEmailText = view.findViewById(R.id.verifyEmailText);
        checkVerificationBtn = view.findViewById(R.id.checkVerificationBtn);
        resendVerificationBtn = view.findViewById(R.id.resendVerificationBtn);
        progressBar = view.findViewById(R.id.verifyProgressBar);
        TextView backToRegisterBtn = view.findViewById(R.id.backToRegisterBtn);

        registrationId = pendingStore.getRegistrationId();
        email = pendingStore.getEmail();

        if (email != null && !email.isEmpty()) {
            verifyEmailText.setText(email);
        }

        checkVerificationBtn.setOnClickListener(v -> checkVerification());
        resendVerificationBtn.setOnClickListener(v -> resendVerification());
        backToRegisterBtn.setOnClickListener(v -> {
            // Abandoning the registration, so the Firebase session is closed.
            FirebaseEmailSession.signOutQuietly();
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        /*
         * The resident may have just come back from their mail app having opened
         * the link, so the check runs automatically on every return to this
         * screen. It refreshes the Firebase session and asks the backend, which is
         * what makes the deep link feel instant.
         */
        if (hasRegistration()) {
            checkVerification();
        }

        updateResendButton();
    }

    private boolean hasRegistration() {
        return registrationId != null && !registrationId.isEmpty()
                && email != null && !email.isEmpty();
    }

    private void checkVerification() {
        if (!hasRegistration()) {
            showMissingRegistration();
            return;
        }

        if (isBusy()) {
            return;
        }

        setLoading(true);

        /*
         * Firebase owns the truth here. The local session is refreshed from the
         * server first, because emailVerified on the cached user does not update
         * on its own, and the resulting ID token is what the backend checks.
         */
        FirebaseEmailSession.confirmVerification(new FirebaseEmailSession.Callback() {
            @Override
            public void onSuccess() {
                // Unreachable; confirmation flows through onVerifiedToken.
            }

            @Override
            public void onError(String message, boolean keepSession) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                if (!keepSession) {
                    // The registration is abandoned, so the session must not be
                    // left resting on the device.
                    FirebaseEmailSession.signOutQuietly();
                }
                toast(message);
            }

            @Override
            public void onNotYetVerified() {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                // Not an error. The resident may simply not have opened the link.
                toast("We have not seen your email opened yet. Open the link in your "
                        + "inbox, then tap this again.");
            }

            @Override
            public void onVerifiedToken(String idToken) {
                if (!isAdded()) {
                    return;
                }
                confirmWithBackend(idToken);
            }
        });
    }

    /**
     * Asks the backend to create the account.
     *
     * <p>This is the only decision that matters: the backend confirms the token
     * with Firebase and creates the resident. Nothing here trusts the token on
     * its own, and nothing here signs the resident in.
     */
    private void confirmWithBackend(String idToken) {
        authRepository.checkEmailVerification(registrationId, idToken,
                new AuthRepository.ApiCallback<EmailVerificationStatus>() {
                    @Override
                    public void onSuccess(EmailVerificationStatus status) {
                        if (!isAdded()) {
                            return;
                        }
                        setLoading(false);

                        // The account exists now, so the Firebase session has done
                        // its job and is closed. EcoWatch login is the only session
                        // from here on.
                        FirebaseEmailSession.signOutQuietly();

                        onVerified(status);
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) {
                            return;
                        }
                        setLoading(false);
                        toast(error);
                    }
                });
    }

    private void onVerified(EmailVerificationStatus status) {
        // The account now exists, so the stored handle has served its purpose and
        // must not be able to be replayed.
        pendingStore.clear();

        /*
         * Record the account for the sign-in screen's account picker, honouring
         * the "remember me on this device" choice made on the register form.
         *
         * Done HERE rather than when the form was submitted, because this is the
         * first moment the account actually exists. Saving at submit time would
         * leave the picker offering an account for a registration that was
         * abandoned or never verified, and that entry could never sign in.
         *
         * Only the username is stored, which the resident typed anyway and which
         * is not a secret; SavedAccounts keeps no password. Unchecking the box is
         * a statement that this device should keep nothing, so the entry is
         * skipped rather than quietly added.
         */
        SessionManager sessionManager = new SessionManager(requireContext());
        boolean remember = sessionManager.isRememberMe();

        if (remember) {
            SavedAccounts.remember(requireContext(), status.getUsername());
        }

        // SessionManager is not otherwise touched below: verification creates the
        // account, it does not sign anyone in. The resident goes to the login
        // screen as before.
        if (remember && activateWithRememberedSession(sessionManager, status)) {
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Email verified")
                .setMessage(status.getMessage().isEmpty()
                        ? "Your EcoWatch account is ready. Sign in with your username and password."
                        : status.getMessage())
                .setCancelable(false)
                .setPositiveButton("Go to Login", (dialog, which) -> goToLogin(status.getUsername()))
                .show();
    }

    /**
     * Completes activation by signing the resident straight in.
     *
     * <p>This is what makes a newly registered account tap-to-sign-in on the
     * account dialog rather than merely offering its username. Remembering an
     * account is only useful if choosing it gets the resident in, and there is no
     * session to remember until a sign-in has happened, so one is performed here
     * using the password they typed a moment ago and held in memory for exactly
     * this purpose.
     *
     * @return true when the sign-in was started, so the caller skips the manual
     *         "Go to Login" prompt; false when there was nothing to sign in with,
     *         or the attempt was rejected, in which case the resident is asked to
     *         sign in themselves
     */
    private boolean activateWithRememberedSession(SessionManager sessionManager,
                                                  EmailVerificationStatus status) {
        String username = PendingRegistrationCredentials.username();
        String password = PendingRegistrationCredentials.password();

        // The held password is spent either way. Leaving it readable after the
        // sign-in has been attempted would widen its lifetime for nothing.
        PendingRegistrationCredentials.clear();

        if (username.isEmpty() || password.isEmpty()) {
            // The process was reclaimed while the resident was in their email app,
            // so the password they typed is gone. That is an ordinary outcome: the
            // account is created and remembered, and they sign in as usual.
            return false;
        }

        setLoading(true);
        authRepository.login(username, password, new AuthRepository.ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);

                // AuthRepository has already saved the session. Pair the username
                // with the token it issued, which is what lets the account dialog
                // sign in on tap from here on.
                SavedAccounts.rememberWithSession(
                        requireContext(),
                        username,
                        user.getServerId(),
                        sessionManager.fetchAuthToken());

                FcmTokenSyncScheduler.syncCurrentToken(requireContext());

                startActivity(new android.content.Intent(
                        requireContext(), DashboardActivity.class));
                if (getActivity() != null) {
                    getActivity().finishAffinity();
                }
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                // The account is real, so it stays remembered; only the failed
                // sign-in is discarded. The resident signs in by hand.
                goToLogin(username);
            }
        });

        return true;
    }

    private void goToLogin(String username) {
        if (getActivity() == null) {
            return;
        }

        android.content.Intent intent =
                new android.content.Intent(getActivity(), LoginActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (username != null && !username.isEmpty()) {
            // Prefilled so the resident does not have to remember which username
            // they chose. This is not a login shortcut: the password is still
            // required and is still checked by the backend exactly as before.
            intent.putExtra(LoginActivity.EXTRA_PREFILL_USERNAME, username);
        }

        startActivity(intent);

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    /**
     * Asks Firebase to send the verification email again.
     *
     * <p>No backend call is involved. Firebase throttles repeated sends itself, so
     * there is no local cooldown to keep and the button is always available.
     */
    private void resendVerification() {
        if (isBusy()) {
            return;
        }

        setLoading(true);

        FirebaseEmailSession.resendVerificationEmail(new FirebaseEmailSession.Callback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                // The button stays live so the resident can try again if the email
                // does not arrive; Firebase decides the rate limit.
                updateResendButton();
                toast("We sent another verification link. Check your inbox and your "
                        + "spam folder.");
            }

            @Override
            public void onError(String message, boolean keepSession) {
                if (!isAdded()) {
                    return;
                }
                setLoading(false);
                if (!keepSession) {
                    FirebaseEmailSession.signOutQuietly();
                }
                toast(message);
            }

            @Override
            public void onNotYetVerified() {
                // Unreachable on this path.
            }

            @Override
            public void onVerifiedToken(String idToken) {
                // Unreachable on this path.
            }
        });
    }

    private void showMissingRegistration() {
        /*
         * The stored handle is gone, which by itself tells us almost nothing.
         *
         * PendingRegistrationStore is cleared the moment the account is created,
         * so an EMPTY store is the normal state of a device whose registration
         * already finished. That is the common case by far: the resident opened
         * their verification link once, the account was activated, the handle was
         * cleared, and now they are back on this screen through a link they
         * reopened. Telling them to "register again" there is not just wrong, it
         * invites a duplicate account for an address that is already taken.
         *
         * So before dead-ending, ask the one question this device CAN answer for
         * itself without touching the network: does it still hold a verified
         * Firebase identity? That identity was created during registration and
         * outlives the pending handle, so if it is verified the account exists
         * and the resident needs to sign in, not register.
         *
         * Deliberately local. Asking the backend whether an address is registered
         * would turn this screen into an account-existence oracle, which is the
         * one thing the rest of the auth flow refuses to be.
         */
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            showAlreadyVerified();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Verification unavailable")
                .setMessage("This device has no registration waiting to be verified. "
                        + "If you have already registered, sign in instead — or use "
                        + "Forgot password if you cannot remember your password.")
                .setCancelable(false)
                .setNegativeButton("Sign in", (dialog, which) -> openLogin())
                .setPositiveButton("Register", (dialog, which) -> {
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .show();
    }

    /**
     * The account is already activated; what is missing is a sign-in.
     *
     * <p>This is the outcome of opening a verification link a second time, and
     * it is a success state rather than an error, so it is worded as one.
     */
    private void showAlreadyVerified() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Email already verified")
                .setMessage("This account is already activated on this device. "
                        + "Sign in with your username and password.")
                .setCancelable(false)
                .setPositiveButton("Sign in", (dialog, which) -> openLogin())
                .show();
    }

    /**
     * Opens the sign-in screen, clearing this activity off the stack so Back
     * from Login leaves the app rather than returning to a verification screen
     * that has nothing left to verify.
     */
    private void openLogin() {
        if (getActivity() == null) {
            return;
        }
        // The Firebase session has served its purpose here; leaving it signed in
        // would let a later registration reuse this verified identity silently.
        FirebaseEmailSession.signOutQuietly();
        getActivity().finish();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void updateResendButton() {
        // Firebase applies its own rate limiting, so this button stays available
        // and the resident can try again if an email does not arrive.
        resendVerificationBtn.setText("Resend Verification Email");
        resendVerificationBtn.setEnabled(!isBusy());
    }

    private boolean isBusy() {
        return progressBar.getVisibility() == View.VISIBLE;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        checkVerificationBtn.setEnabled(!loading);
        resendVerificationBtn.setEnabled(!loading);
    }

    private void toast(String message) {
        if (message != null && !message.isEmpty() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}

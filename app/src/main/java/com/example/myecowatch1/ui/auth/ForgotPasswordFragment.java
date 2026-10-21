package com.example.myecowatch1.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.remote.PasswordResetPreparation;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.utils.FirebaseEmailSession;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Forgot Password.
 *
 * <p>This screen does one thing: asks the backend to email a Firebase reset link
 * to the address on the account. It never sets a password itself, and it holds no
 * code to check.
 *
 * <p>The new password is set in {@link ResetPasswordActivity}, which the emailed
 * link opens. That split is the point of the design: the link is what proves the
 * resident owns the inbox, and the backend is what decides the password. By the
 * time this screen returns, the resident is expected to sign in on the unchanged
 * login screen with their username.
 *
 * <p>The server answers identically whether or not an address is registered, so
 * this screen always shows the same confirmation. That keeps it from being used to
 * find out who has an EcoWatch account.
 */
public class ForgotPasswordFragment extends Fragment {

    private AuthRepository authRepository;
    private LinearLayout methodContainer, linkSentContainer;
    private EditText emailInput;
    private TextView linkSentEmailText;
    private MaterialButton sendResetLinkBtn, resendResetLinkBtn;
    private ProgressBar progressBar;
    private TextView backToLoginBtn;

    /** The address the current link was sent to, so a resend targets the same one. */
    private String currentEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_forgot_password, container, false);
        authRepository = new AuthRepository(requireActivity().getApplication());

        methodContainer = view.findViewById(R.id.methodContainer);
        linkSentContainer = view.findViewById(R.id.linkSentContainer);
        emailInput = view.findViewById(R.id.forgotEmailInput);
        linkSentEmailText = view.findViewById(R.id.linkSentEmailText);
        sendResetLinkBtn = view.findViewById(R.id.sendResetLinkBtn);
        resendResetLinkBtn = view.findViewById(R.id.resendResetLinkBtn);
        progressBar = view.findViewById(R.id.forgotProgressBar);
        backToLoginBtn = view.findViewById(R.id.backToLoginBtn);

        sendResetLinkBtn.setOnClickListener(v -> requestResetLink());
        resendResetLinkBtn.setOnClickListener(v -> {
            if (currentEmail != null && !currentEmail.isEmpty()) {
                requestResetLinkFor(currentEmail);
            }
        });
        backToLoginBtn.setOnClickListener(v -> closeFragment());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateResendButton();
    }

    /**
     * Prepares the reset on the server, then asks Firebase to send the email.
     *
     * <p>Two steps, for a reason. Firebase will not send a reset email for an
     * address it has never seen, and an account created before the Firebase
     * integration has no identity yet, so the backend creates one first. It also
     * owns the cooldown.
     *
     * <p>The message shown is identical whether or not the address is registered,
     * so this screen cannot be used to discover who has an account.
     */
    private void requestResetLink() {
        String email = emailInput.getText() == null
                ? "" : emailInput.getText().toString().trim();

        if (email.isEmpty()) {
            Toast.makeText(getContext(), "Please enter your email address.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address.");
            return;
        }

        requestResetLinkFor(email);
    }

    /** Shared by the initial send and the "send again" button. */
    private void requestResetLinkFor(String email) {
        if (isBusy()) {
            return;
        }

        setLoading(true);

        authRepository.preparePasswordReset(email,
                new AuthRepository.ApiCallback<PasswordResetPreparation>() {
                    @Override
                    public void onSuccess(PasswordResetPreparation preparation) {
                        if (!isAdded()) {
                            return;
                        }

                        if (!preparation.canRequestFirebaseEmail()) {
                            // Either the address is unknown or a cooldown is in
                            // force. The resident is told to check their inbox
                            // either way, so nothing is revealed.
                            setLoading(false);
                            showLinkSent(email);
                            return;
                        }

                        sendViaFirebase(email);
                    }

                    @Override
                    public void onError(String error) {
                        if (!isAdded()) {
                            return;
                        }
                        setLoading(false);
                        Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Asks Firebase to deliver the reset email.
     *
     * <p>This is the only place a reset email is sent from. Firebase decides
     * whether it may be sent, which is why the backend prepares an identity
     * beforehand. Any failure is reported with the same honest message, because a
     * different one would disclose whether the account exists.
     */
    private void sendViaFirebase(String email) {
        FirebaseAuth.getInstance()
                .sendPasswordResetEmail(email, FirebaseEmailSession.actionCodeSettings())
                .addOnCompleteListener(task -> {
                    if (!isAdded()) {
                        return;
                    }
                    setLoading(false);
                    showLinkSent(email);
                });
    }

    private void showLinkSent(String email) {
        currentEmail = email;
        linkSentEmailText.setText(email);
        methodContainer.setVisibility(View.GONE);
        linkSentContainer.setVisibility(View.VISIBLE);
        updateResendButton();

        Toast.makeText(getContext(),
                "If an account exists for this email, a password reset link has "
                        + "been sent.", Toast.LENGTH_LONG).show();
    }

    private boolean isBusy() {
        return progressBar.getVisibility() == View.VISIBLE;
    }

    private void updateResendButton() {
        if (resendResetLinkBtn == null) {
            return;
        }

        // Firebase applies its own rate limiting, so the button stays available.
        resendResetLinkBtn.setText("Send the Link Again");
        resendResetLinkBtn.setEnabled(!isBusy());
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendResetLinkBtn.setEnabled(!loading);
        resendResetLinkBtn.setEnabled(!loading);
    }

    private void closeFragment() {
        if (getActivity() == null) {
            return;
        }

        getActivity().getSupportFragmentManager().beginTransaction()
                .remove(this)
                .commit();

        // If in LoginActivity, show the login layout again
        View loginLayout = getActivity().findViewById(R.id.loginLayout);
        View fragmentContainer = getActivity().findViewById(R.id.fragmentContainer);
        if (loginLayout != null && fragmentContainer != null) {
            loginLayout.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
        } else {
            // If in Dashboard/Profile, just pop back
            getParentFragmentManager().popBackStack();
        }
    }
}

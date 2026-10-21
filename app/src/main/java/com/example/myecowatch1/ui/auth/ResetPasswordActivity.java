package com.example.myecowatch1.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.remote.PasswordResetResult;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.utils.EmailActionLink;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * "Create New Password", reached by tapping the link in the reset email.
 *
 * <p>This screen deliberately holds no credential of its own. It receives an
 * opaque {@code oobCode} from the deep link and forwards it to the backend,
 * which validates it against Firebase, works out which account it belongs to and
 * writes the new bcrypt hash. The resident never names the account being reset,
 * so a link cannot be edited into one that targets somebody else.
 *
 * <p>After a successful reset the resident is returned to the ordinary login
 * screen and signs in with their username. No Firebase sign-in happens here or
 * anywhere else in the app.
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private TextInputEditText newPasswordInput;
    private TextInputEditText confirmNewPasswordInput;
    private MaterialButton resetPasswordBtn;
    private ProgressBar progressBar;
    private TextView backToLoginBtn;

    private String oobCode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_reset_password);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.reset_main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top,
                            systemBars.right, systemBars.bottom);
                    return insets;
                });

        authRepository = new AuthRepository(getApplication());

        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmNewPasswordInput = findViewById(R.id.confirmNewPasswordInput);
        resetPasswordBtn = findViewById(R.id.resetPasswordBtn);
        progressBar = findViewById(R.id.resetProgressBar);
        backToLoginBtn = findViewById(R.id.backToLoginBtn);

        resetPasswordBtn.setOnClickListener(v -> submitNewPassword());
        backToLoginBtn.setOnClickListener(v -> goToLogin(""));

        // A weak password is only worth reporting once the resident stops typing,
        // so the guidance is attached to the field rather than to the button.
        newPasswordInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String password = s.toString();
                if (password.isEmpty()) {
                    newPasswordInput.setError(null);
                } else if (password.length() < 8) {
                    newPasswordInput.setError("Use at least 8 characters.");
                } else if (!password.matches(".*[a-zA-Z].*") || !password.matches(".*\\d.*")) {
                    newPasswordInput.setError("Include at least one letter and one number.");
                } else {
                    newPasswordInput.setError(null);
                }
            }
        });

        readActionCode(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // launchMode="singleTop" means a second tap on the same emailed link is
        // delivered here instead of creating another copy of this screen.
        setIntent(intent);
        readActionCode(intent);
    }

    /**
     * Pulls the oobCode out of the incoming link.
     *
     * <p>Handled by {@link EmailActionLink} so this works whether the code
     * arrived as a query parameter on the App Link, as a query parameter on the
     * custom-scheme link, or as an extra forwarded by
     * {@link EmailActionActivity}.
     *
     * The code is a credential, so it is read but never logged and never
     * persisted: if the resident backgrounds the app the link has to be tapped
     * again.
     */
    private void readActionCode(Intent intent) {
        String code = EmailActionLink.readOobCode(intent);

        if (code == null || code.isEmpty()) {
            oobCode = null;
            setFormEnabled(false);
            showInvalidLink();
            return;
        }

        oobCode = code;
        setFormEnabled(true);
    }

    private void showInvalidLink() {
        Toast.makeText(this,
                "This password reset link is incomplete. Please open the link from "
                        + "your email again.",
                Toast.LENGTH_LONG).show();
    }

    private void submitNewPassword() {
        if (oobCode == null || oobCode.isEmpty()) {
            showInvalidLink();
            return;
        }

        if (isBusy()) {
            return;
        }

        String newPassword = newPasswordInput.getText() == null
                ? "" : newPasswordInput.getText().toString().trim();
        String confirmPassword = confirmNewPasswordInput.getText() == null
                ? "" : confirmNewPasswordInput.getText().toString().trim();

        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please enter and confirm your new password.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            // Checked here for a fast response, and again on the server, which is
            // the copy that actually counts.
            confirmNewPasswordInput.setError("Passwords do not match.");
            return;
        }

        if (newPassword.length() < 8) {
            newPasswordInput.setError("Use at least 8 characters.");
            return;
        }

        if (!newPassword.matches(".*[a-zA-Z].*") || !newPassword.matches(".*\\d.*")) {
            newPasswordInput.setError("Include at least one letter and one number.");
            return;
        }

        setLoading(true);

        authRepository.resetPasswordWithFirebase(oobCode, newPassword,
                new AuthRepository.ApiCallback<PasswordResetResult>() {
                    @Override
                    public void onSuccess(PasswordResetResult result) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        setLoading(false);

                        if (!result.isFirebaseSynced() && !result.getWarning().isEmpty()) {
                            // The EcoWatch password already changed and works. This
                            // is a repairable drift, not a failed reset, so it is
                            // surfaced as a note rather than an error.
                            Toast.makeText(ResetPasswordActivity.this,
                                    result.getWarning(), Toast.LENGTH_LONG).show();
                        }

                        retireActionCode();

                        String message = result.getMessage().isEmpty()
                                ? "Your password has been changed. Sign in with your username."
                                : result.getMessage();
                        Toast.makeText(ResetPasswordActivity.this, message,
                                Toast.LENGTH_LONG).show();

                        goToLogin(result.getUsername());
                    }

                    @Override
                    public void onError(String error) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        setLoading(false);
                        Toast.makeText(ResetPasswordActivity.this, error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Best-effort cleanup: marks the emailed code as used so the same link cannot
     * be replayed from a shared mailbox.
     *
     * <p>The password is already changed and stored by the backend at this point,
     * so this call is pure hygiene. Any failure is ignored on purpose, which is
     * also why nothing here requires the app's Firebase options to be configured
     * for in-app handling.
     */
    private void retireActionCode() {
        if (oobCode == null) {
            return;
        }

        try {
            // applyActionCode takes the oobCode directly. Marking it used is what
            // stops the same emailed link being replayed from a shared mailbox.
            com.google.firebase.auth.FirebaseAuth.getInstance().applyActionCode(oobCode);
        } catch (Throwable ignored) {
            // The reset already succeeded and the new password is stored. A code
            // retirement problem must not turn a completed password change into a
            // failure, so this is deliberately swallowed.
        }
    }

    private void goToLogin(String username) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (username != null && !username.isEmpty()) {
            intent.putExtra(LoginActivity.EXTRA_PREFILL_USERNAME, username);
        }

        startActivity(intent);
        finish();
    }

    private boolean isBusy() {
        return progressBar.getVisibility() == android.view.View.VISIBLE;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading
                ? android.view.View.VISIBLE : android.view.View.GONE);
        resetPasswordBtn.setEnabled(!loading);
        newPasswordInput.setEnabled(!loading);
        confirmNewPasswordInput.setEnabled(!loading);
    }

    private void setFormEnabled(boolean enabled) {
        newPasswordInput.setEnabled(enabled);
        confirmNewPasswordInput.setEnabled(enabled);
        resetPasswordBtn.setEnabled(enabled);
    }

    /** Minimal watcher so the field validation does not need an anonymous class. */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}

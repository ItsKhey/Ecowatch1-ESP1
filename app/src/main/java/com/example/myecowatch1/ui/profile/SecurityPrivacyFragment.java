package com.example.myecowatch1.ui.profile;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.ui.auth.ForgotPasswordFragment;
import com.example.myecowatch1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import android.provider.Settings;
import androidx.appcompat.widget.SwitchCompat;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Build;

public class SecurityPrivacyFragment extends Fragment {

    private static final String TAG = "SecurityPrivacyFragment";
    private SessionManager sessionManager;
    private AuthRepository authRepository;
    private View fingerprintRow;
    private String currentResidentId = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_security_privacy, container, false);
        sessionManager = new SessionManager(requireContext());
        authRepository = new AuthRepository(requireActivity().getApplication());

        authRepository.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                currentResidentId = user.getServerId();
                sessionManager.syncBiometricsFromDb(currentResidentId, () -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(this::updateBiometricStatus);
                    }
                });
            }
        });

        initViews(view);
        return view;
    }

    private void initViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> getParentFragmentManager().popBackStack());

        fingerprintRow = view.findViewById(R.id.itemFingerprint);

        setupRow(view.findViewById(R.id.itemChangePassword), android.R.drawable.ic_menu_edit, "Change Password", null);
        setupRow(view.findViewById(R.id.itemLocationPrivacy), android.R.drawable.ic_menu_mylocation, "Location Privacy", null);
        setupRow(view.findViewById(R.id.itemReportPhotos), android.R.drawable.ic_menu_camera, "Report Photos", null);
        
        setupSwitchRow(fingerprintRow, android.R.drawable.ic_lock_idle_lock, "Biometric Login");

        fingerprintRow.setOnClickListener(v -> handleBiometricToggle("fingerprint"));

        view.findViewById(R.id.itemChangePassword).setOnClickListener(v -> showChangePasswordDialog());
        view.findViewById(R.id.itemLocationPrivacy).setOnClickListener(v -> showPrivacyInfo("Location Privacy", "EcoWatch uses your location only when you submit a report. This helps waste management personnel identify and reach the reported area more accurately."));
        view.findViewById(R.id.itemReportPhotos).setOnClickListener(v -> showPrivacyInfo("Report Photos", "Photos you upload are used solely to document environmental concerns. They help authorities understand the nature and scale of the reported issue."));
    }

    private void setupSwitchRow(View row, int iconRes, String title) {
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleTv = row.findViewById(R.id.rowTitle);
        icon.setImageResource(iconRes);
        titleTv.setText(title);
    }

    private void setupRow(View row, int iconRes, String title, String value) {
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleTv = row.findViewById(R.id.rowTitle);
        TextView valueTv = row.findViewById(R.id.rowValue);

        icon.setImageResource(iconRes);
        titleTv.setText(title);
        if (valueTv != null) {
            if (value != null) {
                valueTv.setText(value);
                valueTv.setVisibility(View.VISIBLE);
            } else {
                valueTv.setVisibility(View.GONE);
            }
        }
    }

    private void updateBiometricStatus() {
        if (currentResidentId.isEmpty()) return;
        sessionManager.getBiometricSettingsAsync(currentResidentId, setting -> {
            if (getActivity() != null && setting != null) {
                getActivity().runOnUiThread(() -> {
                    MaterialSwitch fpSwitch = fingerprintRow.findViewById(R.id.rowSwitch);
                    fpSwitch.setChecked(setting.isFingerprintEnabled());
                });
            }
        });
    }

    private void handleBiometricToggle(String type) {
        if (currentResidentId.isEmpty()) {
            Toast.makeText(getContext(), "User data not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        MaterialSwitch targetSwitch = fingerprintRow.findViewById(R.id.rowSwitch);
        boolean currentlyEnabled = targetSwitch.isChecked();

        if (currentlyEnabled) {
            // Turning OFF
            sessionManager.setFingerprintEnabled(currentResidentId, false);
            targetSwitch.setChecked(false);
            Toast.makeText(getContext(), "Biometric login disabled", Toast.LENGTH_SHORT).show();
        } else {
            // Turning ON
            checkHardwareAndEnrollment("fingerprint", targetSwitch);
        }
    }

    private void checkHardwareAndEnrollment(String type, MaterialSwitch targetSwitch) {
        PackageManager pm = requireContext().getPackageManager();
        BiometricManager bm = BiometricManager.from(requireContext());
        
        boolean hasHardware = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);

        if (!hasHardware) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Hardware Not Supported")
                    .setMessage("We were unable to detect a compatible Fingerprint sensor on this device. This security feature is unavailable for your device configuration.")
                    .setPositiveButton("Dismiss", null)
                    .show();
            return;
        }

        int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            showEnrollmentDialog("fingerprint");
        } else if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            showConfirmPasswordDialog("fingerprint");
        } else {
            String errorMsg = "Biometric authentication is currently unavailable. Please ensure your device's security settings are correctly configured.";
            Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
        }
    }

    private void showEnrollmentDialog(String type) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Fingerprint Registration Required")
                .setMessage("To enable fingerprint authentication, please register your fingerprint in your device settings. This ensures a secure and professional authentication experience.")
                .setPositiveButton("System Settings", (dialog, which) -> {
                    Intent intent;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        intent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                        intent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricManager.Authenticators.BIOMETRIC_STRONG);
                    } else {
                        intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    }
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showConfirmPasswordDialog(String type) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_confirm_password, null);
        TextInputEditText passInput = dialogView.findViewById(R.id.confirmPasswordInput);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirm);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnConfirm.setOnClickListener(v -> {
            String password = passInput.getText().toString().trim();
            String savedPassword = sessionManager.fetchUserPassword().trim();

            if (password.isEmpty()) {
                Toast.makeText(getContext(), "Password is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (savedPassword.isEmpty()) {
                Log.e(TAG, "Security check failed: No password found in session for " + currentResidentId);
                Toast.makeText(getContext(), "Security error: Please re-login to update your credentials.", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                return;
            }

            if (password.equals(savedPassword)) {
                dialog.dismiss();
                startBiometricActivation("fingerprint");
            } else {
                Log.e(TAG, "Password mismatch: Entered='" + password + "', Saved='" + savedPassword + "'");
                Toast.makeText(getContext(), "Incorrect password", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void startBiometricActivation(String type) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable Fingerprint Login")
                .setSubtitle("Please perform a biometric scan to finalize activation.")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            Toast.makeText(getContext(), "Activation failed: " + errString, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        sessionManager.setFingerprintEnabled(currentResidentId, true);
                        updateBiometricStatus();
                        Toast.makeText(getContext(), "Fingerprint login enabled successfully!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(getContext(), "Authentication failed. Please verify your biometric identity.", Toast.LENGTH_SHORT).show();
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    private void showChangePasswordDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_change_password, null);
        TextInputEditText oldPass = dialogView.findViewById(R.id.oldPasswordInput);
        TextInputEditText newPass = dialogView.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmPass = dialogView.findViewById(R.id.confirmPasswordInput);
        MaterialButton btnUpdate = dialogView.findViewById(R.id.btnChangePassword);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnUpdate.setOnClickListener(v -> {
            String op = oldPass.getText().toString();
            String np = newPass.getText().toString();
            String cp = confirmPass.getText().toString();

            if (op.isEmpty() || np.isEmpty() || cp.isEmpty()) {
                Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!np.equals(cp)) {
                Toast.makeText(getContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            btnUpdate.setEnabled(false);
            authRepository.changePassword(op, np, new AuthRepository.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(getContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    
                    // Update session credentials if necessary
                    sessionManager.saveUserCredentials(currentResidentId, sessionManager.fetchUserEmail(), sessionManager.fetchUserUsername(), np);
                }

                @Override
                public void onError(String error) {
                    btnUpdate.setEnabled(true);
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Add "Forgot Password?" link to the dialog
        TextView forgotLink = new TextView(getContext());
        forgotLink.setText("Forgot Password?");
        forgotLink.setTextColor(getResources().getColor(R.color.green));
        forgotLink.setPadding(0, 24, 0, 0);
        forgotLink.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        forgotLink.setClickable(true);
        forgotLink.setFocusable(true);
        forgotLink.setOnClickListener(v -> {
            dialog.dismiss();
            showSettingsForgotPasswordDialog();
        });
        
        ((ViewGroup) dialogView).addView(forgotLink);

        dialog.show();
    }

    private void showSettingsForgotPasswordDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_settings_forgot_password, null);
        TextInputEditText newPass = dialogView.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmPass = dialogView.findViewById(R.id.confirmNewPasswordInput);
        MaterialButton btnReset = dialogView.findViewById(R.id.btnSettingsResetPassword);
        MaterialButton btnBack = dialogView.findViewById(R.id.btnSettingsForgotBack);

        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnReset.setOnClickListener(v -> {
            String np = newPass.getText().toString().trim();
            String cp = confirmPass.getText().toString().trim();

            if (np.isEmpty() || cp.isEmpty()) {
                Toast.makeText(getContext(), "Please enter and confirm your new password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!np.equals(cp)) {
                Toast.makeText(getContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            btnReset.setEnabled(false);
            authRepository.resetLoggedInPassword(np, new AuthRepository.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(getContext(), "Password reset successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Update saved password in session for biometrics to continue working
                    String residentId = sessionManager.fetchResidentId();
                    String email = sessionManager.fetchUserEmail();
                    String username = sessionManager.fetchUserUsername();
                    sessionManager.saveUserCredentials(residentId, email, username, np);
                    
                    dialog.dismiss();
                }

                @Override
                public void onError(String error) {
                    btnReset.setEnabled(true);
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnBack.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showPrivacyInfo(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got it", null)
                .show();
    }
}

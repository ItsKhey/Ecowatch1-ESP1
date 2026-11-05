package com.example.ecowatchpersonnel1.ui.profile;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.repository.BiometricRepository;
import com.example.ecowatchpersonnel1.data.repository.PersonnelAuthRepository;
import com.example.ecowatchpersonnel1.utils.BiometricTokenVault;
import com.example.ecowatchpersonnel1.utils.SessionManager;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import javax.crypto.Cipher;

/**
 * Security and privacy for the signed-in cleaner.
 *
 * <p>Two real controls and two explanations. The fingerprint row turns an actual
 * app lock on, verified by a biometric scan before it is saved rather than by a
 * switch flip, because a lock that can be switched on by anyone holding the phone
 * is not a lock. Change Password verifies the current password on the server, so
 * a borrowed phone with an open session cannot take the account over.
 *
 * <p>The two privacy rows explain where the coordinates and photos on a task come
 * from rather than pretending to be settings: neither is something this app can
 * change, and a cleaner following the task screen deserves to know that.
 */
public class SecurityPrivacyFragment extends Fragment {

    private SessionManager sessionManager;
    private PersonnelAuthRepository authRepository;
    private BiometricRepository biometricRepository;

    /**
     * Which account this screen is acting for. Read once, from the session, because
     * every database write is keyed by it.
     */
    private String personnelId;

    private View fingerprintRow;
    private MaterialSwitch fingerprintSwitch;
    private View changePasswordRow;

    /**
     * What the database currently says about this account's fingerprint.
     *
     * <p>Cached from {@link #observeRegistration()} because the toggle and the
     * switch both need it on the main thread the instant a tap lands, and Room
     * cannot be queried there.
     */
    private boolean fingerprintRegistered;

    /**
     * Set while a biometric scan is running.
     *
     * <p>Without it, a failed or cancelled scan would fall through to the
     * generic error handler and leave the switch mid-flight, so the user could tap
     * again and stack prompts on top of each other.
     */
    private boolean promptShowing;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_security_privacy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sessionManager = new SessionManager(requireContext());
        authRepository = new PersonnelAuthRepository(requireContext());
        biometricRepository = new BiometricRepository(requireContext());
        personnelId = sessionManager.fetchPersonnelId();

        setupRows(view);
        setupClickListeners(view);
        observeRegistration();
    }

    /**
     * Keeps the switch in step with the database rather than with a local flag.
     *
     * <p>Observed so the row is the single source of truth: a registration written
     * here, or a database restored from a backup, both reach the switch without
     * anything having to reconcile them by hand.
     */
    private void observeRegistration() {
        biometricRepository.observe(personnelId)
                .observe(getViewLifecycleOwner(), setting -> {
                    fingerprintRegistered = setting != null
                            && setting.isFingerprintRegistered();
                    if (fingerprintSwitch != null) {
                        fingerprintSwitch.setChecked(fingerprintRegistered);
                    }
                });
    }

    private void setupRows(View view) {
        fingerprintRow = view.findViewById(R.id.itemFingerprint);
        fingerprintSwitch = fingerprintRow.findViewById(R.id.rowSwitch);
        // Left off until the observer reports what the database holds, so the
        // switch never shows a state the row does not support.

        setupSwitchRow(fingerprintRow, android.R.drawable.ic_lock_idle_lock,
                getString(R.string.security_biometric_title),
                getString(R.string.security_biometric_summary));

        changePasswordRow = view.findViewById(R.id.itemChangePassword);
        setupRow(changePasswordRow, android.R.drawable.ic_menu_edit,
                getString(R.string.security_change_password), null);

        setupRow(view.findViewById(R.id.itemLocationPrivacy),
                android.R.drawable.ic_menu_mylocation,
                getString(R.string.security_location_privacy), null);

        setupRow(view.findViewById(R.id.itemReportPhotos),
                android.R.drawable.ic_menu_camera,
                getString(R.string.security_report_photos), null);
    }

    private void setupClickListeners(View view) {
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        fingerprintRow.setOnClickListener(v -> handleBiometricToggle());

        changePasswordRow.setOnClickListener(v -> showChangePasswordDialog());

        view.findViewById(R.id.itemLocationPrivacy).setOnClickListener(v ->
                showInfo(getString(R.string.security_location_privacy),
                        getString(R.string.security_location_privacy_message)));

        view.findViewById(R.id.itemReportPhotos).setOnClickListener(v ->
                showInfo(getString(R.string.security_report_photos),
                        getString(R.string.security_report_photos_message)));
    }

    // ------------------------------------------------------------ biometric ---

    /**
 * Turns the lock on or off, depending on what the database says.
 *
 * <p>Turning it on needs both the password and a sensor confirmation, so a lock
 * cannot be enabled by anyone merely holding an unlocked phone. Turning it off
 * needs no scan: the phone is already in the account holder's hands, and
 * requiring one would strand anyone whose fingerprint became unreadable.
     */
    private void handleBiometricToggle() {
        if (promptShowing) return;

        // Branches on the value the observer cached rather than reading the row
        // here: this runs on the main thread and Room refuses that. The observer
        // keeps the field current on every database change, and if a tap somehow
        // arrives before its first delivery the value is false, which sends the
        // flow down the registration path — the safe direction to be wrong in.
        if (fingerprintRegistered) {
            // The sealed token goes with it. Leaving it behind would mean the
            // login screen kept offering an unlock for an account the cleaner
            // has just turned the lock off for. Scope the clear to this account:
            // another cleaner on the same shared phone may still have a working
            // fingerprint login.
            BiometricTokenVault.clear(requireContext(), personnelId);
            biometricRepository.unregister(personnelId, () -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.security_biometric_disabled,
                        Toast.LENGTH_SHORT).show();
            });
            return;
        }

        if (!hasFingerprintHardware()) {
            showInfo(getString(R.string.security_biometric_title),
                    getString(R.string.security_no_hardware));
            return;
        }

        int availability = BiometricManager.from(requireContext())
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (availability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            showEnrollmentDialog();
            return;
        }
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(requireContext(), R.string.security_biometric_unavailable,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Hardware is ready, but enrolment is not allowed yet: ask for the
        // password before the scan. Without this the lock could be turned on by
        // anyone holding an unlocked phone, which would make it decoration.
        showConfirmPasswordDialog();
    }

    /**
     * Requires the account password before a fingerprint may be registered.
     *
     * <p>The password is checked by the server, which is the only place the hash
     * exists, and is never written to this device. A successful check then
     * registers the biometric; a wrong one leaves the switch off and says so.
     */
    private void showConfirmPasswordDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_confirm_password, null, false);

        TextInputEditText passwordInput = content.findViewById(R.id.confirmPasswordInput);
        View confirmBtn = content.findViewById(R.id.btnConfirm);

        if (passwordInput == null || confirmBtn == null) {
            Toast.makeText(requireContext(), R.string.security_biometric_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // The layout carries its own heading and explanation, so the dialog adds
        // no title of its own. Its rounded background is cleared so the shape in
        // the layout is what shows, with no white square behind it.
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        confirmBtn.setOnClickListener(v -> {
            String password = text(passwordInput);
            if (password.isEmpty()) {
                Toast.makeText(requireContext(), "Enter your password.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            confirmBtn.setEnabled(false);
            authRepository.verifyPassword(password, new ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    if (!isAdded()) return;
                    confirmBtn.setEnabled(true);
                    passwordInput.setText("");
                    dialog.dismiss();
                    // Password proven: now let the sensor register itself.
                    promptForBiometricRegistration();
                }

                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    confirmBtn.setEnabled(true);
                    restoreSwitch();
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    /**
     * The enrolment scan itself.
     *
     * <p>Reached only after the password was accepted, so a successful scan here
     * means the person asking for the lock is the account holder.
     */
    private void promptForBiometricRegistration() {
        // Carried as the prompt's CryptoObject. The keystore key requires a
        // verified biometric, so the token can only be sealed inside the success
        // callback and only opened after a later scan. That is what lets the
        // fingerprint restore a session the cleaner has signed out of, rather
        // than only reopening one that is already open.
        final Cipher sealingCipher = BiometricTokenVault.createSealingCipher(requireContext());

        promptShowing = true;
        new BiometricPrompt(this, ContextCompat.getMainExecutor(requireContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode,
                                                     @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        promptShowing = false;
                        // Cancelling leaves the lock off, which is the correct
                        // outcome: nothing was registered.
                        restoreSwitch();
                        boolean cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_CANCELED;
                        if (!cancelled && isAdded()) {
                            Toast.makeText(requireContext(), errString, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        promptShowing = false;

                        // Seal the live token first, while the cipher the
                        // prompt authorised is still in hand. A null cipher
                        // means this device has no usable keystore: the lock
                        // still protects the session that is open, it just will
                        // not survive a sign-out.
                        if (sealingCipher != null) {
                            String token = sessionManager.fetchAuthToken();
                            if (token != null && !token.trim().isEmpty()) {
                                BiometricTokenVault.seal(requireContext(), sealingCipher,
                                        personnelId, sessionManager.fetchUsername(), token);
                            }
                        }

                        // Password proven and fingerprint scanned: write the
                        // registration to the database, scoped to this cleaner so a
                        // shared phone cannot hand one account's unlock to another.
                        // The switch is flipped from the database callback rather
                        // than here, so the row is written before the UI claims the
                        // fingerprint exists.
                        biometricRepository.register(personnelId, sessionManager.fetchUsername(),
                                () -> {
                                    if (!isAdded()) return;
                                    if (fingerprintSwitch != null) {
                                        fingerprintSwitch.setChecked(true);
                                    }
                                    Toast.makeText(requireContext(),
                                            R.string.security_biometric_enabled,
                                            Toast.LENGTH_SHORT).show();
                                });
                    }
                })
                .authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.security_biometric_title))
                        .setSubtitle(getString(R.string.security_register_message))
                        .setNegativeButtonText(getString(android.R.string.cancel))
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build(),
                sealingCipher == null ? null
                        : new BiometricPrompt.CryptoObject(sealingCipher));
    }

    /**
     * Puts the switch back to the cached database value.
     *
     * <p>Called after a scan that changed nothing. The observer would correct it a
     * moment later anyway, but doing it immediately stops the switch flickering on
     * for a frame with a fingerprint that was never registered. Reads the cached
     * value rather than the row, because this runs on the main thread.
     */
    private void restoreSwitch() {
        if (fingerprintSwitch != null) {
            fingerprintSwitch.setChecked(fingerprintRegistered);
        }
    }

    private boolean hasFingerprintHardware() {
        return requireContext().getPackageManager()
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT);
    }

    private void showEnrollmentDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.security_not_enrolled_title)
                .setMessage(R.string.security_not_enrolled_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.security_open_settings, (dialog, which) -> {
                    Intent intent;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        intent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                        intent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                BiometricManager.Authenticators.BIOMETRIC_STRONG);
                    } else {
                        intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    }
                    startActivity(intent);
                })
                .show();
    }

    // ------------------------------------------------------------- password ---

    /**
     * Asks for the current password before setting a new one.
     *
     * <p>The current password is checked by the server, never kept on the device.
     * The fields are read inside a try/catch on purpose: a field that failed to
     * inflate would otherwise crash a security screen over a layout mistake.
     */
    private void showChangePasswordDialog() {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null, false);

        TextInputEditText currentInput = content.findViewById(R.id.oldPasswordInput);
        TextInputEditText newInput = content.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmInput = content.findViewById(R.id.confirmPasswordInput);
        View submitBtn = content.findViewById(R.id.btnChangePassword);

        if (currentInput == null || newInput == null || confirmInput == null
                || submitBtn == null) {
            // A layout that failed to inflate must not crash the security screen;
            // ask an administrator instead, which is the real fallback anyway.
            Toast.makeText(requireContext(),
                    "Ask an administrator to reset your password.", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .create();

        submitBtn.setOnClickListener(v -> {
            String current = text(currentInput);
            String updated = text(newInput);
            String confirmed = text(confirmInput);

            if (current.isEmpty() || updated.isEmpty() || confirmed.isEmpty()) {
                Toast.makeText(requireContext(), "All fields are required.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!updated.equals(confirmed)) {
                Toast.makeText(requireContext(), "Passwords do not match.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            // The server enforces 8 to 72 characters; saying so here saves a round
            // trip and tells the cleaner why before they wonder.
            if (updated.length() < 8 || updated.length() > 72) {
                Toast.makeText(requireContext(),
                        "Password must be between 8 and 72 characters.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (updated.equals(current)) {
                Toast.makeText(requireContext(),
                        "The new password must be different from the current one.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            submitBtn.setEnabled(false);
            authRepository.changePassword(current, updated, new ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    if (!isAdded()) return;
                    submitBtn.setEnabled(true);
                    Toast.makeText(requireContext(), result, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }

                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    submitBtn.setEnabled(true);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private static String text(TextInputEditText field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString();
    }

    // ----------------------------------------------------------------- rows ---

    private void setupRow(View row, int iconRes, String title, String value) {
        if (row == null) return;
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleView = row.findViewById(R.id.rowTitle);
        TextView valueView = row.findViewById(R.id.rowValue);

        if (icon != null) icon.setImageResource(iconRes);
        if (titleView != null) titleView.setText(title);
        if (valueView != null) {
            if (value == null) {
                valueView.setVisibility(View.GONE);
            } else {
                valueView.setText(value);
                valueView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void setupSwitchRow(View row, int iconRes, String title, String description) {
        if (row == null) return;
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleView = row.findViewById(R.id.rowTitle);
        TextView descriptionView = row.findViewById(R.id.rowDescription);

        if (icon != null) icon.setImageResource(iconRes);
        if (titleView != null) titleView.setText(title);
        if (descriptionView != null) {
            if (description == null) {
                descriptionView.setVisibility(View.GONE);
            } else {
                descriptionView.setText(description);
                descriptionView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.settings_got_it, null)
                .show();
    }
}
package com.example.ecowatchpersonnel1.ui.auth;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.remote.RetrofitClient;
import com.example.ecowatchpersonnel1.data.remote.ServerConfig;
import com.example.ecowatchpersonnel1.data.repository.BiometricRepository;
import com.example.ecowatchpersonnel1.notifications.FcmTokenSyncScheduler;
import com.example.ecowatchpersonnel1.ui.home.DashboardActivity;
import com.example.ecowatchpersonnel1.utils.BiometricTokenVault;
import com.example.ecowatchpersonnel1.utils.FingerprintEntryPolicy;
import com.example.ecowatchpersonnel1.utils.SavedAccounts;
import com.example.ecowatchpersonnel1.utils.ServerHealthCheck;
import com.example.ecowatchpersonnel1.utils.SessionManager;

import javax.crypto.Cipher;

/**
 * The only way into the Personnel app, and the only place that asks for identity.
 *
 * <p>A cleaner arrives here either by typing their password or by tapping the
 * fingerprint button, which reopens the token sealed in {@link BiometricTokenVault}
 * after a scan. Whichever they use, this screen settles it: everything downstream
 * trusts the session this produces and asks nothing further. Re-asking on each
 * return to the foreground is what made the fingerprint irritating enough to stop
 * people using it.
 *
 * <p>There is no registration and no password reset: cleaners are created by an
 * administrator, and the password is verified by the backend exactly as it is
 * here.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText usernameInput;
    private EditText passwordInput;
    private LoginViewModel viewModel;
    private SessionManager sessionManager;
    private BiometricRepository biometricRepository;
    private View biometricLoginLayout;

    /** True while the fingerprint prompt is on screen, so taps cannot stack prompts. */
    private boolean biometricPromptShowing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sessionManager = new SessionManager(this);
        biometricRepository = new BiometricRepository(this);
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // A live session goes straight through, unless this device has a fingerprint
        // registered for an account: then the form is shown with the fingerprint
        // button so the lock is satisfied here rather than immediately after.
        //
        // The registration lives in the database, and Room cannot be read on this
        // thread, so the form is built first and this decision arrives
        // asynchronously. The form being visible for that moment is deliberate:
        // it is the same screen the cleaner sees when there is no session at all,
        // so there is no flash of anything they were not already looking at.
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        biometricLoginLayout = findViewById(R.id.biometricLoginLayout);

        observeRegistrations();

        if (viewModel.isLoggedIn()) {
            biometricRepository.hasAnyRegisteredAccount(hasAccount -> {
                if (isFinishing() || isDestroyed()) return;
                if (!hasAccount) startDashboard();
            });
        }

        // The last account this device used is the one most likely to be typed
        // again; the password is never remembered.
        if (usernameInput.getText().toString().trim().isEmpty()) {
            String mostRecent = SavedAccounts.mostRecent(this);
            if (mostRecent != null) {
                usernameInput.setText(mostRecent);
            }
        }

        findViewById(R.id.loginBtn).setOnClickListener(v -> attemptLogin());

        findViewById(R.id.btnFingerprintLogin)
                .setOnClickListener(v -> handleBiometricLogin());

        TextView serverSettingsBtn = findViewById(R.id.serverSettingsBtn);
        serverSettingsBtn.setOnClickListener(v -> showServerSettings());

        viewModel.getError().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getLoginSuccess().observe(this, personnel -> {
            if (personnel == null) return;
            SavedAccounts.remember(this, personnel.username);
            sessionManager.rememberUsername(personnel.username);
            FcmTokenSyncScheduler.syncCurrentToken(this);
            Toast.makeText(this, "Welcome, " + personnel.displayName(), Toast.LENGTH_SHORT).show();

            // This screen is where identity is settled. Nothing downstream asks
            // again, so neither a password nor a fingerprint login is followed by
            // a second prompt on the dashboard.
            startDashboard();
        });
    }

    // ------------------------------------------------------------ fingerprint ---

    /**
     * True when there is a session and a registration this device can use.
     *
     * <p>Both halves matter. The row proves an account registered a fingerprint;
     * the token proves there is a session for it to open. A button that scans
     * successfully and then lands on nothing is worse than no button at all. The
     * token read is preferences, so this is safe to call from the main thread.
     */
    private boolean canUseFingerprintLogin() {
        return FingerprintEntryPolicy.shouldShowButton(
                hasRegisteredFingerprint,
                sessionManager.isLoggedIn(),
                BiometricTokenVault.hasStoredSession(this, null));
    }

    /**
     * Cached answer from the database.
     *
     * <p>Held as a field because the observer delivers it asynchronously and the
     * click handler needs it immediately. Defaulting to false means a tap before
     * the first delivery is treated as "no registration", which fails safe into
     * the password form rather than into a prompt that cannot succeed.
     */
    private boolean hasRegisteredFingerprint;

    /**
     * Reveals the button as soon as the database says a registration exists.
     *
     * <p>Observed rather than read once, so registering a fingerprint in Security
     * &amp; Privacy shows the button here with nothing having to hand the two
     * screens a message.
     */
    private void observeRegistrations() {
        biometricRepository.observeRegisteredAccounts().observe(this, accounts -> {
            hasRegisteredFingerprint = accounts != null && !accounts.isEmpty();
            showBiometricButton(canUseFingerprintLogin());
        });
    }

    /**
     * Shows or hides the fingerprint button.
     *
     * <p>Hidden unless the sensor can actually authenticate. A fingerprint that
     * stopped registering would leave a button that scans successfully and then
     * fails, which is worse than making the cleaner use the password form.
     */
    private void showBiometricButton(boolean canUse) {
        if (biometricLoginLayout == null) return;

        int availability = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        boolean usable = canUse && availability == BiometricManager.BIOMETRIC_SUCCESS;

        biometricLoginLayout.setVisibility(usable ? View.VISIBLE : View.GONE);
    }

    /**
     * Rebuilds a signed-out session from the sealed token.
     *
     * <p>The cipher is only obtainable once a biometric has been verified, which is
     * what the {@code CryptoObject} carries. So the flow is: build the cipher, ask
     * for the scan, and open the token inside the success callback. A null cipher
     * means the key was invalidated — most often because a new fingerprint was
     * enrolled — and the ciphertext is then unrecoverable, so it is dropped and the
     * cleaner falls back to the password form rather than being offered a button
     * that can never work.
     */
    private void restoreSessionFromVault() {
        final Cipher openingCipher = BiometricTokenVault.createOpeningCipher(this);

        if (openingCipher == null) {
            BiometricTokenVault.discardUnopenable(this);
            Toast.makeText(this, "Your fingerprint changed. Sign in with your password.",
                    Toast.LENGTH_LONG).show();
            observeRegistrations();
            return;
        }

        biometricPromptShowing = true;
        new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        biometricPromptShowing = false;

                        String token = BiometricTokenVault.open(LoginActivity.this,
                                openingCipher);
                        String personnelId = BiometricTokenVault.storedPersonnelId(
                                LoginActivity.this);

                        if (token == null || personnelId == null
                                || personnelId.trim().isEmpty()) {
                            // The scan succeeded but the token would not open. The
                            // keystore says this device should be able to, so treat
                            // it as unrecoverable rather than retrying forever.
                            BiometricTokenVault.discardUnopenable(LoginActivity.this);
                            Toast.makeText(LoginActivity.this,
                                    "Could not unlock this account. Sign in with your password.",
                                    Toast.LENGTH_LONG).show();
                            showBiometricButton(false);
                            return;
                        }

                        sessionManager.saveSession(token, personnelId.trim());
                        Toast.makeText(LoginActivity.this,
                                R.string.toast_biometric_login_success, Toast.LENGTH_SHORT).show();
                        FcmTokenSyncScheduler.syncCurrentToken(LoginActivity.this);
                        startDashboard();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                     @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        biometricPromptShowing = false;
                        boolean cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_CANCELED;
                        if (!cancelled) {
                            Toast.makeText(LoginActivity.this, errString,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.security_fingerprint_login_title))
                        .setSubtitle(vaultAccountSubtitle())
                        .setNegativeButtonText(getString(android.R.string.cancel))
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build(),
                        new BiometricPrompt.CryptoObject(openingCipher));
    }

    /**
     * Names the account the scan will open.
     *
     * <p>Worth saying out loud: the vault holds one account, and on a shared phone
     * a cleaner tapping this needs to know it is opening their own.
     */
    private String vaultAccountSubtitle() {
        String username = BiometricTokenVault.storedUsername(this);
        if (username != null && !username.trim().isEmpty()) {
            return getString(R.string.security_fingerprint_login_for, username.trim());
        }
        return getString(R.string.security_fingerprint_login_subtitle);
    }

    /**
     * Unlocks the session that is already on this device.
     *
     * <p>No credentials are involved: the fingerprint only proves who is holding
     * the phone, and the token was stored when the cleaner last signed in with
     * their password. That is why this app can offer fingerprint entry without
     * ever saving a password, unlike the resident app which keeps one.
     */
    private void handleBiometricLogin() {
        if (biometricPromptShowing) return;

        if (!canUseFingerprintLogin()) {
            Toast.makeText(this, "Sign in with your password first.", Toast.LENGTH_SHORT).show();
            showBiometricButton(false);
            return;
        }

        // Only after a sign-out is there a sealed token to open. With a live
        // session the plain prompt below is enough, and this avoids a keystore
        // round trip on every unlock.
        if (FingerprintEntryPolicy.needsVaultUnlock(sessionManager.isLoggedIn(),
                BiometricTokenVault.hasStoredSession(this, null))) {
            restoreSessionFromVault();
            return;
        }

        int availability = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            // A fingerprint that stopped registering must not lock the cleaner
            // out, so the button steps aside and the password form stays. The row
            // is left alone: turning the sensor back on should not cost the
            // cleaner their password prompt all over again.
            Toast.makeText(this, biometricUnavailableMessage(availability),
                    Toast.LENGTH_LONG).show();
            showBiometricButton(false);
            return;
        }

        biometricPromptShowing = true;
        new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        biometricPromptShowing = false;
                        // The scan itself is the login. Nothing downstream asks
                        // again, so there is no pass to hand to the dashboard.
                        Toast.makeText(LoginActivity.this,
                                R.string.toast_biometric_login_success, Toast.LENGTH_SHORT).show();
                        FcmTokenSyncScheduler.syncCurrentToken(LoginActivity.this);
                        startDashboard();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                     @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        biometricPromptShowing = false;
                        // Cancelling is a normal choice: stay on the form with no
                        // complaint. A real failure is worth reporting.
                        boolean cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_CANCELED;
                        if (!cancelled) {
                            Toast.makeText(LoginActivity.this, errString,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.security_fingerprint_login_title))
                        .setSubtitle(getString(R.string.security_fingerprint_login_subtitle))
                        .setNegativeButtonText(getString(android.R.string.cancel))
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .build());
    }

    /** Says why the button is unavailable, in terms the cleaner can act on. */
    private String biometricUnavailableMessage(int availability) {
        if (availability == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return "No fingerprint is registered on this device. Sign in with your "
                    + "password and turn the fingerprint option back on in Settings.";
        }
        if (availability == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            return "The fingerprint sensor is busy. Try again, or sign in with your "
                    + "password.";
        }
        return "This device cannot use fingerprint login. Sign in with your password.";
    }

    private void attemptLogin() {
        String identifier = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (TextUtils.isEmpty(identifier) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.toast_login_missing_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // Said before the request rather than after: a spinner-free screen on a
        // slow connection looks broken, and the cleaner is left tapping again.
        Toast.makeText(this, R.string.toast_logging_in, Toast.LENGTH_SHORT).show();
        viewModel.login(identifier, password);
    }

    /**
     * Lets the server address be corrected without a rebuild.
     *
     * <p>The address is compiled in, which is right until the laptop's DHCP
     * address changes; because that is the most likely reason a working install
     * stops working, it is fixable from here.
     */
    private void showServerSettings() {
        View content = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        EditText input = content.findViewById(R.id.serverAddressInput);
        TextView status = content.findViewById(R.id.serverStatusText);
        input.setText(ServerConfig.editableAddress(this));

        View findBtn = content.findViewById(R.id.findServerBtn);
        TextView scanStatus = content.findViewById(R.id.scanStatusText);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Server settings")
                .setView(content)
                .setPositiveButton("Save", null)
                .setNeutralButton("Reset", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String typed = input.getText().toString().trim();
                if (typed.isEmpty()) {
                    applyServerChange(null, dialog,
                            "Using the address this app was built with.");
                    return;
                }
                ServerHealthCheck.probe(this, typed, result -> runOnUiThread(() -> {
                    if (!result.isReachable()) {
                        new AlertDialog.Builder(this)
                                .setTitle("Cannot reach that address")
                                .setMessage(result.getSummary() + "\n\nSave it anyway?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Save anyway", (d2, w2) ->
                                        applyServerChange(typed, dialog, null))
                                .show();
                        return;
                    }
                    applyServerChange(typed, dialog, result.getSummary());
                }));
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    applyServerChange(null, dialog,
                            "Using the address this app was built with."));

            if (findBtn != null) {
                findBtn.setOnClickListener(v ->
                        scanForServer(findBtn, scanStatus, input, status));
            }
        });

        dialog.show();
    }

    private void scanForServer(View findBtn, TextView scanStatus, EditText input, TextView status) {
        findBtn.setEnabled(false);
        scanStatus.setVisibility(View.VISIBLE);
        scanStatus.setText("Searching this network...");

        final int port = ServerConfig.portOf(ServerConfig.editableAddress(this));

        ServerHealthCheck.scanLocalNetwork(port, found -> runOnUiThread(() -> {
            findBtn.setEnabled(true);

            if (found.isEmpty()) {
                scanStatus.setText("No EcoWatch server found. Check that the server is "
                        + "running and that this phone is on the same network.");
                return;
            }
            if (found.size() == 1) {
                input.setText(found.get(0));
                scanStatus.setText("Found " + found.get(0) + ". Tap Save to use it.");
                status.setText("");
                return;
            }

            scanStatus.setText("Found " + found.size() + ". Choose one:");
            final String[] options = found.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("Which server?")
                    .setItems(options, (d, which) -> {
                        input.setText(options[which]);
                        scanStatus.setText("Selected " + options[which] + ". Tap Save.");
                    })
                    .show();
        }));
    }

    private void applyServerChange(String address, AlertDialog dialog, String message) {
        if (address == null) {
            ServerConfig.clearOverride(this);
        } else {
            String problem = ServerConfig.setOverride(this, address);
            if (problem != null) {
                new AlertDialog.Builder(this)
                        .setTitle("Invalid address")
                        .setMessage(problem)
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
        }

        RetrofitClient.reset();
        dialog.dismiss();
        new AlertDialog.Builder(this)
                .setTitle("Server updated")
                .setMessage(message == null ? "Server address saved." : message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void startDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}

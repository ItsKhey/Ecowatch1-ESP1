package com.example.myecowatch1.ui.auth;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.content.pm.PackageManager;
import androidx.biometric.BiometricManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.BiometricSetting;
import com.example.myecowatch1.data.remote.RetrofitClient;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.notifications.FcmTokenSyncScheduler;
import com.example.myecowatch1.ui.home.DashboardActivity;
import com.example.myecowatch1.utils.LocaleHelper;
import com.example.myecowatch1.utils.SavedAccounts;
import com.example.myecowatch1.utils.ServerHealthCheck;
import com.example.myecowatch1.utils.SessionManager;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    /**
     * Optional hint carrying the username a resident just registered or reset.
     *
     * <p>This only ever fills the field. The password is still required and is
     * still verified by the backend exactly as before, so this is a convenience
     * and not a login shortcut.
     */
    public static final String EXTRA_PREFILL_USERNAME = "prefill_username";

    private EditText emailInput, passwordInput;
    private Button loginBtn;
    private MaterialCheckBox rememberMeCheckBox;
    private View btnFingerprintLogin;
    private View biometricButtonsLayout;
    private LoginViewModel viewModel;
    private SessionManager sessionManager;
    private boolean isBiometricLogin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sessionManager = new SessionManager(this);
        LocaleHelper.applyLocale(this, sessionManager.getLanguage());

        // Force light mode for login screen as requested
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        if (viewModel.isLoggedIn()) {
            startDashboard();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        rememberMeCheckBox = findViewById(R.id.rememberMeCheckBox);

        // Start from whatever the resident last chose, so the choice is a device
        // preference that sticks rather than a question asked at every sign-in.
        if (rememberMeCheckBox != null) {
            rememberMeCheckBox.setChecked(sessionManager.isRememberMe());
        }

        applyUsernamePrefill(getIntent());

        biometricButtonsLayout = findViewById(R.id.biometricButtonsLayout);
        btnFingerprintLogin = findViewById(R.id.btnFingerprintLogin);

        sessionManager.syncBiometricsFromDb(null, () -> {
            runOnUiThread(this::updateBiometricButtonVisibility);
        });

        loginBtn.setOnClickListener(v -> {
            isBiometricLogin = false;
            String identifier = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            if (identifier.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show();
                return;
            }
            // Recorded before the request so the repository's saveSession reads
            // the choice the resident just made.
            sessionManager.setRememberMe(rememberMeCheckBox == null
                    || rememberMeCheckBox.isChecked());
            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show();
            viewModel.login(identifier, password);
        });

        btnFingerprintLogin.setOnClickListener(v -> handleBiometricLogin("fingerprint"));

        viewModel.getError().observe(this, error -> {
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getLoginSuccess().observe(this, user -> {
            if (user != null) {
                Toast.makeText(this, "Logged in Successfully", Toast.LENGTH_SHORT).show();
                
                String officialEmail = user.getEmail();
                String officialUsername = user.getUsername();
                String officialId = user.getServerId();
                String password = passwordInput.getText().toString().trim();

                /*
                 * Only when a password was actually typed. This observer also
                 * fires for a sign-in restored from a remembered session, where
                 * the field is empty by definition; saving that empty string
                 * would erase whatever the biometric record already holds for
                 * this account.
                 */
                if (!password.isEmpty()) {
                    sessionManager.saveUserCredentials(
                            officialId, officialEmail, officialUsername, password);
                }

                // Remember the username so the next sign-in does not mean typing
                // it again. The password is deliberately not part of this.
                //
                // Gated on the same choice the register form offers, so
                // "remember me on this device" means the same thing wherever it
                // is answered. Saving unconditionally here would put the account
                // back into the picker the resident just declined to keep.
                if (sessionManager.isRememberMe()) {
                    // Two things, not one: the username puts the account in the
                    // picker, and the token is what lets choosing it sign in
                    // without the password being typed again.
                    SavedAccounts.rememberWithSession(
                            this,
                            officialUsername,
                            officialId,
                            sessionManager.fetchAuthToken());
                }

                sessionManager.syncBiometricsFromDb(officialId, () -> {
                    sessionManager.saveResidentId(officialId);
                    FcmTokenSyncScheduler.syncCurrentToken(this);
                    runOnUiThread(this::startDashboard);
                });
            }
        });

        TextView toRegisterBtn = findViewById(R.id.toRegisterBtn);
        toRegisterBtn.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        TextView forgotPasswordBtn = findViewById(R.id.forgotPasswordBtn);
        forgotPasswordBtn.setOnClickListener(v -> showForgotPasswordFragment());

        TextView serverSettingsBtn = findViewById(R.id.serverSettingsBtn);
        if (serverSettingsBtn != null) {
            serverSettingsBtn.setOnClickListener(v -> showServerSettings());
        }

        setupSavedAccounts();
    }

    /**
     * Pre-fills the login form with the account used most recently, and wires the
     * icon in the username field that opens the account list.
     *
     * <p>Registration creates the account immediately in this build and drops the
     * resident back here to try it, so the username they just chose is the one
     * thing guaranteed to be worth pre-filling. The password is never stored or
     * pre-filled.
     *
     * <p>Runs after {@link #applyUsernamePrefill}, so a username handed over by an
     * intent wins over the remembered one.
     *
     * <p>This used to also configure a floating button that opened a separate
     * sheet of remembered accounts. That sheet and the button are gone: the same
     * accounts are now listed in the one account dialog, alongside the
     * fingerprint accounts, so there is a single list to learn instead of two
     * that behaved differently.
     */
    private void setupSavedAccounts() {
        // Deliberately does not overwrite anything already typed, which includes
        // a username supplied by an intent.
        String mostRecent = SavedAccounts.mostRecent(this);
        if (mostRecent != null && emailInput != null
                && emailInput.getText().toString().trim().isEmpty()) {
            emailInput.setText(mostRecent);
        }

        TextInputLayout usernameLayout = findViewById(R.id.usernameLayout);
        if (usernameLayout != null) {
            // The icon, not the whole field. A listener on the edit text would
            // fire on every attempt to correct a typo and make the field
            // effectively read-only.
            usernameLayout.setEndIconOnClickListener(v -> showAccountSelectionDialog("password"));
        }
    }

    /**
     * Signs in with the session stored for a remembered account.
     *
     * <p>Goes through the ViewModel rather than reusing the local session, because
     * the token has to be checked with the server: it carries a 30-day expiry, and
     * trusting it blindly would drop the resident onto a dashboard whose first
     * request fails.
     *
     * <p>A token the server rejects is discarded by the ViewModel and the password
     * field takes over, so a remembered account that has quietly expired degrades
     * into a normal sign-in instead of being a dead row in the list.
     */
    private void loginWithRememberedSession(String username, SavedAccounts.Session session) {
        Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show();
        viewModel.loginWithStoredSession(session.token, session.residentId);
    }

    /**
     * Lets the server address be corrected without rebuilding the app.
     *
     * <p>The address is compiled in at build time, which is right for normal use
     * but wrong the moment this machine's DHCP address changes. Because that is the
     * single most likely reason a working install stops working, it is fixable
     * from here rather than only from a build.
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
            // Set the listener after show(), otherwise the default button
            // handler dismisses the dialog before the address is applied.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String typed = input.getText().toString().trim();

                if (typed.isEmpty()) {
                    applyServerChange(null, dialog, "Using the address this app was built with.");
                    return;
                }

                ServerHealthCheck.probe(this, typed, result ->
                        runOnUiThread(() -> {
                            if (!result.isReachable()) {
                                // Offer to keep it anyway: a server can be
                                // reachable from the laptop and not from this
                                // phone, and the typed value may be correct.
                                new AlertDialog.Builder(this)
                                        .setTitle("Cannot reach that address")
                                        .setMessage(result.getSummary()
                                                + "\n\nSave it anyway?")
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
                    applyServerChange(null, dialog, "Using the address this app was built with."));

            if (findBtn != null) {
                findBtn.setOnClickListener(v -> scanForServer(findBtn, scanStatus, input, status));
            }
        });

        dialog.show();
    }

    /**
     * Searches the local network for a running EcoWatch server and offers what it
     * finds.
     *
     * <p>The reason this exists: the development machine's address is assigned by
     * whichever network the laptop is on, so it changes every time the laptop joins
     * a different WiFi. Reading it off the laptop is possible, but awkward when you
     * are holding the phone, which is exactly the situation at a demonstration.
     */
    private void scanForServer(View findBtn, TextView scanStatus, EditText input, TextView status) {
        findBtn.setEnabled(false);
        scanStatus.setVisibility(View.VISIBLE);
        scanStatus.setText("Searching this network...");

        final int port = ServerConfig.portOf(ServerConfig.editableAddress(this));

        ServerHealthCheck.scanLocalNetwork(port, found ->
                runOnUiThread(() -> {
                    findBtn.setEnabled(true);

                    if (found.isEmpty()) {
                        scanStatus.setText("No EcoWatch server found. Check the server is "
                                + "running, and that this phone is on the same network as the "
                                + "laptop.");
                        return;
                    }

                    if (found.size() == 1) {
                        // The common case, and unambiguous: just take it.
                        input.setText(found.get(0));
                        scanStatus.setText("Found " + found.get(0) + ". Tap Save to use it.");
                        status.setText("");
                        return;
                    }

                    // More than one is genuinely ambiguous, so let the resident
                    // pick rather than guessing which machine is the laptop.
                    scanStatus.setText("Found " + found.size() + ". Choose one:");
                    final String[] options = found.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("Which server?")
                            .setItems(options, (d, which) -> {
                                input.setText(options[which]);
                                scanStatus.setText("Selected " + options[which]
                                        + ". Tap Save to use it.");
                            })
                            .show();
                }));
    }

    /**
     * Stores a new address and discards the cached HTTP client so the next request
     * uses it. Passing null clears any override and returns to the build default.
     */
    private void applyServerChange(String address, AlertDialog dialog, String message) {        if (address == null) {
            ServerConfig.clearOverride(this);
        } else {
            String problem = ServerConfig.setOverride(this, address);
            if (problem != null) {
                showMessage("Invalid address", problem);
                return;
            }
        }

        RetrofitClient.reset();
        dialog.dismiss();
        showMessage("Server updated", message == null ? "Server address saved." : message);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Fills the username field when the screen was opened from registration or a
     * password reset.
     *
     * <p>The field is only populated. Nothing here bypasses the password check,
     * which remains the single gate into the app and is unchanged.
     */
    private void applyUsernamePrefill(Intent intent) {
        if (intent == null || emailInput == null) {
            return;
        }

        String username = intent.getStringExtra(EXTRA_PREFILL_USERNAME);
        if (username != null && !username.trim().isEmpty()) {
            emailInput.setText(username.trim());
            // Consumed so a later return to this screen does not overwrite
            // whatever the resident has since typed.
            intent.removeExtra(EXTRA_PREFILL_USERNAME);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyUsernamePrefill(intent);
    }

    private void handleBiometricLogin(String type) {
        // 1. Hardware check
        boolean hasSensor = getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        if (!hasSensor) {
            showMessage("Fingerprint login isn't available",
                    "This device has no fingerprint sensor. Please log in with your password.");
            return;
        }

        // 2. Enrollment / availability check
        BiometricManager bm = BiometricManager.from(this);
        int result = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        switch (result) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                break;

            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                showMessage("No fingerprint set up",
                        "Add a fingerprint in Settings > Security > Fingerprint, then try again.");
                return;

            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                showMessage("Sensor unavailable",
                        "The fingerprint sensor is busy or unavailable right now. Try again later or use your password.");
                return;

            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            default:
                showMessage("Fingerprint login isn't available",
                        "This device doesn't support fingerprint login. Please use your password.");
                return;
        }

        // Opens the same merged dialog the username field's icon opens. The two
        // previous "no fingerprint accounts enabled" toasts are gone: the dialog
        // now lists remembered accounts too, so this tap is never a dead end. A
        // remembered account fills the username and the resident types the
        // password, which is exactly what those toasts used to tell them to do.
        showAccountSelectionDialog(type);
    }

    private void showForgotPasswordFragment() {
        findViewById(R.id.loginLayout).setVisibility(View.GONE);
        findViewById(R.id.fragmentContainer).setVisibility(View.VISIBLE);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new ForgotPasswordFragment())
                .commit();
    }

    /**
     * Lists every account this device knows about, and signs in with whichever is
     * chosen.
     *
     * <p>One list instead of two. It merges the two stores that used to be reached
     * separately:
     *
     * <ul>
     *   <li>{@link SavedAccounts} holds usernames from registering or signing in on
     *       this device. No password is stored, so choosing one only fills the
     *       username and the resident types the password.</li>
     *   <li>{@link BiometricSetting} holds accounts whose owner opted in to
     *       fingerprint login. These do store a password, so choosing one opens
     *       the fingerprint prompt.</li>
     * </ul>
     *
     * <p>An account can be in both stores. It is listed once, as the fingerprint
     * row, because that is the stronger of the two: it can actually complete the
     * sign-in on its own.
     *
     * <p>The biometric list arrives on a background thread, so the dialog opens
     * first and fills in when the query returns. That keeps it responsive on a
     * device with a slow disk.
     *
     * @param type "fingerprint" when opened from the fingerprint button, anything
     *             else when opened from the username field. Only ever used to
     *             filter which biometric rows qualify.
     */
    private void showAccountSelectionDialog(String type) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_account_selection, null);
        RecyclerView rvAccounts = dialogView.findViewById(R.id.rvAccounts);
        TextView noAccounts = dialogView.findViewById(R.id.tvNoAccounts);
        TextView subtitle = dialogView.findViewById(R.id.tvAccountsSubtitle);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // The adapter owns the merged list, so a Remove can refresh the dialog in place
        // without this callback having to reach back into it.
        AccountSelectionAdapter adapter = new AccountSelectionAdapter(this, type,
                new AccountSelectionAdapter.Listener() {
                    @Override
                    public void onAccountChosen(AccountRow row) {
                        if (row.setting != null) {
                            // Fingerprint accounts have a stored password, so this
                            // can go all the way through without further input.
                            dialog.dismiss();
                            showBiometricPrompt(row.setting.getResidentId(),
                                    row.setting.getSavedUsername(),
                                    row.setting.getSavedPassword(), type);
                            return;
                        }

                        /*
                         * A remembered account. When "remember me on this device"
                         * was accepted at sign-in, the session that sign-in
                         * produced is stored alongside the username, so choosing
                         * the account signs in without a password.
                         *
                         * Absent a stored session the account still appears here,
                         * because the username alone is worth offering; it just
                         * cannot go further on its own.
                         */
                        SavedAccounts.Session session =
                                SavedAccounts.sessionFor(LoginActivity.this, row.username);

                        dialog.dismiss();
                        if (session != null) {
                            loginWithRememberedSession(row.username, session);
                        } else {
                            emailInput.setText(row.username);
                            passwordInput.requestFocus();
                        }
                    }

                    @Override
                    public void onAccountRemoved(String username, int remaining) {
                        // The adapter has already dropped the account from
                        // SavedAccounts and refreshed itself. Two things left:
                        // stop the form holding a username that no longer exists,
                        // and re-check the empty state, since removing the last
                        // account leaves the list with nothing in it.
                        if (username.equals(emailInput.getText().toString().trim())) {
                            emailInput.setText("");
                        }
                        updateEmptyState(remaining == 0, rvAccounts, subtitle, noAccounts);
                    }
                });

        rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        rvAccounts.setAdapter(adapter);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // The biometric rows are read on a background thread, so the dialog is
        // already on screen by the time they arrive.
        sessionManager.getAllEnabledBiometrics(settings -> runOnUiThread(() -> {
            if (!dialog.isShowing()) {
                return;
            }

            adapter.submit(SavedAccounts.usernames(LoginActivity.this), settings);
            updateEmptyState(adapter.getItemCount() == 0, rvAccounts, subtitle, noAccounts);
        }));
    }

    /**
     * Shows either the list or the "no accounts" message, never both.
     *
     * <p>Called after the first load and again after every Remove, because the
     * list can empty out under the resident's finger.
     */
    private static void updateEmptyState(boolean isEmpty,
                                         RecyclerView list,
                                         TextView subtitle,
                                         TextView empty) {
        list.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        subtitle.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    /**
     * Whether a stored account is usable for the way the dialog was opened.
     *
     * <p>The dialog is reached both from the fingerprint button and from the plain
     * username field. Only the fingerprint button should promise a fingerprint
     * sign-in, so it leaves out face-only records rather than opening a prompt
     * this device cannot satisfy.
     */
    private static boolean matchesBiometricType(BiometricSetting setting, String type) {
        if (!"fingerprint".equals(type)) {
            return true;
        }
        return setting.isFingerprintEnabled() || setting.isEnabled();
    }

    /**
     * Finds a biometric record by username, case-insensitively.
     *
     * <p>Case-insensitive because the resident types this username by hand and may
     * well not match the capitalisation it was registered with, and the two
     * strings come from different stores.
     */
    private static BiometricSetting findByUsername(List<BiometricSetting> settings, String username) {
        for (BiometricSetting setting : settings) {
            String saved = setting.getSavedUsername();
            if (saved != null && saved.trim().equalsIgnoreCase(username.trim())) {
                return setting;
            }
        }
        return null;
    }

    /**
     * One row in the account dialog.
     *
     * <p>The single {@code setting} field is what makes a row behave in one of two
     * ways: non-null means the row carries a stored password and taps open the
     * fingerprint prompt, null means it only fills the username.
     */
    private static final class AccountRow {
        final String username;
        final BiometricSetting setting;

        AccountRow(String username, BiometricSetting setting) {
            this.username = username;
            this.setting = setting;
        }
    }

    /**
     * Renders the merged account list and owns it.
     *
     * <p>Holding the merge here rather than in the activity means a Remove can
     * rebuild the list in place, from data the adapter already has, instead of the
     * dialog having to be reopened.
     */
    private static class AccountSelectionAdapter extends RecyclerView.Adapter<AccountSelectionAdapter.ViewHolder> {

        /** Told which account was chosen, or which remembered one was dropped. */
        interface Listener {
            void onAccountChosen(AccountRow row);

            /**
             * @param remaining how many rows are left, so the dialog can show its
             *                   empty state without needing this adapter back
             */
            void onAccountRemoved(String username, int remaining);
        }

        private final Context context;
        private final String type;
        private final Listener listener;

        private final List<AccountRow> rows = new ArrayList<>();

        /** Kept apart from the rows so a Remove can rebuild without re-querying. */
        private final List<BiometricSetting> settings = new ArrayList<>();

        AccountSelectionAdapter(Context context, String type, Listener listener) {
            this.context = context.getApplicationContext();
            this.type = type;
            this.listener = listener;
        }

        /**
         * Loads both stores and rebuilds the list.
         *
         * @param remembered usernames from {@link SavedAccounts}, most recent first
         * @param biometric  fingerprint rows, which may be null if the query failed
         */
        void submit(List<String> remembered, List<BiometricSetting> biometric) {
            settings.clear();
            if (biometric != null) {
                for (BiometricSetting setting : biometric) {
                    if (setting != null && matchesBiometricType(setting, type)) {
                        settings.add(setting);
                    }
                }
            }
            rebuild(remembered);
        }

        /**
         * Drops a remembered account and refreshes the visible list.
         *
         * <p>Read back from {@link SavedAccounts} rather than filtered locally, so
         * the list matches what is actually stored even if the username was
         * written with different whitespace.
         *
         * <p>Fingerprint rows survive: that is a separate store which the owner
         * opted into deliberately, and it is not this dialog's to delete.
         */
        private void forget(String username) {
            SavedAccounts.forget(context, username);
            rebuild(SavedAccounts.usernames(context));
            listener.onAccountRemoved(username, rows.size());
        }

        private void rebuild(List<String> remembered) {
            rows.clear();

            if (remembered != null) {
                for (String username : remembered) {
                    if (username == null || username.trim().isEmpty()) {
                        continue;
                    }
                    // Non-null when this account also has a fingerprint record, so
                    // it is listed once and signs in by fingerprint.
                    rows.add(new AccountRow(username, findByUsername(settings, username)));
                }
            }

            for (BiometricSetting setting : settings) {
                String username = setting.getSavedUsername();
                if (username == null || username.trim().isEmpty() || contains(username)) {
                    continue;
                }
                rows.add(new AccountRow(username, setting));
            }

            notifyDataSetChanged();
        }

        /** Whether the list already shows this account, so it is not listed twice. */
        private boolean contains(String username) {
            for (AccountRow row : rows) {
                if (row.username.equalsIgnoreCase(username)) {
                    return true;
                }
            }
            return false;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AccountRow row = rows.get(position);

            holder.tvUsername.setText(row.username);

            boolean fingerprint = row.setting != null;
            holder.tag.setVisibility(fingerprint ? View.VISIBLE : View.GONE);
            // Only remembered accounts can be un-remembered here. A fingerprint
            // record is a deliberate opt-in that stores a password, so taking it
            // away belongs to the security screen, not the login screen.
            holder.remove.setVisibility(fingerprint ? View.GONE : View.VISIBLE);

            holder.itemView.setOnClickListener(v -> listener.onAccountChosen(row));
            holder.remove.setOnClickListener(v -> forget(row.username));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvUsername;
            final TextView tag;
            final TextView remove;

            ViewHolder(@NonNull View itemView) {
                super(itemView);

                tvUsername = itemView.findViewById(R.id.tvEmail);
                tag = itemView.findViewById(R.id.tvAccountTag);
                remove = itemView.findViewById(R.id.tvRemoveAccount);
            }
        }
    }

    private void showBiometricPrompt(String residentId, String username, String password, String type) {
        String title = "Fingerprint Login";
        
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Log in as " + username)
                .setNegativeButtonText("Use Password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                            Toast.makeText(LoginActivity.this, "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        isBiometricLogin = true;
                        
                        emailInput.setText(username);
                        passwordInput.setText(password);
                        
                        Toast.makeText(LoginActivity.this, "Fingerprint Authentication Succeeded!", Toast.LENGTH_SHORT).show();
                        
                        sessionManager.syncBiometricsFromDb(residentId, () -> {
                            runOnUiThread(() -> viewModel.login(username, password));
                        });
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(LoginActivity.this, "Authentication failed. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    private void startDashboard() {
        startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
        finish();
    }

    private void updateBiometricButtonVisibility() {
        boolean hasSensor = getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        if (!hasSensor) {
            biometricButtonsLayout.setVisibility(View.GONE);
            return;
        }

        sessionManager.getAllEnabledBiometrics(settings -> {
            runOnUiThread(() -> {
                if (settings != null && !settings.isEmpty()) {
                    boolean hasFingerprint = false;
                    for (BiometricSetting s : settings) {
                        if (s.isFingerprintEnabled()) hasFingerprint = true;
                    }
                    
                    biometricButtonsLayout.setVisibility(hasFingerprint ? View.VISIBLE : View.GONE);
                    btnFingerprintLogin.setVisibility(hasFingerprint ? View.VISIBLE : View.GONE);
                } else {
                    biometricButtonsLayout.setVisibility(View.GONE);
                }
            });
        });
    }
}

package com.example.ecowatchpersonnel1.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.Personnel;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.remote.ServerConfig;
import com.example.ecowatchpersonnel1.data.repository.PersonnelAuthRepository;
import com.example.ecowatchpersonnel1.ui.auth.LoginActivity;
import com.example.ecowatchpersonnel1.ui.home.DashboardActivity;
import com.example.ecowatchpersonnel1.utils.SessionManager;

/**
 * The cleaner's settings menu: identity at the top, then every screen reachable
 * from it, then sign out.
 *
 * <p>Each row navigates to a real fragment. The destinations are pushed onto the
 * back stack rather than replacing the container permanently, so the hardware
 * back button returns to this menu instead of leaving the app, which is what a
 * person one-handed in the field expects.
 *
 * <p>Theme is the only setting that lives on the device. It is written straight to
 * {@link SessionManager} and applied immediately: switching calls
 * {@code setDefaultNightMode}, which recreates the visible activities, so no extra
 * bookkeeping is needed.
 */
public class SettingsMenuFragment extends Fragment {

    private PersonnelAuthRepository authRepository;
    private SessionManager sessionManager;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView profileImage;
    private TextView profileName, profileEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_menu, container, false);

        authRepository = new PersonnelAuthRepository(requireContext());
        sessionManager = new SessionManager(requireContext());

        initViews(view);
        setupClickListeners(view);
        loadProfile();

        return view;
    }

    private void initViews(View view) {
        profileImage = view.findViewById(R.id.profileImage);
        profileName = view.findViewById(R.id.profileName);
        profileEmail = view.findViewById(R.id.profileEmail);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        setupRow(view.findViewById(R.id.itemManageProfile), R.drawable.ic_profile,
                getString(R.string.settings_account_details), null);
        setupRow(view.findViewById(R.id.itemActivityHistory), R.drawable.ic_inbox,
                getString(R.string.settings_task_history), null);
        setupRow(view.findViewById(R.id.itemSecurity), android.R.drawable.ic_lock_idle_lock,
                getString(R.string.settings_security_privacy), null);
        // Driver-only in practice: a Regular cleaner taps through to an explanation
        // rather than a truck. Hiding the row instead would make the menu shift as
        // accounts change, and the screen already says why it is empty.
        setupRow(view.findViewById(R.id.vehicle), R.drawable.garbage_truck,
                getString(R.string.settings_vehicle), null);
        setupRow(view.findViewById(R.id.itemNotifications), com.example.ecowatchpersonnel1.core.R.drawable.ic_notification,
                getString(R.string.settings_notifications), null);

        setupRow(view.findViewById(R.id.itemTheme), android.R.drawable.ic_menu_day,
                getString(R.string.settings_theme), themeLabel(sessionManager.getThemeMode()));

        setupRow(view.findViewById(R.id.itemHelpCenter), android.R.drawable.ic_menu_help,
                getString(R.string.settings_help_center), null);
        setupRow(view.findViewById(R.id.itemTerms), android.R.drawable.ic_menu_agenda,
                getString(R.string.settings_terms), null);
        setupRow(view.findViewById(R.id.itemAboutUs), android.R.drawable.ic_menu_info_details,
                getString(R.string.settings_about_us), null);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::loadProfile);
        }
    }

    private void setupClickListeners(View view) {
        View cardHeader = view.findViewById(R.id.cardProfileHeader);
        if (cardHeader != null) {
            cardHeader.setOnClickListener(v -> navigateTo(new ProfileFragment()));
        }

        View itemManageProfile = view.findViewById(R.id.itemManageProfile);
        if (itemManageProfile != null) {
            itemManageProfile.setOnClickListener(v -> navigateTo(new ProfileFragment()));
        }

        View itemActivityHistory = view.findViewById(R.id.itemActivityHistory);
        if (itemActivityHistory != null) {
            itemActivityHistory.setOnClickListener(v -> navigateTo(new HistoryFragment()));
        }

        View itemSecurity = view.findViewById(R.id.itemSecurity);
        if (itemSecurity != null) {
            itemSecurity.setOnClickListener(v -> navigateTo(new SecurityPrivacyFragment()));
        }

        View vehicleRow = view.findViewById(R.id.vehicle);
        if (vehicleRow != null) {
            vehicleRow.setOnClickListener(v -> navigateTo(new VehicleFragment()));
        }

        View itemNotifications = view.findViewById(R.id.itemNotifications);
        if (itemNotifications != null) {
            itemNotifications.setOnClickListener(v -> navigateTo(new NotificationSettingsFragment()));
        }

        View itemTheme = view.findViewById(R.id.itemTheme);
        if (itemTheme != null) {
            itemTheme.setOnClickListener(v -> showThemeSelectionDialog());
        }

        View itemHelpCenter = view.findViewById(R.id.itemHelpCenter);
        if (itemHelpCenter != null) {
            itemHelpCenter.setOnClickListener(v -> showInfo(
                    R.string.settings_help_title, R.string.settings_help_message));
        }

        View itemTerms = view.findViewById(R.id.itemTerms);
        if (itemTerms != null) {
            itemTerms.setOnClickListener(v -> showInfo(
                    R.string.settings_terms_title, R.string.settings_terms_message));
        }

        View itemAboutUs = view.findViewById(R.id.itemAboutUs);
        if (itemAboutUs != null) {
            itemAboutUs.setOnClickListener(v -> navigateTo(new AboutUsFragment()));
        }

        View logoutBtn = view.findViewById(R.id.logoutBtn);
        if (logoutBtn != null) {
            logoutBtn.setOnClickListener(v -> confirmSignOut());
        }
    }

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

    private void navigateTo(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    // ---------------------------------------------------------------- theme ---

    private String themeLabel(int mode) {
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            return getString(R.string.settings_theme_light);
        }
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return getString(R.string.settings_theme_dark);
        }
        return getString(R.string.settings_theme_system);
    }

    private void showThemeSelectionDialog() {
        String[] options = {
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_system)
        };
        int currentTheme = sessionManager.getThemeMode();
        int checkedItem = 2; // System
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) checkedItem = 0;
        else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) checkedItem = 1;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_theme_dialog_title)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    int selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

                    if (which == 0) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                    } else if (which == 1) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                    }

                    sessionManager.setThemeMode(selectedMode);
                    AppCompatDelegate.setDefaultNightMode(selectedMode);

                    if (getView() != null) {
                        setupRow(getView().findViewById(R.id.itemTheme),
                                android.R.drawable.ic_menu_day,
                                getString(R.string.settings_theme),
                                themeLabel(selectedMode));
                    }

                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // -------------------------------------------------------------- profile ---

    /**
     * Fills the identity card.
     *
     * <p>The avatar and name come from the server rather than the session, so a
     * cleaner whose profile was updated by an administrator sees the new one on
     * the next pull. A failure here leaves the previous values on screen: the
     * menu is usable without them and an error toast would be noise.
     */
    private void loadProfile() {
        authRepository.fetchMe(new ApiCallback<Personnel>() {
            @Override
            public void onSuccess(Personnel personnel) {
                if (!isAdded() || personnel == null) return;
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

                if (profileName != null) {
                    profileName.setText(personnel.displayName());
                }
                if (profileEmail != null) {
                    String emailOrUsername = personnel.email;
                    if (emailOrUsername == null || emailOrUsername.trim().isEmpty()) {
                        emailOrUsername = personnel.username;
                    }
                    profileEmail.setText(emailOrUsername != null
                            && !emailOrUsername.trim().isEmpty() ? emailOrUsername : "—");
                }

                String url = personnel.profileImageUrl != null
                        ? personnel.profileImageUrl
                        : personnel.profileImage;
                if (url == null || url.trim().isEmpty()) {
                    if (profileImage != null) profileImage.setImageResource(R.drawable.pfp);
                    return;
                }
                if (profileImage != null) {
                    Glide.with(SettingsMenuFragment.this)
                            .load(ServerConfig.absoluteUrl(requireContext(), url))
                            .placeholder(R.drawable.pfp)
                            .error(R.drawable.pfp)
                            .into(profileImage);
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    // --------------------------------------------------------------- logout ---

    /**
     * Confirms before signing out, because on a shared field phone the person
     * holding it may not be the person signed in.
     */
    private void confirmSignOut() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_log_out)
                .setMessage(R.string.profile_sign_out_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_log_out, (dialog, which) -> signOut())
                .show();
    }

    private void signOut() {
        if (requireActivity() instanceof DashboardActivity) {
            ((DashboardActivity) requireActivity()).signOutAndReturnToLogin(null);
            return;
        }
        authRepository.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showInfo(int titleRes, int messageRes) {
        new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.settings_got_it, null)
                .show();
    }
}
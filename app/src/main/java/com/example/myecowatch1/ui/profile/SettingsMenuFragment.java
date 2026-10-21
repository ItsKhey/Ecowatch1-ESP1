package com.example.myecowatch1.ui.profile;

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
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.User;
import com.example.myecowatch1.data.repository.AuthRepository;
import com.example.myecowatch1.ui.auth.LoginActivity;
import com.example.myecowatch1.utils.LocaleHelper;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.utils.SessionManager;

public class SettingsMenuFragment extends Fragment {

    private AuthRepository authRepository;
    private SessionManager sessionManager;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageView profileImage;
    private TextView profileName, profileEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings_menu, container, false);

        authRepository = new AuthRepository(requireActivity().getApplication());
        sessionManager = new SessionManager(requireContext());

        initViews(view);
        setupClickListeners(view);
        observeUser();

        return view;
    }

    private void initViews(View view) {
        profileImage = view.findViewById(R.id.profileImage);
        profileName = view.findViewById(R.id.profileName);
        profileEmail = view.findViewById(R.id.profileEmail);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        setupRow(view.findViewById(R.id.itemManageProfile), android.R.drawable.ic_menu_edit, "Manage Profile", null);
        setupRow(view.findViewById(R.id.itemActivityHistory), android.R.drawable.ic_menu_recent_history, "Report History", null);
        setupRow(view.findViewById(R.id.itemSecurity), android.R.drawable.ic_lock_idle_lock, "Security & Privacy", null);
        setupRow(view.findViewById(R.id.itemNotifications), R.drawable.ic_notification, "Notifications", null);
        
        int currentTheme = sessionManager.getThemeMode();
        String themeText = "System";
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) themeText = "Light";
        else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) themeText = "Dark";
        
        setupRow(view.findViewById(R.id.itemTheme), android.R.drawable.ic_menu_day, "Theme", themeText);
        
        String currentLang = sessionManager.getLanguage();
        String langText = currentLang.equals("tl") ? "Filipino" : "English";
        setupRow(view.findViewById(R.id.itemLanguage), android.R.drawable.ic_menu_sort_alphabetically, "Language", "— " + langText);
        
        setupRow(view.findViewById(R.id.itemHelpCenter), android.R.drawable.ic_menu_help, "Help Center", null);
        setupRow(view.findViewById(R.id.itemTerms), android.R.drawable.ic_menu_agenda, "Terms & Policies", null);
        setupRow(view.findViewById(R.id.itemAboutUs), android.R.drawable.ic_menu_info_details, "About Us", null);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            authRepository.fetchMe(null);
            swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 2000);
        });
    }

    private void setupRow(View row, int iconRes, String title, String value) {
        if (row == null) return;
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleTv = row.findViewById(R.id.rowTitle);
        TextView valueTv = row.findViewById(R.id.rowValue);

        icon.setImageResource(iconRes);
        titleTv.setText(title);
        if (value != null) {
            valueTv.setText(value);
            valueTv.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners(View view) {
        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        }

        view.findViewById(R.id.itemManageProfile).setOnClickListener(v -> navigateTo(new ProfileFragment()));
        view.findViewById(R.id.itemActivityHistory).setOnClickListener(v -> navigateTo(new HistoryFragment()));
        view.findViewById(R.id.itemSecurity).setOnClickListener(v -> navigateTo(new SecurityPrivacyFragment()));
        view.findViewById(R.id.itemNotifications).setOnClickListener(v -> navigateTo(new NotificationSettingsFragment()));
        view.findViewById(R.id.itemTheme).setOnClickListener(v -> showThemeSelectionDialog());
        view.findViewById(R.id.itemLanguage).setOnClickListener(v -> showLanguageSelectionDialog());
        view.findViewById(R.id.itemHelpCenter).setOnClickListener(v -> comingSoon());
        view.findViewById(R.id.itemTerms).setOnClickListener(v -> comingSoon());
        view.findViewById(R.id.itemAboutUs).setOnClickListener(v -> navigateTo(new AboutUsFragment()));

        view.findViewById(R.id.logoutBtn).setOnClickListener(v -> {
            authRepository.logout();
            Intent intent = new Intent(getContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void navigateTo(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void comingSoon() {
        Toast.makeText(getContext(), "Coming soon....", Toast.LENGTH_SHORT).show();
    }

    private void showThemeSelectionDialog() {
        String[] options = {"Light", "Dark", "System"};
        int currentTheme = sessionManager.getThemeMode();
        int checkedItem = 2; // System
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) checkedItem = 0;
        else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) checkedItem = 1;

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Theme")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    int selectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    String themeName = "System";
                    
                    if (which == 0) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_NO;
                        themeName = "Light";
                    } else if (which == 1) {
                        selectedMode = AppCompatDelegate.MODE_NIGHT_YES;
                        themeName = "Dark";
                    }

                    sessionManager.setThemeMode(selectedMode);
                    AppCompatDelegate.setDefaultNightMode(selectedMode);
                    
                    // Update UI
                    setupRow(getView().findViewById(R.id.itemTheme), android.R.drawable.ic_menu_day, "Theme", themeName);
                    
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLanguageSelectionDialog() {
        String[] options = {"English", "Filipino"};
        String currentLang = sessionManager.getLanguage();
        int checkedItem = currentLang.equals("tl") ? 1 : 0;

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Language")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String selectedLang = (which == 1) ? "tl" : "en";
                    String langName = (which == 1) ? "Filipino" : "English";

                    sessionManager.setLanguage(selectedLang);
                    
                    // Update current row UI
                    setupRow(getView().findViewById(R.id.itemLanguage), 
                            android.R.drawable.ic_menu_sort_alphabetically, 
                            "Language", "— " + langName);

                    // Apply language change and restart activity to see effects
                    LocaleHelper.applyLocale(requireContext(), selectedLang);
                    requireActivity().recreate();
                    
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void observeUser() {
        authRepository.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                String fullName = user.getFirstName() + " " + user.getLastName();
                profileName.setText(fullName);
                profileEmail.setText(user.getEmail());

                if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
                    Glide.with(this).load(ServerConfig.absoluteUrl(requireContext(), user.getProfileImageUrl())).placeholder(R.drawable.pfp).into(profileImage);
                }
            }
        });
    }
}

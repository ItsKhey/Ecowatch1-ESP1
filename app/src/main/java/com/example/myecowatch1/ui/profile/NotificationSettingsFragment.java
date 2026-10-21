package com.example.myecowatch1.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.myecowatch1.R;
import com.example.myecowatch1.data.local.AppDatabase;
import com.example.myecowatch1.data.local.NotificationSettingDao;
import com.example.myecowatch1.data.models.NotificationSetting;
import com.example.myecowatch1.data.repository.NotificationPreferenceRepository;
import com.example.myecowatch1.utils.SessionManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.TextView;
import android.widget.ImageView;

public class NotificationSettingsFragment extends Fragment {

    private static final String TAG = "NotificationSettings";
    private SessionManager sessionManager;
    private NotificationSettingDao dao;
    private NotificationPreferenceRepository preferenceRepository;
    private ExecutorService executor;
    private NotificationSetting settings;

    private MaterialSwitch switchAllowNotifications, switchReportStatus, switchReportCompleted, switchReportDenied, 
                           switchAnnouncements, switchSound, switchVibration;
    private View settingsContentLayout;
    private View rowAllowNotifications, rowReportStatus, rowReportCompleted, rowReportDenied, 
                 rowAnnouncements, rowSound, rowVibration;

    private final ActivityResultLauncher<Intent> soundPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    if (uri != null && settings != null) {
                        settings.setSoundUri(uri.toString());
                        saveSettings();
                        // Synced so the choice survives a reinstall and matches
                        // on the resident's other devices.
                        preferenceRepository.pushSoundUri(uri.toString());
                        Toast.makeText(getContext(), "Notification sound updated", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification_settings, container, false);
        
        sessionManager = new SessionManager(requireContext());
        dao = AppDatabase.getInstance(requireContext()).notificationSettingDao();
        preferenceRepository =
                new NotificationPreferenceRepository(requireActivity().getApplication());
        executor = Executors.newSingleThreadExecutor();

        initViews(view);
        loadSettings();

        return view;
    }

    private void initViews(View view) {
        view.findViewById(R.id.btnBack).setOnClickListener(v -> getParentFragmentManager().popBackStack());

        rowAllowNotifications = view.findViewById(R.id.itemAllowNotifications);
        switchAllowNotifications = rowAllowNotifications.findViewById(R.id.rowSwitch);
        setupRowUI(rowAllowNotifications, R.drawable.ic_notification, "Allow Notifications", "Allow EcoWatch to send cleanup alerts and status reports.");
        
        settingsContentLayout = view.findViewById(R.id.settingsContentLayout);

        rowReportStatus = view.findViewById(R.id.itemReportStatus);
        switchReportStatus = rowReportStatus.findViewById(R.id.rowSwitch);
        setupRowUI(rowReportStatus, R.drawable.ic_edit_pen, "Report Status Updates", "Receive notifications when your report status changes.");

        rowReportCompleted = view.findViewById(R.id.itemReportCompleted);
        switchReportCompleted = rowReportCompleted.findViewById(R.id.rowSwitch);
        setupRowUI(rowReportCompleted, R.drawable.checkbox, "Report Completed", "Notify me when a cleanup has been completed.");

        rowReportDenied = view.findViewById(R.id.itemReportDenied);
        switchReportDenied = rowReportDenied.findViewById(R.id.rowSwitch);
        setupRowUI(rowReportDenied, R.drawable.trashic, "Report Denied", "Notify me when a report is denied.");

        rowAnnouncements = view.findViewById(R.id.itemAnnouncements);
        switchAnnouncements = rowAnnouncements.findViewById(R.id.rowSwitch);
        setupRowUI(rowAnnouncements, R.drawable.logo1, "CENRO Announcements", "Receive environmental announcements and important updates.");

        rowSound = view.findViewById(R.id.itemSound);
        switchSound = rowSound.findViewById(R.id.rowSwitch);
        setupRowUI(rowSound, android.R.drawable.ic_lock_silent_mode_off, "Notification Sound", "Play a sound for new notifications.");

        rowVibration = view.findViewById(R.id.itemVibration);
        switchVibration = rowVibration.findViewById(R.id.rowSwitch);
        setupRowUI(rowVibration, android.R.drawable.ic_lock_idle_alarm, "Vibration", "Vibrate device for incoming alerts.");

        switchAllowNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // If already enabled in settings object, just show layout
                if (settings != null && settings.isMasterEnabled()) {
                    settingsContentLayout.setVisibility(View.VISIBLE);
                    return;
                }

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Allow Push Notifications")
                        .setMessage("Do you want to allow EcoWatch to send high priority clean up alerts and task status reports?")
                        .setPositiveButton("Allow", (dialog, which) -> {
                            settingsContentLayout.setVisibility(View.VISIBLE);
                            if (settings != null) {
                                settings.setMasterEnabled(true);
                                saveSettings();
                            }
                            preferenceRepository.pushMasterEnabled(true);
                        })
                        .setNegativeButton("Deny", (dialog, which) -> {
                            switchAllowNotifications.setChecked(false);
                            settingsContentLayout.setVisibility(View.GONE);
                            if (settings != null) {
                                settings.setMasterEnabled(false);
                                saveSettings();
                            }
                            preferenceRepository.pushMasterEnabled(false);
                        })
                        .setCancelable(false)
                        .show();
            } else {
                settingsContentLayout.setVisibility(View.GONE);
                if (settings != null) {
                    settings.setMasterEnabled(false);
                    saveSettings();
                }
                preferenceRepository.pushMasterEnabled(false);
            }
        });

        // Add click listeners to the rows to toggle switches
        rowAllowNotifications.setOnClickListener(v -> switchAllowNotifications.toggle());
        rowReportStatus.setOnClickListener(v -> switchReportStatus.toggle());
        rowReportCompleted.setOnClickListener(v -> switchReportCompleted.toggle());
        rowReportDenied.setOnClickListener(v -> switchReportDenied.toggle());
        rowAnnouncements.setOnClickListener(v -> switchAnnouncements.toggle());
        rowSound.setOnClickListener(v -> switchSound.toggle());
        rowVibration.setOnClickListener(v -> switchVibration.toggle());

        // Each toggle saves locally for instant feedback and pushes just that one
        // field to the server. Sending the whole set would let a stale local
        // value for another category overwrite a change made on another device.
        switchReportStatus.setOnClickListener(v -> {
            updateSettingsFromUI();
            saveSettings();
            preferenceRepository.pushReportStatusUpdates(switchReportStatus.isChecked());
        });
        switchReportCompleted.setOnClickListener(v -> {
            updateSettingsFromUI();
            saveSettings();
            preferenceRepository.pushReportCompleted(switchReportCompleted.isChecked());
        });
        switchReportDenied.setOnClickListener(v -> {
            updateSettingsFromUI();
            saveSettings();
            preferenceRepository.pushReportDenied(switchReportDenied.isChecked());
        });
        switchAnnouncements.setOnClickListener(v -> {
            updateSettingsFromUI();
            saveSettings();
            preferenceRepository.pushCenroAnnouncements(switchAnnouncements.isChecked());
        });
        switchSound.setOnClickListener(v -> {
            updateSettingsFromUI();
            saveSettings();
        });
        switchVibration.setOnClickListener(v -> {
            updateSettingsFromUI();
            saveSettings();
        });

        view.findViewById(R.id.btnChangeSound).setOnClickListener(v -> pickSound());
    }

    private void setupRowUI(View row, int iconRes, String title, String description) {
        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleTv = row.findViewById(R.id.rowTitle);
        TextView descTv = row.findViewById(R.id.rowDescription);

        icon.setImageResource(iconRes);
        titleTv.setText(title);
        if (description != null) {
            descTv.setText(description);
            descTv.setVisibility(View.VISIBLE);
        }
    }

    private void loadSettings() {
        String residentId = sessionManager.fetchResidentId();
        if (residentId == null) return;

        executor.execute(() -> {
            settings = dao.getSettingsByResidentId(residentId);
            if (settings == null) {
                settings = new NotificationSetting(residentId);
                dao.saveSettings(settings);
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(this::updateUI);
            }
        });

        // Pull the server's copy over the local one. The screen renders from the
        // local row immediately, then re-renders once the server answers, so a
        // change made elsewhere or before a reinstall wins without the screen
        // ever appearing to hang on the network.
        preferenceRepository.syncFromServer(() -> {
            if (getView() == null) return;
            executor.execute(() -> {
                NotificationSetting refreshed = dao.getSettingsByResidentId(residentId);
                if (refreshed == null || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (getView() == null) return;
                    settings = refreshed;
                    updateUI();
                });
            });
        });
    }

    private void updateUI() {
        if (settings == null) return;
        boolean allowed = settings.isMasterEnabled();
        switchAllowNotifications.setChecked(allowed);
        settingsContentLayout.setVisibility(allowed ? View.VISIBLE : View.GONE);

        switchReportStatus.setChecked(settings.isReportStatusUpdates());
        switchReportCompleted.setChecked(settings.isReportCompleted());
        switchReportDenied.setChecked(settings.isReportDenied());
        switchAnnouncements.setChecked(settings.isCenroAnnouncements());
        switchSound.setChecked(settings.isSoundEnabled());
        switchVibration.setChecked(settings.isVibrationEnabled());
    }

    private void updateSettingsFromUI() {
        if (settings == null) return;
        settings.setMasterEnabled(switchAllowNotifications.isChecked());
        settings.setReportStatusUpdates(switchReportStatus.isChecked());
        settings.setReportCompleted(switchReportCompleted.isChecked());
        settings.setReportDenied(switchReportDenied.isChecked());
        settings.setCenroAnnouncements(switchAnnouncements.isChecked());
        settings.setSoundEnabled(switchSound.isChecked());
        settings.setVibrationEnabled(switchVibration.isChecked());
    }

    private void saveSettings() {
        if (settings == null) return;
        executor.execute(() -> dao.saveSettings(settings));
    }

    private void pickSound() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, 
                (settings.getSoundUri().isEmpty() ? null : Uri.parse(settings.getSoundUri())));
        soundPickerLauncher.launch(intent);
    }
}

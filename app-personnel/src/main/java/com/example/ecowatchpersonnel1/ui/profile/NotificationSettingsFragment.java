package com.example.ecowatchpersonnel1.ui.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.repository.PersonnelNotificationRepository;
import com.example.ecowatchpersonnel1.utils.NotificationChannelTuning;
import com.example.ecowatchpersonnel1.utils.NotificationPreferences;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * What this cleaner wants to be alerted about.
 *
 * <p>The whole row is the touch target, not the switch. The switch is deliberately
 * non-clickable in {@code item_settings_switch}, so a tap on the label does the
 * thing a tap on the switch looks like it should do. Wiring only the switch's own
 * listener leaves a row that looks live and is not, which is what this screen used
 * to be.
 *
 * <p>Nothing here decides anything by itself: the choices are written to
 * {@link NotificationPreferences} and consulted by
 * {@link PersonnelNotificationRepository} at the moment an alert is raised. A
 * switch that only stored a boolean would be decoration.
 */
public class NotificationSettingsFragment extends Fragment {

    /**
     * Set while the stored values are being pushed into the switches.
     *
     * <p>A {@code setChecked} call fires the change listener, so without this the
     * screen would write every preference back the moment it opened. That is
     * invisible most of the time and destructive once a default changes.
     */
    private boolean applyingStoredValues;

    private MaterialSwitch switchAllowNotifications, switchTaskAssigned,
            switchTaskCompleted, switchTaskRevision, switchAnnouncements,
            switchSound, switchVibration;
    private View settingsContentLayout;

    private final ActivityResultLauncher<Intent> soundPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null) {
                            return;
                        }
                        Uri uri = result.getData().getParcelableExtra(
                                RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                        if (uri == null) return;

                        NotificationPreferences.setSoundUri(requireContext(), uri);
                        NotificationChannelTuning.apply(requireContext());
                        Toast.makeText(requireContext(),
                                R.string.notif_sound_updated, Toast.LENGTH_SHORT).show();
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupBack(view);
        bindRows(view);
        applyStoredSettings();
    }

    private void setupBack(View view) {
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
    }

    private void bindRows(View view) {
        settingsContentLayout = view.findViewById(R.id.settingsContentLayout);

        switchAllowNotifications = bindRow(view, R.id.itemAllowNotifications,
                R.drawable.ic_notification,
                getString(R.string.notif_allow_title),
                getString(R.string.notif_allow_summary));
        switchTaskAssigned = bindRow(view, R.id.itemTaskAssigned, R.drawable.ic_inbox,
                getString(R.string.notif_task_assigned_title),
                getString(R.string.notif_task_assigned_summary));
        switchTaskCompleted = bindRow(view, R.id.itemTaskCompleted,
                R.drawable.ic_mark_all_read,
                getString(R.string.notif_task_completed_title),
                getString(R.string.notif_task_completed_summary));
        switchTaskRevision = bindRow(view, R.id.itemTaskRevision, R.drawable.ic_back,
                getString(R.string.notif_task_revision_title),
                getString(R.string.notif_task_revision_summary));
        switchAnnouncements = bindRow(view, R.id.itemAnnouncements, R.drawable.ic_notification,
                getString(R.string.notif_announcements_title),
                getString(R.string.notif_announcements_summary));
        switchSound = bindRow(view, R.id.itemSound, android.R.drawable.ic_lock_silent_mode_off,
                getString(R.string.notif_sound_title),
                getString(R.string.notif_sound_summary));
        switchVibration = bindRow(view, R.id.itemVibration,
                android.R.drawable.ic_lock_idle_alarm,
                getString(R.string.notif_vibration_title),
                getString(R.string.notif_vibration_summary));

        bindMasterRow(view);
        bindCategoryRows(view);
        bindAlertStyleRows(view);

        View btnChangeSound = view.findViewById(R.id.btnChangeSound);
        if (btnChangeSound != null) {
            btnChangeSound.setOnClickListener(v -> openSoundPicker());
        }
    }

    /**
     * Lays out one row and makes the whole thing a touch target.
     *
     * @return the row's switch, so the caller can restore its stored value later
     */
    @Nullable
    private MaterialSwitch bindRow(View root, int rowId, int iconRes, String title,
                                   String summary) {
        View row = root.findViewById(rowId);
        if (row == null) return null;

        ImageView icon = row.findViewById(R.id.rowIcon);
        TextView titleView = row.findViewById(R.id.rowTitle);
        TextView summaryView = row.findViewById(R.id.rowDescription);
        MaterialSwitch toggle = row.findViewById(R.id.rowSwitch);

        if (icon != null) icon.setImageResource(iconRes);
        if (titleView != null) titleView.setText(title);
        if (summaryView != null) {
            // The summary is the point of these rows: without it the switches say
            // what they control but not what the cleaner is agreeing to.
            summaryView.setText(summary);
            summaryView.setVisibility(View.VISIBLE);
        }
        return toggle;
    }

    /**
     * Makes a row's tap toggle its switch.
     *
     * <p>The switch itself is not clickable, by design in the layout, so this is
     * the only way the setting can be changed. The saved value is written through
     * the switch's own change listener rather than here, so there is one place
     * that writes a preference.
     */
    private void makeRowToggleTheSwitch(View root, int rowId, MaterialSwitch toggle) {
        if (root == null || toggle == null) return;

        View row = root.findViewById(rowId);
        if (row == null) return;

        row.setOnClickListener(v -> {
            if (applyingStoredValues) return;
            toggle.setChecked(!toggle.isChecked());
        });
    }

    private void bindMasterRow(View view) {
        if (switchAllowNotifications == null) return;

        switchAllowNotifications.setOnCheckedChangeListener((button, isChecked) -> {
            if (applyingStoredValues) return;

            NotificationPreferences.setEnabled(requireContext(), isChecked);
            applyMasterState(isChecked);

            // The channel's sound and vibration are properties of the channel, not
            // of each notification, so they are re-applied here rather than checked
            // per alert.
            NotificationChannelTuning.apply(requireContext());
        });

        makeRowToggleTheSwitch(view, R.id.itemAllowNotifications, switchAllowNotifications);
    }

    private void bindCategoryRows(View view) {
        // The setter is passed per row rather than a key looked up by name: with
        // one shared listener a new row added carelessly would write to whichever
        // category happened to be named in it.
        bindCategory(view, R.id.itemTaskAssigned, switchTaskAssigned,
                NotificationPreferences::setTaskAssignedEnabled);
        bindCategory(view, R.id.itemTaskCompleted, switchTaskCompleted,
                NotificationPreferences::setTaskCompletedEnabled);
        bindCategory(view, R.id.itemTaskRevision, switchTaskRevision,
                NotificationPreferences::setTaskRevisionEnabled);
        bindCategory(view, R.id.itemAnnouncements, switchAnnouncements,
                NotificationPreferences::setAnnouncementsEnabled);
    }

    /** Writes one preference when its row is toggled. */
    private interface PreferenceSetter {
        void set(Context context, boolean value);
    }

    private void bindCategory(View view, int rowId, MaterialSwitch toggle, PreferenceSetter setter) {
        if (toggle == null) return;

        toggle.setOnCheckedChangeListener((button, isChecked) -> {
            if (applyingStoredValues) return;
            setter.set(requireContext(), isChecked);
        });

        makeRowToggleTheSwitch(view, rowId, toggle);
    }

    private void bindAlertStyleRows(View view) {
        if (switchSound != null) {
            switchSound.setOnCheckedChangeListener((button, isChecked) -> {
                if (applyingStoredValues) return;
                NotificationPreferences.setSoundEnabled(requireContext(), isChecked);
                NotificationChannelTuning.apply(requireContext());
            });
            makeRowToggleTheSwitch(view, R.id.itemSound, switchSound);
        }

        if (switchVibration != null) {
            switchVibration.setOnCheckedChangeListener((button, isChecked) -> {
                if (applyingStoredValues) return;
                NotificationPreferences.setVibrationEnabled(requireContext(), isChecked);
                NotificationChannelTuning.apply(requireContext());
            });
            makeRowToggleTheSwitch(view, R.id.itemVibration, switchVibration);
        }
    }

    /** Pushes every stored value into the switches without re-saving any of them. */
    private void applyStoredSettings() {
        applyingStoredValues = true;
        try {
            boolean master = NotificationPreferences.isEnabled(requireContext());

            if (switchAllowNotifications != null) {
                switchAllowNotifications.setChecked(master);
            }
            if (switchTaskAssigned != null) {
                switchTaskAssigned.setChecked(
                        NotificationPreferences.isTaskAssignedEnabled(requireContext()));
            }
            if (switchTaskCompleted != null) {
                switchTaskCompleted.setChecked(
                        NotificationPreferences.isTaskCompletedEnabled(requireContext()));
            }
            if (switchTaskRevision != null) {
                switchTaskRevision.setChecked(
                        NotificationPreferences.isTaskRevisionEnabled(requireContext()));
            }
            if (switchAnnouncements != null) {
                switchAnnouncements.setChecked(
                        NotificationPreferences.isAnnouncementsEnabled(requireContext()));
            }
            if (switchSound != null) {
                switchSound.setChecked(NotificationPreferences.isSoundEnabled(requireContext()));
            }
            if (switchVibration != null) {
                switchVibration.setChecked(
                        NotificationPreferences.isVibrationEnabled(requireContext()));
            }

            applyMasterState(master);
        } finally {
            applyingStoredValues = false;
        }
    }

    /**
     * Greys the category rows while the master switch is off.
     *
     * <p>They keep their stored values: turning everything off and back on again
     * should not silently re-enable the categories a cleaner had chosen.
     */
    private void applyMasterState(boolean master) {
        if (settingsContentLayout != null) {
            settingsContentLayout.setVisibility(master ? View.VISIBLE : View.GONE);
        }

        float alpha = master ? 1f : 0.4f;
        for (MaterialSwitch toggle : new MaterialSwitch[]{switchTaskAssigned,
                switchTaskCompleted, switchTaskRevision, switchAnnouncements,
                switchSound, switchVibration}) {
            if (toggle == null) continue;

            toggle.setEnabled(master);
            toggle.setAlpha(alpha);

            // The row, not just the switch, is the touch target, so it has to be
            // disabled too or a greyed-out switch still changes its preference.
            View row = (View) toggle.getParent();
            if (row != null) {
                row.setEnabled(master);
                row.setAlpha(alpha);
            }
        }
    }

    private void openSoundPicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                getString(R.string.notif_sound_picker_title));
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        Uri current = NotificationPreferences.soundUri(requireContext());
        if (current != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, current);
        }
        soundPickerLauncher.launch(intent);
    }
}
package com.example.ecowatchpersonnel1.ui.profile;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.Personnel;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.remote.ServerConfig;
import com.example.ecowatchpersonnel1.data.remote.TaskCountsResponse;
import com.example.ecowatchpersonnel1.data.repository.PersonnelAuthRepository;
import com.example.ecowatchpersonnel1.data.repository.TaskRepository;
import com.example.ecowatchpersonnel1.ui.home.DashboardActivity;
import com.example.ecowatchpersonnel1.utils.SessionManager;
import com.example.ecowatchpersonnel1.utils.TaskStatsStore;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * The cleaner's own account.
 *
 * <p>Read-only by design: personnel accounts are created and edited by an
 * administrator, so this screen shows what the server reports and offers exactly
 * one action, signing out. The row of task counts is also the server's, read
 * from {@code /personnel/task-counts} and counted over every assignment rather
 * than over one page of the task list.
 */
public class ProfileFragment extends Fragment {

    private PersonnelAuthRepository repository;
    private TaskRepository taskRepository;

    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progress;
    private ShapeableImageView profileImage;
    private TextView nameText;
    private TextView personnelIdText;
    private TextView statusText;
    private TextView usernameText;
    private TextView emailText;
    private TextView phoneText;
    private TextView occupationText;
    private View vehicleCard;
    private TextView vehiclePlateText;
    private TextView vehicleIdText;
    private TextView totalAssignedCount;
    private TextView onHandCount;
    private TextView inProgressCount;
    private TextView completedCount;

    /**
     * Who the saved counts belong to. Read up front from the session because the
     * counts arrive asynchronously: without it a second account signing in on
     * this device would paint the previous cleaner's totals first.
     */
    private String personnelId;

    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new PersonnelAuthRepository(requireContext());
        taskRepository = new TaskRepository(requireContext());

        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        progress = view.findViewById(R.id.profileProgress);
        profileImage = view.findViewById(R.id.profileImage);
        nameText = view.findViewById(R.id.profileUserName);
        personnelIdText = view.findViewById(R.id.profilePersonnelId);
        statusText = view.findViewById(R.id.profileStatus);
        usernameText = view.findViewById(R.id.profileUsername);
        emailText = view.findViewById(R.id.profileEmail);
        phoneText = view.findViewById(R.id.profilePhone);
        occupationText = view.findViewById(R.id.personnelOccupation);
        vehicleCard = view.findViewById(R.id.profileVehicleCard);
        vehiclePlateText = view.findViewById(R.id.profileVehiclePlate);
        vehicleIdText = view.findViewById(R.id.profileVehicleId);
        totalAssignedCount = view.findViewById(R.id.totalAssignedReport);
        onHandCount = view.findViewById(R.id.reporterStatOnhand);
        inProgressCount = view.findViewById(R.id.reporterStatInProgress);
        completedCount = view.findViewById(R.id.reporterStatCompleted);
        personnelId = new SessionManager(requireContext()).fetchPersonnelId();

        // Paint the last recorded counts straight away; the fresh ones replace
        // them as soon as the recount below lands.
        renderStats(TaskStatsStore.load(requireContext(), personnelId));

        swipeRefresh.setOnRefreshListener(() -> {
            loadProfile();
            loadStats();
        });
        view.findViewById(R.id.profileSignOutBtn).setOnClickListener(v -> confirmSignOut());

        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else if (requireActivity() instanceof DashboardActivity) {
                    ((DashboardActivity) requireActivity()).selectTab(DashboardActivity.Tab.MENU);
                }
            });
        }

        loadProfile();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository != null) {
            loadProfile();
            loadStats();
        }
    }

    private void loadProfile() {
        progress.setVisibility(loaded ? View.GONE : View.VISIBLE);

        repository.fetchMe(new ApiCallback<Personnel>() {
            @Override
            public void onSuccess(Personnel personnel) {
                if (!isAdded()) return;
                loaded = true;
                progress.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                render(personnel);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                loaded = true;
                progress.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Repaints the stat cards from the server's own counts and records them.
     *
     * <p>The totals come from {@code /personnel/task-counts} rather than being
     * recounted from the task list: the list is one page, so a cleaner with more
     * tasks than a page holds would see a completed count that silently omitted
     * the older ones. The same numbers are counted again by the admin app for
     * this cleaner, so both screens always agree: the first card is every task
     * ever assigned, the second what is still on hand, then in progress and
     * completed.
     *
     * <p>On failure the cards keep the last recorded numbers. A stale count is
     * more useful on a read-only screen than an error toast, and the profile
     * fetch above already reports its own failures.
     */
    private void loadStats() {
        taskRepository.fetchTaskCounts(new ApiCallback<TaskCountsResponse.Counts>() {
            @Override
            public void onSuccess(TaskCountsResponse.Counts counts) {
                if (!isAdded() || counts == null) return;

                TaskStatsStore.Stats stats = new TaskStatsStore.Stats(
                        counts.totalAssignedCount(), counts.onHand(),
                        counts.inProgressCount(), counts.completedCount());
                renderStats(stats);
                TaskStatsStore.save(requireContext(), personnelId, stats);
            }

            @Override
            public void onError(String message) {
                // Nothing to report: the previous counts are still on screen.
            }
        });
    }

    /** Writes the four totals into their cards, without touching the labels. */
    private void renderStats(TaskStatsStore.Stats stats) {
        if (stats == null) stats = TaskStatsStore.Stats.empty();
        if (totalAssignedCount == null || onHandCount == null
                || inProgressCount == null || completedCount == null) return;
        totalAssignedCount.setText(String.valueOf(stats.totalAssigned));
        onHandCount.setText(String.valueOf(stats.onHand));
        inProgressCount.setText(String.valueOf(stats.inProgress));
        completedCount.setText(String.valueOf(stats.completed));
    }

    private void render(Personnel personnel) {
        if (personnel == null) return;

        nameText.setText(personnel.displayName());
        personnelIdText.setText(personnel.personnelId == null
                ? "PERSONNEL ID: —" : "PERSONNEL ID: " + personnel.personnelId);

        statusText.setText(describeStatus(personnel));

        usernameText.setText(orDash(personnel.username));
        emailText.setText(orDash(firstNonBlank(personnel.email, personnel.gmail)));
        phoneText.setText(orDash(personnel.phoneNumber));
        occupationText.setText(personnel.isDriver()
                ? R.string.profile_occupation_driver
                : R.string.profile_occupation_regular);

        bindAssignedVehicle(personnel);

        String url = firstNonBlank(personnel.profileImageUrl, personnel.profileImage,
                personnel.avatarUrl);
        if (url == null) {
            profileImage.setImageResource(R.drawable.pfp);
        } else {
            Glide.with(this)
                    .load(ServerConfig.absoluteUrl(requireContext(), url))
                    .placeholder(R.drawable.pfp)
                    .error(R.drawable.pfp)
                    .into(profileImage);
        }
    }

    /**
     * Shows the truck this cleaner is assigned to.
     *
     * <p>Driver-only, and an account with no truck is left out rather than shown
     * with dashes: a "Vehicle Assigned" panel reading "—" tells the cleaner
     * nothing, where its absence tells the administrator to go and assign one.
     */
    private void bindAssignedVehicle(Personnel personnel) {
        boolean hasVehicle = personnel.isDriver() && personnel.hasAssignedVehicle();
        vehicleCard.setVisibility(hasVehicle ? View.VISIBLE : View.GONE);
        if (!hasVehicle) return;

        String plate = personnel.assignedVehiclePlate();
        vehiclePlateText.setText(plate != null
                ? plate : getString(R.string.vehicle_plate_unknown));
        vehicleIdText.setText(getString(R.string.vehicle_id_format,
                personnel.assignedVehicleId()));
    }

    /** A short, human phrase for the account's availability. */
    private String describeStatus(Personnel personnel) {
        if (personnel.isDeleted) return "Account removed";
        if (personnel.isDisabled || !personnel.isActive) return "Inactive";
        if (personnel.cleaning) return "Cleaning";
        if (personnel.online) return "Available · Online";
        return "Available";
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_sign_out)
                .setMessage(R.string.profile_sign_out_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.profile_sign_out, (dialog, which) -> signOut())
                .show();
    }

    private void signOut() {
        if (requireActivity() instanceof DashboardActivity) {
            ((DashboardActivity) requireActivity()).signOutAndReturnToLogin(null);
        }
    }

    private static String orDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}

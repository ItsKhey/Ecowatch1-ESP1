package com.example.ecowatchpersonnel1.ui.profile;

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

import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.Personnel;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.repository.PersonnelAuthRepository;
import com.example.ecowatchpersonnel1.ui.home.DashboardActivity;

/**
 * The truck assigned to the signed-in cleaner.
 *
 * <p>Read-only, like the account screen: the assignment is an administrator's
 * decision, so this screen reports one truck or explains why there isn't one. It
 * never issues a request of its own beyond {@code /personnel/me}, which is the
 * same fetch the profile screen makes — the cleaner is already signed in, so the
 * server knows who they are and there is no id to pass.
 *
 * <p>Two different empty states, because they need different answers from the
 * administrator: a Regular cleaner is never going to be given a truck by this
 * screen, whereas a Driver without one is a gap somebody has to close.
 */
public class VehicleFragment extends Fragment {

    private PersonnelAuthRepository repository;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progress;
    private TextView subtitle;
    private View vehicleCard;
    private TextView plateText;
    private TextView vehicleIdText;
    private TextView emptyText;

    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_vehicle, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new PersonnelAuthRepository(requireContext());

        swipeRefresh = view.findViewById(R.id.vehicleSwipeRefresh);
        progress = view.findViewById(R.id.vehicleProgress);
        subtitle = view.findViewById(R.id.vehicleSubtitle);
        emptyText = view.findViewById(R.id.vehicleEmpty);

        // The id on the <include> tag IS the card, and it replaces the included root's
        // own id — so the card is addressed through the include's id and its two
        // value rows are looked up inside it.
        vehicleCard = view.findViewById(R.id.vehicleCard);
        plateText = vehicleCard.findViewById(R.id.vehicleCardPlate);
        vehicleIdText = vehicleCard.findViewById(R.id.vehicleCardVehicleId);

        view.findViewById(R.id.vehicleBackBtn).setOnClickListener(v -> goBack());
        swipeRefresh.setOnRefreshListener(this::load);

        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The admin may have reassigned this cleaner while they were elsewhere in
        // the app, so returning here re-reads rather than showing a stale truck.
        if (repository != null) load();
    }

    private void goBack() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else if (requireActivity() instanceof DashboardActivity) {
            ((DashboardActivity) requireActivity()).selectTab(DashboardActivity.Tab.MENU);
        }
    }

    private void load() {
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

    private void render(Personnel personnel) {
        if (personnel == null) return;

        boolean isDriver = personnel.isDriver();
        boolean hasVehicle = isDriver && personnel.hasAssignedVehicle();

        subtitle.setText(isDriver
                ? R.string.vehicle_subtitle_driver
                : R.string.vehicle_subtitle_regular);

        vehicleCard.setVisibility(hasVehicle ? View.VISIBLE : View.GONE);
        if (hasVehicle) {
            String plate = personnel.assignedVehiclePlate();
            plateText.setText(plate != null
                    ? plate : getString(R.string.vehicle_plate_unknown));
            vehicleIdText.setText(getString(R.string.vehicle_id_format,
                    personnel.assignedVehicleId()));
        }

        // A cleaner is told why there is nothing here; a driver is told to go ask
        // for a truck. Telling a cleaner to chase an assignment would be wrong.
        if (hasVehicle) {
            emptyText.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(isDriver
                    ? R.string.vehicle_empty_driver
                    : R.string.vehicle_no_occupation);
        }
    }
}
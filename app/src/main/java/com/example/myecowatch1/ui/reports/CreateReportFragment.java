package com.example.myecowatch1.ui.reports;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.myecowatch1.R;
import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.remote.ServerConfig;
import com.example.myecowatch1.ui.home.DashboardActivity;
import com.example.myecowatch1.utils.NearbyReportPayload;
import com.example.myecowatch1.utils.NotificationDateFormatter;
import com.example.myecowatch1.utils.PhotoCompressor;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateReportFragment extends Fragment {

    private static final int MAX_PHOTOS = 10;

    private EditText titleInput, descriptionInput;
    private Spinner wasteTypeSpinner;
    private Button submitBtn, takePhotoBtn, selectPhotoBtn, getLocationBtn, cancelEditBtn;
    private TextView locationTextView;
    private TextView photoCounterText;
    private RecyclerView photoRecyclerView;
    private ReportsViewModel viewModel;
    private File photoFile;
    private double latitude = 0, longitude = 0;
    private FusedLocationProviderClient fusedLocationClient;
    private Report editingReport;

    // Multi-photo support
    private List<File> selectedPhotos = new ArrayList<>();
    private PhotoThumbnailAdapter photoAdapter;

    // Shown when a report already exists at the submitted location.
    private BottomSheetDialog nearbyBottomSheet;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    if (isUsableImage(photoFile)) {
                        addPhotoToList(photoFile);
                        photoFile = null;
                    } else {
                        photoFile = null;
                        Toast.makeText(getContext(), "No photo was captured. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                } else if (photoFile != null && photoFile.length() == 0L) {
                    photoFile = null;
                }
            }
    );

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    // Handle multi-selection
                    if (data.getClipData() != null) {
                        ClipData clipData = data.getClipData();
                        int count = Math.min(clipData.getItemCount(), MAX_PHOTOS - selectedPhotos.size());
                        for (int i = 0; i < count; i++) {
                            Uri uri = clipData.getItemAt(i).getUri();
                            handleGalleryImage(uri);
                        }
                    } else if (data.getData() != null) {
                        handleGalleryImage(data.getData());
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    getCurrentLocation();
                } else {
                    Toast.makeText(getContext(), "Permission denied for location", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_report, container, false);

        viewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        titleInput = view.findViewById(R.id.reportTitleInput);
        descriptionInput = view.findViewById(R.id.reportDescriptionInput);
        wasteTypeSpinner = view.findViewById(R.id.wasteTypeSpinner);
        submitBtn = view.findViewById(R.id.submitReportBtn);
        takePhotoBtn = view.findViewById(R.id.takePhotoBtn);
        selectPhotoBtn = view.findViewById(R.id.selectPhotoBtn);
        getLocationBtn = view.findViewById(R.id.getLocationBtn);
        cancelEditBtn = view.findViewById(R.id.cancelEditBtn);
        locationTextView = view.findViewById(R.id.locationTextView);
        photoCounterText = view.findViewById(R.id.photoCounterText);
        photoRecyclerView = view.findViewById(R.id.photoRecyclerView);

        setupSpinner();
        setupPhotoGrid();

        if (getArguments() != null && getArguments().containsKey("report")) {
            editingReport = (Report) getArguments().getSerializable("report");
            setupEditMode();
        } else {
            // New report mode — photo grid handles display
        }

        takePhotoBtn.setOnClickListener(v -> dispatchTakePictureIntent());
        selectPhotoBtn.setOnClickListener(v -> dispatchGalleryIntent());
        getLocationBtn.setOnClickListener(v -> checkLocationPermission());

        cancelEditBtn.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        submitBtn.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.getSubmitting().getValue())) {
                return;
            }

            String title = titleInput.getText().toString().trim();
            String description = descriptionInput.getText().toString().trim();
            String wasteType = wasteTypeSpinner.getSelectedItem().toString();

            if (title.isEmpty()) {
                Toast.makeText(getContext(), "Title is required.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (wasteType.equals("Select Waste Category")) {
                Toast.makeText(getContext(), "Please select a waste category", Toast.LENGTH_SHORT).show();
                return;
            }

            if (editingReport == null && selectedPhotos.isEmpty()) {
                Toast.makeText(getContext(), "Please select at least one photo before submitting.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (editingReport != null) {
                viewModel.updateReport(buildEditedReport(title, description, wasteType),
                        selectedPhotos.isEmpty() ? null : selectedPhotos.get(0));
            } else {
                viewModel.submitReportMultiple(title, description, wasteType,
                        new ArrayList<>(selectedPhotos), latitude, longitude);
            }
        });

        viewModel.getSubmitting().observe(getViewLifecycleOwner(), submitting -> {
            boolean inFlight = Boolean.TRUE.equals(submitting);
            submitBtn.setEnabled(!inFlight);
            takePhotoBtn.setEnabled(!inFlight);
            selectPhotoBtn.setEnabled(!inFlight);
            submitBtn.setText(inFlight
                    ? "Submitting..."
                    : editingReport == null ? "Submit Report" : "Update Report");
        });

        viewModel.getSubmissionSuccess().observe(getViewLifecycleOwner(), event -> {
            Boolean success = event == null ? null : event.getContentIfNotHandled();
            if (Boolean.TRUE.equals(success)) {
                String message = editingReport != null ? "Report updated successfully!" : "Report submitted successfully.";
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                if (isAdded() && getActivity() != null) {
                    if (editingReport != null) {
                        getActivity().getSupportFragmentManager().popBackStack();
                    } else {
                        if (getActivity() instanceof DashboardActivity) {
                            ((DashboardActivity) getActivity()).navigateToReports();
                        } else {
                            getActivity().getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.fragmentContainer, new ReportsFragment())
                                    .commitAllowingStateLoss();
                        }
                    }
                }
            }
        });

        viewModel.getNearbyReportDetection().observe(getViewLifecycleOwner(), event -> {
            Map<String, Object> nearby = event == null ? null : event.getContentIfNotHandled();
            if (nearby != null) {
                if (hasOutstandingNearbyReport(nearby)) {
                    showNearbyReportBottomSheet(nearby);
                } else {
                    // Nothing there is still waiting for a cleaner, so there is
                    // no duplicate to decide on. See hasOutstandingNearbyReport.
                    submitWithoutNearbyWarning();
                }
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), event -> {
            String error = event == null ? null : event.getContentIfNotHandled();
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    private void setupPhotoGrid() {
        photoAdapter = new PhotoThumbnailAdapter();
        photoAdapter.setOnPhotoActionListener(new PhotoThumbnailAdapter.OnPhotoActionListener() {
            @Override
            public void onRemovePhoto(int position) {
                if (position >= 0 && position < selectedPhotos.size()) {
                    selectedPhotos.remove(position);
                    photoAdapter.removePhoto(position);
                    updatePhotoCounter();
                }
            }

            @Override
            public void onAddPhoto() {
                dispatchGalleryIntent();
            }
        });

        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        photoRecyclerView.setLayoutManager(layoutManager);
        photoRecyclerView.setAdapter(photoAdapter);
        updatePhotoCounter();
    }

    private void addPhotoToList(File photo) {
        if (photo != null && selectedPhotos.size() < MAX_PHOTOS) {
            selectedPhotos.add(photo);
            photoAdapter.addPhoto(photo);
            updatePhotoCounter();
        }
    }

    private void updatePhotoCounter() {
        if (photoCounterText != null) {
            photoCounterText.setText(selectedPhotos.size() + "/" + MAX_PHOTOS + " Photos Selected/Taken");
        }
    }

    /**
     * Whether the nearby hand-off is worth putting in front of the resident.
     *
     * <p>See {@link NearbyReportPayload#hasOutstandingReports(Map)}: the server
     * counts only the reports at that spot which are still outstanding, so a
     * count of zero means there is no duplicate left to decide on.
     */
    private boolean hasOutstandingNearbyReport(Map<String, Object> nearbyData) {
        return NearbyReportPayload.hasOutstandingReports(nearbyData);
    }

    /**
     * Files the held submission straight away, because the nearby hand-off had
     * nothing outstanding behind it.
     *
     * <p>The ViewModel is still holding the report and its photos, so the
     * submission is completed rather than dropped, and the resident is not made
     * to tap through a prompt about nothing.
     *
     * <p>The cluster id in the hand-off is deliberately not sent back. The
     * server picks the cluster from the coordinates on every submission and
     * ignores the id on the wire, so replaying a finished one would claim
     * nothing. If a report does land at the spot in between, the server answers
     * with a fresh hand-off and the sheet comes back with the resident.
     */
    private void submitWithoutNearbyWarning() {
        Log.i("CreateReportFragment",
                "Skipping the duplicate sheet: nothing outstanding at this spot");
        viewModel.submitPendingCreateReport();
    }

    /**
     * Tells the resident a report already exists at this spot.
     *
     * <p>The server answers a submission that lands near an existing report with
     * the nearby details, so the resident can decide whether their waste is a
     * separate problem. Only the distance, the cleanup status, how many reports
     * are there and when it was last active are shown; none of the backend's
     * grouping data reaches the resident.
     */
    private void showNearbyReportBottomSheet(Map<String, Object> nearbyData) {
        if (nearbyBottomSheet != null && nearbyBottomSheet.isShowing()) {
            return;
        }

        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_nearby_report, null);
        nearbyBottomSheet = new BottomSheetDialog(requireContext());
        nearbyBottomSheet.setContentView(sheetView);
        // The layout supplies the theme-aware surface, so the dialog's own
        // default background is dropped to avoid a second layer peeking out
        // around the rounded corners.
        nearbyBottomSheet.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView distanceView = sheetView.findViewById(R.id.nearbyReportDistance);
        TextView countView = sheetView.findViewById(R.id.nearbyReportCount);
        TextView statusView = sheetView.findViewById(R.id.nearbyReportStatus);
        TextView lastActivityView = sheetView.findViewById(R.id.nearbyReportLastActivity);
        ImageView previewImage = sheetView.findViewById(R.id.nearbyReportImage);

        Object distance = nearbyData.get("distance");
        if (distance instanceof Number) {
            distanceView.setText(String.format(Locale.getDefault(), "%.1f meters",
                    ((Number) distance).doubleValue()));
        } else {
            distanceView.setText("Very close");
        }

        // reportCount and spotCount are the same figure on the server; the
        // resident sees one number, not two internal counters.
        Object reportCount = nearbyData.get("reportCount");
        if (reportCount == null) reportCount = nearbyData.get("spotCount");
        countView.setText(formatCount(reportCount));

        // Server's status field for the nearby reports.
        Object status = nearbyData.get("clusterStatus");
        statusView.setText("Cleanup status: "
                + (status != null ? status.toString() : "Pending"));

        lastActivityView.setText("Last activity: " + formatLastActivity(nearbyData.get("lastActivityAt")));

        Object previewPhoto = nearbyData.get("previewPhoto");
        if (previewPhoto != null && !previewPhoto.toString().isEmpty()) {
            Glide.with(this)
                    .load(ServerConfig.absoluteUrl(requireContext(), previewPhoto.toString()))
                    .centerCrop()
                    .into(previewImage);
        } else {
            previewImage.setVisibility(View.GONE);
        }

        sheetView.findViewById(R.id.submitAnywayBtn).setOnClickListener(v -> {
            nearbyBottomSheet.dismiss();
            // Server-side grouping key, passed straight through and never displayed.
            Object groupId = nearbyData.get("clusterId");
            if (groupId != null && !groupId.toString().trim().isEmpty()) {
                // The pending submission is still held by the ViewModel, so this
                // completes it against the nearby reports.
                viewModel.submitDespiteNearbyReport(groupId.toString());
            }
        });

        sheetView.findViewById(R.id.cancelNearbyReportBtn).setOnClickListener(v -> {
            nearbyBottomSheet.dismiss();
            // Photos are preserved — the resident can retry or cancel manually.
        });

        nearbyBottomSheet.show();
    }

    /**
     * Renders a count as a whole number.
     *
     * <p>The payload is decoded generically, so a JSON number such as {@code 2}
     * arrives as a {@code Double} and would otherwise display as "2.0".
     */
    private String formatCount(Object value) {
        if (value == null) return "0";
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isNaN(number) && !Double.isInfinite(number)
                    && number == Math.rint(number)) {
                return String.valueOf((long) number);
            }
            return String.valueOf(number);
        }
        return value.toString();
    }

    /**
     * Renders the nearby reports' last activity as a readable time instead of
     * the raw ISO timestamp the API returns.
     */
    private String formatLastActivity(Object value) {
        if (value == null) return "Unknown";
        String raw = value.toString().trim();
        if (raw.isEmpty()) return "Unknown";
        return NotificationDateFormatter.formatRelative(raw);
    }

    private void showSuccessDialog(String title, String message) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_submission_success, null);
        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();

        TextView titleView = dialogView.findViewById(R.id.successTitle);
        TextView messageView = dialogView.findViewById(R.id.successMessage);
        titleView.setText(title);
        messageView.setText(message);

        View okBtn = dialogView.findViewById(R.id.successOkBtn);
        okBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (isAdded() && getActivity() != null) {
                if (getActivity() instanceof DashboardActivity) {
                    ((DashboardActivity) getActivity()).navigateToReports();
                } else {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new ReportsFragment())
                            .commitAllowingStateLoss();
                }
            }
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    /**
     * Copies the report being edited and applies the submitted values.
     */
    private Report buildEditedReport(String title, String description, String wasteType) {
        Report edited = new Report(title, description, wasteType, latitude, longitude);

        edited.setId(editingReport.getId());
        edited.setMongoId(editingReport.getStoredMongoId());
        edited.setReportId(editingReport.getReportId());
        edited.setResidentId(editingReport.getResidentId());
        edited.setCreatedAt(editingReport.getCreatedAt());
        edited.setUpdatedAt(editingReport.getUpdatedAt());
        edited.setStatus(editingReport.getStatus());
        edited.setAdminResponse(editingReport.getAdminResponse());
        edited.setPhotoUrl(editingReport.getPhotoUrl());
        edited.setLocalPhotoPath(editingReport.getLocalPhotoPath());
        edited.setSynced(editingReport.isSynced());
        edited.setDeleted(editingReport.isDeleted());
        edited.setDeletedAt(editingReport.getDeletedAt());
        edited.setArchived(editingReport.isArchived());
        edited.setRestored(editingReport.isRestored());
        // Keeps the server-side nearby group so an edit still targets it. Never shown.
        edited.setClusterId(editingReport.getClusterId());
        return edited;
    }

    private void setupEditMode() {
        if (editingReport == null) return;

        titleInput.setText(editingReport.getTitle());
        descriptionInput.setText(editingReport.getDescription());
        latitude = editingReport.getLatitude();
        longitude = editingReport.getLongitude();
        renderLocation();

        locationTextView.setClickable(true);
        locationTextView.setFocusable(true);
        locationTextView.setOnClickListener(v -> showEditLocationDialog());

        ArrayAdapter<String> adapter = (ArrayAdapter<String>) wasteTypeSpinner.getAdapter();
        if (adapter != null) {
            int pos = adapter.getPosition(editingReport.getWasteType());
            if (pos >= 0) wasteTypeSpinner.setSelection(pos);
        }

        String photoUrl = editingReport.getPhotoUrl();
        Object imageSource = null;
        if (editingReport.getLocalPhotoPath() != null) {
            File localFile = new File(editingReport.getLocalPhotoPath());
            if (localFile.exists()) imageSource = localFile;
        }

        if (imageSource == null && photoUrl != null && !photoUrl.isEmpty()) {
            // Relative path, expanded against whichever server this build targets.
            imageSource = ServerConfig.absoluteUrl(requireContext(), photoUrl);
        }

        // In edit mode, the existing photo is shown in the photo grid
        if (imageSource != null && selectedPhotos.isEmpty()) {
            try {
                File localFile = null;
                if (imageSource instanceof File) {
                    localFile = (File) imageSource;
                } else if (imageSource instanceof String && ((String) imageSource).startsWith("/")) {
                    localFile = new File((String) imageSource);
                }
                if (localFile != null && localFile.exists()) {
                    selectedPhotos.add(localFile);
                    photoAdapter.addPhoto(localFile);
                    updatePhotoCounter();
                }
            } catch (Exception ignored) {
                // If we can't load the existing photo, just skip it
            }
        }

        submitBtn.setText("Update Report");
        cancelEditBtn.setVisibility(View.VISIBLE);

        if (getView() != null) {
            TextView header = getView().findViewById(R.id.createReportHeader);
            if (header != null) header.setText("Edit Environmental Report");
        }
    }

    private void renderLocation() {
        String coordinates = hasCoordinates(latitude, longitude)
                ? String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", latitude, longitude)
                : "Not selected";
        String hint = editingReport != null ? " (tap to edit)" : "";
        locationTextView.setText(coordinates + hint);
    }

    private static boolean hasCoordinates(double lat, double lon) {
        return lat != 0d || lon != 0d;
    }

    private void showEditLocationDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_location, null);
        EditText latitudeInput = dialogView.findViewById(R.id.latitudeInput);
        EditText longitudeInput = dialogView.findViewById(R.id.longitudeInput);
        View cancelBtn = dialogView.findViewById(R.id.btnCancelLocation);
        View saveBtn = dialogView.findViewById(R.id.btnSaveLocation);

        if (hasCoordinates(latitude, longitude)) {
            latitudeInput.setText(String.format(Locale.US, "%.6f", latitude));
            longitudeInput.setText(String.format(Locale.US, "%.6f", longitude));
        }

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            String latText = latitudeInput.getText().toString().trim();
            String lonText = longitudeInput.getText().toString().trim();

            if (latText.isEmpty() && lonText.isEmpty()) {
                latitude = 0d;
                longitude = 0d;
                renderLocation();
                dialog.dismiss();
                return;
            }

            if (latText.isEmpty() || lonText.isEmpty()) {
                Toast.makeText(getContext(), "Enter both latitude and longitude.", Toast.LENGTH_SHORT).show();
                return;
            }

            double parsedLat, parsedLon;
            try {
                parsedLat = Double.parseDouble(latText);
                parsedLon = Double.parseDouble(lonText);
            } catch (NumberFormatException error) {
                Toast.makeText(getContext(), "Latitude and longitude must be numbers.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (parsedLat < -90 || parsedLat > 90) {
                Toast.makeText(getContext(), "Latitude must be between -90 and 90.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (parsedLon < -180 || parsedLon > 180) {
                Toast.makeText(getContext(), "Longitude must be between -180 and 180.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (parsedLat == 0d && parsedLon == 0d) {
                Toast.makeText(getContext(), "That is not a valid location.", Toast.LENGTH_SHORT).show();
                return;
            }

            latitude = parsedLat;
            longitude = parsedLon;
            renderLocation();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateCameraIcon() {
        // No longer needed — photo grid handles display
    }

    private void setupSpinner() {
        String[] types = {
            "Select Waste Category",
            "Biodegradable",
            "Recyclable",
            "Residual",
            "Hazardous",
            "Electronic Waste (E-Waste)",
            "General Waste"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, types) {
            @Override
            public boolean isEnabled(int position) {
                return position != 0;
            }

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(getResources().getColor(R.color.green, null));
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                if (position == 0) {
                    view.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                } else {
                    view.setTextColor(getResources().getColor(R.color.green, null));
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wasteTypeSpinner.setAdapter(adapter);
    }

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    dispatchTakePictureIntentInternal();
                } else {
                    Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private void dispatchTakePictureIntent() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntentInternal();
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void dispatchTakePictureIntentInternal() {
        if (selectedPhotos.size() >= MAX_PHOTOS) {
            Toast.makeText(getContext(), "Maximum " + MAX_PHOTOS + " photos allowed.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            photoFile = createImageFile();
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                cameraLauncher.launch(takePictureIntent);
            }
        } catch (IOException ex) {
            Toast.makeText(getContext(), "Error creating file", Toast.LENGTH_SHORT).show();
        }
    }

    private void dispatchGalleryIntent() {
        if (selectedPhotos.size() >= MAX_PHOTOS) {
            Toast.makeText(getContext(), "Maximum " + MAX_PHOTOS + " photos allowed.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void handleGalleryImage(Uri uri) {
        if (uri == null) {
            Toast.makeText(getContext(), "No image was selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPhotos.size() >= MAX_PHOTOS) {
            Toast.makeText(getContext(), "Maximum " + MAX_PHOTOS + " photos allowed.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Scaled to a bounded longest edge before it is ever written. The full
        // camera file is around 3MB, which made every later list that showed this
        // report wait on an upload of bytes that a thumbnail never displays.
        File selectedFile = null;
        try {
            selectedFile = createImageFile();
            PhotoCompressor.writeScaledJpeg(requireContext(), uri, selectedFile);
        } catch (IOException | RuntimeException error) {
            if (selectedFile != null) {
                // Never leave a half-written file behind for the uploader to find.
                selectedFile.delete();
            }
            Toast.makeText(getContext(), "Unable to load the selected image.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isUsableImage(selectedFile)) {
            selectedFile.delete();
            Toast.makeText(getContext(), "Selected image is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        addPhotoToList(selectedFile);
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                renderLocation();
            } else {
                Toast.makeText(getContext(), "Could not get location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isUsableImage(File file) {
        return file != null
                && file.isFile()
                && file.canRead()
                && file.length() > 0L;
    }
}

package com.example.ecowatchpersonnel1.ui.tasks;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.ecowatchpersonnel1.R;
import com.example.ecowatchpersonnel1.data.models.PersonnelEvidence;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.models.TaskPermissions;
import com.example.ecowatchpersonnel1.data.models.TaskReport;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.remote.ServerConfig;
import com.example.ecowatchpersonnel1.data.repository.TaskHistoryRepository;
import com.example.ecowatchpersonnel1.data.repository.TaskRepository;
import com.example.ecowatchpersonnel1.utils.ImageCompressor;
import com.example.ecowatchpersonnel1.utils.NotificationDateFormatter;
import com.example.ecowatchpersonnel1.utils.WasteScaleDisplay;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * One assignment and its evidence workflow.
 *
 * <p>The screen is a straight mirror of the server's state machine: the accept,
 * start and complete buttons appear only when the matching {@code permissions}
 * flag is set, and evidence phases are uploaded with the phase the server
 * expects. The app never decides on its own that a transition is legal.
 */
public class TaskDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";

    private static final String PHASE_BEFORE = "BEFORE";
    private static final String PHASE_AFTER = "AFTER";

    public static Intent intentFor(Context context, String assignmentId) {
        Intent intent = new Intent(context, TaskDetailActivity.class);
        intent.putExtra(EXTRA_ASSIGNMENT_ID, assignmentId);
        return intent;
    }

    private TaskRepository repository;
    /**
     * The on-device task history, written to on every render so it never lags the
     * server. Not read from here: this screen always shows live server state, and
     * a stale cache must never stand in for a transition the backend has not
     * confirmed.
     */
    private TaskHistoryRepository historyRepository;
    private String assignmentId;
    private PersonnelTask task;

    private ShapeableImageView reportImage;
    private TextView detailTitle;
    private TextView detailStatus;
    private TextView detailMeta;
    private TextView reportTitle;
    private TextView reportCategory;
    private TextView reportDescription;
    private TextView reportLocation;
    private TextView reportWasteScale;
    private View reportDispatchDetails;
    private View reportAssignedTeamRow;
    private TextView reportAssignedTeam;
    private View reportAssignedVehicleRow;
    private TextView reportAssignedVehicle;
    private TextView timelineAccepted;
    private TextView timelineStarted;
    private TextView timelineCompleted;
    private TextView timelineNotes;
    private View adminNoteCard;
    private TextView adminNoteTitle;
    private TextView adminNoteText;
    private TextView beforeRequired;
    private TextView afterRequired;
    private TextView actionHint;
    private RecyclerView beforeRecycler;
    private RecyclerView afterRecycler;
    private EvidenceAdapter beforeAdapter;
    private EvidenceAdapter afterAdapter;
    private TextInputLayout taskNotesLayout;
    private TextInputEditText taskNotesInput;
    private MaterialButton btnAccept;
    private MaterialButton btnStart;
    private MaterialButton btnComplete;
    private View actionBar;
    private ProgressBar progress;

    private String pendingPhase;
    private File pendingCameraFile;

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                File captured = pendingCameraFile;
                pendingCameraFile = null;
                if (success && captured != null) {
                    compressAndUpload(Uri.fromFile(captured), captured);
                } else {
                    deleteQuietly(captured);
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    compressAndUpload(uri, null);
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            launchCamera();
                        } else {
                            Toast.makeText(this, R.string.evidence_camera_denied,
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_task_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.taskDetailRoot),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right,
                            systemBars.bottom);
                    return insets;
                });

        assignmentId = getIntent().getStringExtra(EXTRA_ASSIGNMENT_ID);
        if (assignmentId == null || assignmentId.trim().isEmpty()) {
            Toast.makeText(this, R.string.task_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        assignmentId = assignmentId.trim();

        repository = new TaskRepository(this);
        historyRepository = new TaskHistoryRepository(this);
        bindViews();
        wireActions();
        loadTask();
    }

    private void bindViews() {
        reportImage = findViewById(R.id.reportImage);
        detailTitle = findViewById(R.id.detailTitle);
        detailStatus = findViewById(R.id.detailStatus);
        detailMeta = findViewById(R.id.detailMeta);
        reportTitle = findViewById(R.id.reportTitle);
        reportCategory = findViewById(R.id.reportCategory);
        reportDescription = findViewById(R.id.reportDescription);
        reportLocation = findViewById(R.id.reportLocation);
        reportWasteScale = findViewById(R.id.reportWasteScale);
        reportDispatchDetails = findViewById(R.id.reportDispatchDetails);
        reportAssignedTeamRow = findViewById(R.id.reportAssignedTeamRow);
        reportAssignedTeam = findViewById(R.id.reportAssignedTeam);
        reportAssignedVehicleRow = findViewById(R.id.reportAssignedVehicleRow);
        reportAssignedVehicle = findViewById(R.id.reportAssignedVehicle);
        timelineAccepted = findViewById(R.id.timelineAccepted);
        timelineStarted = findViewById(R.id.timelineStarted);
        timelineCompleted = findViewById(R.id.timelineCompleted);
        timelineNotes = findViewById(R.id.timelineNotes);
        adminNoteCard = findViewById(R.id.adminNoteCard);
        adminNoteTitle = findViewById(R.id.adminNoteTitle);
        adminNoteText = findViewById(R.id.adminNoteText);
        beforeRequired = findViewById(R.id.beforeRequired);
        afterRequired = findViewById(R.id.afterRequired);
        actionHint = findViewById(R.id.actionHint);
        beforeRecycler = findViewById(R.id.beforeRecycler);
        afterRecycler = findViewById(R.id.afterRecycler);
        taskNotesLayout = findViewById(R.id.taskNotesLayout);
        taskNotesInput = findViewById(R.id.taskNotesInput);
        btnAccept = findViewById(R.id.btnAccept);
        btnStart = findViewById(R.id.btnStart);
        btnComplete = findViewById(R.id.btnComplete);
        actionBar = findViewById(R.id.detailActionBar);
        progress = findViewById(R.id.detailProgress);

        beforeAdapter = new EvidenceAdapter(PHASE_BEFORE, evidenceListener);
        afterAdapter = new EvidenceAdapter(PHASE_AFTER, evidenceListener);
        beforeRecycler.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        afterRecycler.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        beforeRecycler.setAdapter(beforeAdapter);
        afterRecycler.setAdapter(afterAdapter);
    }

    private void wireActions() {
        findViewById(R.id.taskBackBtn).setOnClickListener(v -> finish());
        btnAccept.setOnClickListener(v -> acceptTask());
        btnStart.setOnClickListener(v -> startTask());
        btnComplete.setOnClickListener(v -> completeTask());
    }

    private final EvidenceAdapter.Listener evidenceListener = new EvidenceAdapter.Listener() {
        @Override
        public void onAddPhoto(String phase) {
            pendingPhase = phase;
            showPhotoSourceDialog();
        }

        @Override
        public void onRemovePhoto(String phase, PersonnelEvidence evidence) {
            confirmRemoveEvidence(evidence);
        }
    };

    // -------------------------------------------------------------- loading ---

    private void loadTask() {
        setBusy(true);
        repository.fetchTask(assignmentId, new ApiCallback<PersonnelTask>() {
            @Override
            public void onSuccess(PersonnelTask result) {
                setBusy(false);
                render(result);
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void render(PersonnelTask loaded) {
        task = loaded;
        if (task == null) {
            Toast.makeText(this, R.string.task_missing, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Every mutation on this screen ends back here with the server's own view
        // of the task, so writing it to the saved history now means a cleaner who
        // opens Task History right after completing a task sees the new status
        // instead of the previous one.
        historyRepository.save(task);

        String title = task.reportTitle();
        detailTitle.setText(title);
        reportTitle.setText(title);

        detailStatus.setText(task.statusLabel());
        detailStatus.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(statusColor(task)));

        StringBuilder meta = new StringBuilder();
        if (notBlank(task.reportId)) {
            meta.append("Report ").append(task.reportId);
        }
        if (notBlank(task.assignedAt)) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append("Assigned ").append(NotificationDateFormatter.format(task.assignedAt));
        }
        detailMeta.setText(meta.length() == 0 ? "" : meta.toString());

        renderReport(task.report);
        renderDispatchDetails(task);
        renderAdminNote(task);
        renderTimeline(task);
        renderEvidence(task);
        renderActions(task);
    }

    private void renderReport(TaskReport report) {
        if (report == null) {
            reportCategory.setVisibility(View.GONE);
            reportDescription.setVisibility(View.GONE);
            reportLocation.setVisibility(View.GONE);
            reportImage.setVisibility(View.GONE);
            return;
        }

        if (notBlank(report.category)) {
            reportCategory.setVisibility(View.VISIBLE);
            reportCategory.setText(report.category.trim());
        } else {
            reportCategory.setVisibility(View.GONE);
        }

        if (notBlank(report.description)) {
            reportDescription.setVisibility(View.VISIBLE);
            reportDescription.setText(report.description.trim());
        } else {
            reportDescription.setVisibility(View.GONE);
        }

        if (report.latitude != null && report.longitude != null) {
            reportLocation.setVisibility(View.VISIBLE);
            reportLocation.setText(String.format(Locale.US, "Location: %.5f, %.5f",
                    report.latitude, report.longitude));
        } else {
            reportLocation.setVisibility(View.GONE);
        }

        if (notBlank(report.imageUrl)) {
            reportImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(ServerConfig.absoluteUrl(this, report.imageUrl))
                    .placeholder(R.drawable.bg_photo_thumbnail)
                    .error(R.drawable.bg_photo_thumbnail)
                    .centerCrop()
                    .into(reportImage);
        } else {
            reportImage.setVisibility(View.GONE);
        }
    }

    /**
     * Shows the size of the job and what was dispatched to deal with it.
     *
     * <p>The scale is always shown: it is the one thing that tells a cleaner
     * whether they are walking in alone or arriving as half a crew with a
     * truck. The crew and truck rows only appear when there are ones, because a
     * small report is handled by a single cleaner and has neither, and empty
     * rows would only be noise.
     */
    private void renderDispatchDetails(PersonnelTask task) {
        if (reportWasteScale != null) {
            reportWasteScale.setText(WasteScaleDisplay.labelFor(this,
                    task == null ? null : task.getWasteScale()));
        }
        if (reportDispatchDetails == null) return;

        String team = task == null ? null : task.getAssignedTeamLabel();
        if (reportAssignedTeamRow != null) {
            reportAssignedTeamRow.setVisibility(team == null ? View.GONE : View.VISIBLE);
        }
        if (reportAssignedTeam != null) {
            reportAssignedTeam.setText(team == null ? "" : team);
        }

        String vehicle = task == null ? null : task.getAssignedVehicleLabel();
        if (reportAssignedVehicleRow != null) {
            reportAssignedVehicleRow.setVisibility(vehicle == null ? View.GONE : View.VISIBLE);
        }
        if (reportAssignedVehicle != null) {
            reportAssignedVehicle.setText(vehicle == null ? "" : vehicle);
        }

        reportDispatchDetails.setVisibility(
                team == null && vehicle == null ? View.GONE : View.VISIBLE);
    }

    /**
     * Shows the administrator's note for this report.
     *
     * <p>Prefers the report's {@code personnel_note}, the channel the assignment
     * flow writes into for the cleaner (instructions on assign, the reviewer's
     * reason on a revision). The resident-facing {@code admin_response} is only
     * a fallback, so a cleaner never has to read a message that was written for
     * the resident, and a revision request still shows its reason on the task
     * and not just in the notification that told them to reopen it.
     */
    private void renderAdminNote(PersonnelTask task) {
        if (adminNoteCard == null) return;

        String note = adminNoteFor(task);
        if (PersonnelTask.STATUS_CANCELLED.equals(task.status) || !notBlank(note)) {
            adminNoteCard.setVisibility(View.GONE);
            return;
        }

        adminNoteCard.setVisibility(View.VISIBLE);
        if (adminNoteTitle != null) {
            adminNoteTitle.setText(R.string.admin_note_section);
        }
        if (adminNoteText != null) {
            adminNoteText.setText(note.trim());
        }
    }

    /** The admin note addressed to this cleaner, with the resident response as fallback. */
    private String adminNoteFor(PersonnelTask task) {
        if (task == null || task.report == null) return null;
        if (notBlank(task.report.personnelNote)) return task.report.personnelNote;
        return task.report.adminResponse;
    }

    private void renderTimeline(PersonnelTask task) {
        setOrHide(timelineAccepted, "Accepted", task.acceptedAt);
        setOrHide(timelineStarted, "Started", task.startedAt);
        setOrHide(timelineCompleted, "Completed", task.completedAt);

        if (notBlank(task.completionNotes)) {
            timelineNotes.setVisibility(View.VISIBLE);
            timelineNotes.setText("Notes: " + task.completionNotes.trim());
        } else {
            timelineNotes.setVisibility(View.GONE);
        }
    }

    private void setOrHide(TextView view, String label, String timestamp) {
        if (notBlank(timestamp)) {
            view.setVisibility(View.VISIBLE);
            view.setText(label + ": " + NotificationDateFormatter.format(timestamp));
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void renderEvidence(PersonnelTask task) {
        TaskPermissions permissions = task.permissions;

        List<PersonnelEvidence> before = task.evidence == null ? null : task.evidence.before;
        List<PersonnelEvidence> after = task.evidence == null ? null : task.evidence.after;

        beforeAdapter.submit(before,
                permissions != null && permissions.canUploadBefore,
                permissions != null && permissions.canDeleteEvidence);
        afterAdapter.submit(after,
                permissions != null && permissions.canUploadAfter,
                permissions != null && permissions.canDeleteEvidence);

        beforeRequired.setText(task.beforeCount > 0
                ? task.beforeCount + " added" : getString(R.string.evidence_required));
        afterRequired.setText(task.afterCount > 0
                ? task.afterCount + " added" : getString(R.string.evidence_required));
    }

    private void renderActions(PersonnelTask task) {
        TaskPermissions permissions = task.permissions;
        boolean assigned = PersonnelTask.STATUS_ASSIGNED.equals(task.status);
        boolean accepted = PersonnelTask.STATUS_ACCEPTED.equals(task.status);
        boolean inProgress = PersonnelTask.STATUS_IN_PROGRESS.equals(task.status);

        btnAccept.setVisibility(assigned ? View.VISIBLE : View.GONE);
        btnAccept.setEnabled(permissions != null && permissions.canAccept);

        btnStart.setVisibility(accepted ? View.VISIBLE : View.GONE);
        btnStart.setEnabled(permissions != null && permissions.canStart);

        btnComplete.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        btnComplete.setEnabled(permissions != null && permissions.canComplete);

        taskNotesLayout.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        if (inProgress && notBlank(task.completionNotes)
                && (taskNotesInput.getText() == null
                || taskNotesInput.getText().length() == 0)) {
            taskNotesInput.setText(task.completionNotes);
        }

        boolean anyButton = assigned || accepted || inProgress;
        actionBar.setVisibility(anyButton ? View.VISIBLE : View.GONE);

        String hint = null;
        if (accepted && (permissions == null || !permissions.canStart)) {
            hint = getString(R.string.task_need_before);
        } else if (inProgress && (permissions == null || !permissions.canComplete)) {
            hint = getString(R.string.task_need_after);
        } else if (PersonnelTask.STATUS_COMPLETED.equals(task.status)) {
            hint = getString(R.string.task_completed_hint);
        } else if (PersonnelTask.STATUS_CANCELLED.equals(task.status)) {
            hint = getString(R.string.task_cancelled_hint);
        }

        if (hint == null) {
            actionHint.setVisibility(View.GONE);
        } else {
            actionHint.setVisibility(View.VISIBLE);
            actionHint.setText(hint);
        }
    }

    // -------------------------------------------------------------- actions ---

    private void acceptTask() {
        setBusy(true);
        repository.acceptTask(assignmentId, new ApiCallback<PersonnelTask>() {
            @Override
            public void onSuccess(PersonnelTask result) {
                setBusy(false);
                render(result);
                Toast.makeText(TaskDetailActivity.this, R.string.task_accepted,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startTask() {
        setBusy(true);
        repository.startTask(assignmentId, new ApiCallback<PersonnelTask>() {
            @Override
            public void onSuccess(PersonnelTask result) {
                setBusy(false);
                render(result);
                Toast.makeText(TaskDetailActivity.this, R.string.task_started,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void completeTask() {
        setBusy(true);
        String notes = taskNotesInput.getText() == null
                ? null : taskNotesInput.getText().toString();
        repository.completeTask(assignmentId, notes, new ApiCallback<PersonnelTask>() {
            @Override
            public void onSuccess(PersonnelTask result) {
                setBusy(false);
                render(result);
                Toast.makeText(TaskDetailActivity.this, R.string.task_completed_toast,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                setBusy(false);
                Toast.makeText(TaskDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmRemoveEvidence(PersonnelEvidence evidence) {
        if (evidence == null || !notBlank(evidence.evidenceId)) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.evidence_remove_title)
                .setMessage(R.string.evidence_remove_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.evidence_remove, (dialog, which) ->
                        removeEvidence(evidence))
                .show();
    }

    private void removeEvidence(PersonnelEvidence evidence) {
        setBusy(true);
        repository.deleteEvidence(assignmentId, evidence.evidenceId,
                new ApiCallback<PersonnelTask>() {
                    @Override
                    public void onSuccess(PersonnelTask result) {
                        setBusy(false);
                        render(result);
                    }

                    @Override
                    public void onError(String message) {
                        setBusy(false);
                        Toast.makeText(TaskDetailActivity.this, message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ------------------------------------------------------------- evidence ---

    private void showPhotoSourceDialog() {
        String[] options = {
                getString(R.string.add_photo_camera),
                getString(R.string.add_photo_gallery)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_photo_title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        requestCamera();
                    } else {
                        pickImageLauncher.launch("image/*");
                    }
                })
                .show();
    }

    private void requestCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            pendingCameraFile = createImageFile();
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", pendingCameraFile);
            takePictureLauncher.launch(uri);
        } catch (IOException | IllegalArgumentException error) {
            deleteQuietly(pendingCameraFile);
            pendingCameraFile = null;
            Toast.makeText(this, R.string.evidence_camera_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void compressAndUpload(Uri source, File sourceToDelete) {
        if (pendingPhase == null) {
            deleteQuietly(sourceToDelete);
            return;
        }

        File compressed = null;
        try {
            compressed = createImageFile();
            ImageCompressor.writeScaledJpeg(this, source, compressed);
        } catch (IOException | RuntimeException error) {
            deleteQuietly(compressed);
            deleteQuietly(sourceToDelete);
            Toast.makeText(this, R.string.evidence_prepare_failed, Toast.LENGTH_LONG).show();
            return;
        }

        deleteQuietly(sourceToDelete);
        uploadEvidence(compressed);
    }

    private void uploadEvidence(File file) {
        final String phase = pendingPhase;
        if (task == null || phase == null) {
            deleteQuietly(file);
            return;
        }

        setBusy(true);
        repository.uploadEvidence(assignmentId, phase, file, "image/jpeg",
                new ApiCallback<PersonnelTask>() {
                    @Override
                    public void onSuccess(PersonnelTask result) {
                        setBusy(false);
                        deleteQuietly(file);
                        pendingPhase = null;
                        render(result);
                        Toast.makeText(TaskDetailActivity.this, R.string.evidence_uploaded,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        setBusy(false);
                        deleteQuietly(file);
                        Toast.makeText(TaskDetailActivity.this, message,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());
        File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (directory != null && !directory.exists()) {
            directory.mkdirs();
        }
        return File.createTempFile("ECO_" + timeStamp + "_", ".jpg", directory);
    }

    // ---------------------------------------------------------------- bits ---

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private static int statusColor(PersonnelTask task) {
        if (task == null) {
            return android.graphics.Color.parseColor("#607D8B");
        }
        if (task.awaitingApproval()
                || PersonnelTask.STATUS_UNDER_APPROVAL.equalsIgnoreCase(task.status)
                || "UNDER APPROVAL".equalsIgnoreCase(task.status)) {
            return android.graphics.Color.parseColor("#8E24AA");
        }
        String status = task.status;
        if (status == null) {
            return android.graphics.Color.parseColor("#607D8B");
        }
        switch (status) {
            case PersonnelTask.STATUS_ASSIGNED:
                return android.graphics.Color.parseColor("#F57C00");
            case PersonnelTask.STATUS_ACCEPTED:
                return android.graphics.Color.parseColor("#1976D2");
            case PersonnelTask.STATUS_IN_PROGRESS:
                return android.graphics.Color.parseColor("#F9A825");
            case PersonnelTask.STATUS_UNDER_APPROVAL:
                return android.graphics.Color.parseColor("#8E24AA");
            case PersonnelTask.STATUS_COMPLETED:
                return android.graphics.Color.parseColor("#2E7D32");
            case PersonnelTask.STATUS_CANCELLED:
                return android.graphics.Color.parseColor("#C62828");
            default:
                return android.graphics.Color.parseColor("#607D8B");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deleteQuietly(pendingCameraFile);
    }
}

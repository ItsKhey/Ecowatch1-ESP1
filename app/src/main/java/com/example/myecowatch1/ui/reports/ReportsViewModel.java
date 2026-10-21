package com.example.myecowatch1.ui.reports;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.repository.ReportRepository;
import com.example.myecowatch1.utils.NearbyReportPayload;
import com.example.myecowatch1.utils.SessionManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReportsViewModel extends AndroidViewModel {

    private final ReportRepository repository;
    private final MutableLiveData<Event<String>> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> submissionSuccessLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submittingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Event<Map<String, Object>>> nearbyReportLiveData = new MutableLiveData<>();
    private final AtomicBoolean operationInFlight = new AtomicBoolean(false);
    private final SessionManager sessionManager;
    private Report pendingCreateReport;
    private List<File> pendingCreateImages;
    private String pendingCreateResidentId;
    private double pendingCreateLat;
    private double pendingCreateLon;

    private LiveData<List<Report>> inboxReports;
    private LiveData<List<Report>> archivedReports;
    private LiveData<List<Report>> trashReports;
    private LiveData<List<Report>> allReports;

    public ReportsViewModel(@NonNull Application application) {
        super(application);
        repository = new ReportRepository(application);
        sessionManager = new SessionManager(application);
        
        // Initialize LiveData references once
        inboxReports = repository.getInboxReports();
        archivedReports = repository.getArchivedReports();
        trashReports = repository.getTrashReports();
        allReports = repository.getAllReports();
    }

    public LiveData<List<Report>> getAllReports() {
        return allReports;
    }

    public LiveData<List<Report>> getInboxReports() {
        return inboxReports;
    }

    public LiveData<List<Report>> getArchivedReports() {
        return archivedReports;
    }

    public LiveData<List<Report>> getTrashReports() {
        return trashReports;
    }

    public void softDeleteReport(Report report) {
        repository.softDeleteReport(report);
    }

    public void restoreReport(Report report) {
        repository.restoreReport(report);
    }

    public void archiveReport(Report report) {
        repository.archiveReport(report);
    }

    public void bulkSoftDelete(List<Integer> ids) {
        repository.bulkSoftDelete(ids);
    }

    public void bulkPermanentDelete(List<Integer> ids) {
        repository.bulkPermanentDelete(ids);
    }

    public void bulkArchive(List<Integer> ids) {
        repository.bulkArchive(ids);
    }

    public void bulkRestore(List<Integer> ids) {
        repository.bulkRestore(ids);
    }

    public void cleanupTrash() {
        repository.cleanupTrash();
    }

    public LiveData<Event<String>> getError() {
        return errorLiveData;
    }

    public LiveData<Event<Boolean>> getSubmissionSuccess() {
        return submissionSuccessLiveData;
    }

    public LiveData<Boolean> getSubmitting() {
        return submittingLiveData;
    }

    /** A report already exists near the submitted location. */
    public LiveData<Event<Map<String, Object>>> getNearbyReportDetection() {
        return nearbyReportLiveData;
    }

    public void submitReport(String title, String description, String wasteType, File imageFile) {
        submitReport(title, description, wasteType, imageFile, 0, 0);
    }

    public void submitReport(String title, String description, String wasteType, File imageFile, double lat, double lon) {
        if (title == null || title.trim().isEmpty()) {
            errorLiveData.setValue(new Event<>("Title is required."));
            return;
        }
        if (imageFile == null || !imageFile.isFile() || imageFile.length() <= 0L) {
            errorLiveData.setValue(new Event<>("Please select an image before submitting."));
            return;
        }
        if (!operationInFlight.compareAndSet(false, true)) {
            return;
        }

        submittingLiveData.setValue(true);
        String currentResidentId = sessionManager.fetchResidentId();
        String imagePath = imageFile.getAbsolutePath();
        String normalizedTitle = title.trim();
        String normalizedDescription = description != null ? description.trim() : "";

        // Reuse the same provisional report ID for an immediate retry of the
        // same form/image. Render can then recognize the retry as an idempotent
        // replay instead of creating a second report after a lost response.
        if (pendingCreateReport == null
                || !sameCreateInput(pendingCreateReport, normalizedTitle, normalizedDescription,
                wasteType, lat, lon, imagePath, currentResidentId)) {
            pendingCreateReport = new Report(normalizedTitle, normalizedDescription,
                    wasteType, lat, lon);
            pendingCreateReport.setResidentId(currentResidentId);
            pendingCreateImages = new ArrayList<>();
            pendingCreateImages.add(imageFile);
            pendingCreateResidentId = currentResidentId;
        }
        final Report report = pendingCreateReport;

        try {
            repository.submitReport(report, imageFile, new ReportRepository.ApiCallback<Report>() {
                @Override
                public void onSuccess(Report result) {
                    clearPendingCreate();
                    finishOperation();
                    submissionSuccessLiveData.postValue(new Event<>(true));
                }

                @Override
                public void onError(String error) {
                    finishOperation();
                    errorLiveData.postValue(new Event<>(error));
                }
            });
        } catch (RuntimeException error) {
            finishOperation();
            errorLiveData.setValue(new Event<>("Unable to submit report: " + error.getMessage()));
        }
    }

    /**
     * Submits a report with multiple photos. The server may answer with a
     * nearby-report hand-off instead of a created report.
     */
    public void submitReportMultiple(String title, String description, String wasteType,
                                     List<File> imageFiles, double lat, double lon) {
        if (title == null || title.trim().isEmpty()) {
            errorLiveData.setValue(new Event<>("Title is required."));
            return;
        }
        if (imageFiles == null || imageFiles.isEmpty()) {
            errorLiveData.setValue(new Event<>("Please select at least one photo."));
            return;
        }
        if (!operationInFlight.compareAndSet(false, true)) {
            return;
        }

        submittingLiveData.setValue(true);
        String currentResidentId = sessionManager.fetchResidentId();
        String normalizedTitle = title.trim();
        String normalizedDescription = description != null ? description.trim() : "";

        pendingCreateReport = new Report(normalizedTitle, normalizedDescription,
                wasteType, lat, lon);
        pendingCreateReport.setResidentId(currentResidentId);
        pendingCreateImages = new ArrayList<>(imageFiles);
        pendingCreateResidentId = currentResidentId;
        pendingCreateLat = lat;
        pendingCreateLon = lon;

        final Report report = pendingCreateReport;

        try {
            repository.submitReportMultiple(report, imageFiles, lat, lon,
                    new ReportRepository.ApiCallback<Report>() {
                @Override
                public void onSuccess(Report result) {
                    clearPendingCreate();
                    finishOperation();
                    submissionSuccessLiveData.postValue(new Event<>(true));
                }

                @Override
                public void onError(String error) {
                    Map<String, Object> nearbyData = NearbyReportPayload.decode(error);
                    if (nearbyData != null) {
                        // A report already exists here, so this needs a UI
                        // decision rather than an error. The pending submission is
                        // intentionally kept so the resident can either send it
                        // anyway or walk away.
                        finishOperation();
                        nearbyReportLiveData.postValue(new Event<>(nearbyData));
                        return;
                    }

                    finishOperation();
                    if (NearbyReportPayload.isNearbyReportPayload(error)) {
                        // Recognised as a nearby-report hand-off but unreadable.
                        // Report it as retryable instead of leaking a parse
                        // failure; the pending submission is still intact.
                        errorLiveData.postValue(new Event<>(
                                "Could not read the nearby report details. Please submit again."));
                        return;
                    }
                    errorLiveData.postValue(new Event<>(error));
                }
            });
        } catch (RuntimeException error) {
            finishOperation();
            errorLiveData.setValue(new Event<>("Unable to submit report: " + error.getMessage()));
        }
    }

    /**
     * Sends the held submission even though a report already exists nearby.
     *
     * <p>The nearby group id is a server-side grouping detail; it is passed
     * straight through so the report is filed alongside the existing one, and is
     * never shown to the resident.
     */
    public void submitDespiteNearbyReport(String nearbyGroupId) {
        if (pendingCreateReport == null || pendingCreateImages == null || pendingCreateImages.isEmpty()) {
            errorLiveData.setValue(new Event<>("No pending report to submit."));
            return;
        }
        if (!operationInFlight.compareAndSet(false, true)) {
            return;
        }

        submittingLiveData.setValue(true);
        final Report report = pendingCreateReport;

        try {
            repository.submitReportWithNearbyGroup(report, pendingCreateImages,
                    pendingCreateLat, pendingCreateLon, nearbyGroupId,
                    new ReportRepository.ApiCallback<Report>() {
                @Override
                public void onSuccess(Report result) {
                    clearPendingCreate();
                    finishOperation();
                    submissionSuccessLiveData.postValue(new Event<>(true));
                }

                @Override
                public void onError(String error) {
                    finishOperation();
                    errorLiveData.postValue(new Event<>(error));
                }
            });
        } catch (RuntimeException error) {
            finishOperation();
            errorLiveData.setValue(new Event<>("Unable to submit report: " + error.getMessage()));
        }
    }

    /**
     * Files the held submission without asking the resident to confirm it.
     *
     * <p>Used when the nearby hand-off turned out to have nothing outstanding
     * behind it. There is no cluster to attach the report to, so the ordinary
     * submit path is taken and the server decides: with no live report at the
     * spot it files the report under a fresh cluster instead of warning again.
     *
     * <p>The same provisional report ID is reused, so this stays an idempotent
     * replay rather than a second report, should the first attempt have partly
     * landed.
     */
    public void submitPendingCreateReport() {
        if (pendingCreateReport == null || pendingCreateImages == null
                || pendingCreateImages.isEmpty()) {
            errorLiveData.setValue(new Event<>("No pending report to submit."));
            return;
        }
        if (!operationInFlight.compareAndSet(false, true)) {
            return;
        }

        submittingLiveData.setValue(true);
        final Report report = pendingCreateReport;

        try {
            repository.submitReportMultiple(report, pendingCreateImages,
                    pendingCreateLat, pendingCreateLon,
                    new ReportRepository.ApiCallback<Report>() {
                        @Override
                        public void onSuccess(Report result) {
                            clearPendingCreate();
                            finishOperation();
                            submissionSuccessLiveData.postValue(new Event<>(true));
                        }

                        @Override
                        public void onError(String error) {
                            Map<String, Object> nearbyData = NearbyReportPayload.decode(error);
                            if (nearbyData != null) {
                                // The spot genuinely has work on it after all.
                                // Hand it back so the resident still gets the
                                // choice instead of a silent retry.
                                finishOperation();
                                nearbyReportLiveData.postValue(new Event<>(nearbyData));
                                return;
                            }
                            finishOperation();
                            errorLiveData.postValue(new Event<>(error));
                        }
                    });
        } catch (RuntimeException error) {
            finishOperation();
            errorLiveData.setValue(new Event<>("Unable to submit report: " + error.getMessage()));
        }
    }

    private void clearPendingCreate() {
        pendingCreateReport = null;
        pendingCreateImages = null;
        pendingCreateResidentId = null;
    }

    /**
     * Sends a resident edit to the backend and refreshes every surface that
     * reflects the report once the server has confirmed it.
     *
     * <p>The repository reconciles the report row and the "UPDATED" history
     * entry; the notification centre is refreshed here so its badge and list
     * cannot keep serving the pre-edit state.
     */
    public void updateReport(Report report, File imageFile) {
        if (report == null || report.getTitle() == null || report.getTitle().trim().isEmpty()) {
            errorLiveData.setValue(new Event<>("Title is required."));
            return;
        }
        if (!operationInFlight.compareAndSet(false, true)) {
            return;
        }

        submittingLiveData.setValue(true);
        try {
            repository.updateReport(report, imageFile, new ReportRepository.ApiCallback<Report>() {
                @Override
                public void onSuccess(Report result) {
                    finishOperation();
                    refreshNotifications();
                    submissionSuccessLiveData.postValue(new Event<>(true));
                }

                @Override
                public void onError(String error) {
                    finishOperation();
                    errorLiveData.postValue(new Event<>(error));
                }
            });
        } catch (RuntimeException error) {
            finishOperation();
            errorLiveData.setValue(new Event<>("Unable to update report: " + error.getMessage()));
        }
    }

    /** Pulls the notification centre so it matches the freshly edited report. */
    private void refreshNotifications() {
        try {
            com.example.myecowatch1.data.repository.NotificationRepository
                    .getInstance(getApplication())
                    .fetchNotifications();
        } catch (RuntimeException error) {
            // A notification refresh failure must not turn a committed edit into
            // a reported error.
            android.util.Log.w("ReportsViewModel",
                    "Unable to refresh notifications after editing a report", error);
        }
    }

    private boolean sameCreateInput(Report report, String title, String description,
                                    String wasteType, double lat, double lon,
                                    String imagePath, String residentId) {
        return report != null
                && java.util.Objects.equals(report.getTitle(), title)
                && java.util.Objects.equals(report.getDescription(), description)
                && java.util.Objects.equals(report.getWasteType(), wasteType)
                && Double.compare(report.getLatitude(), lat) == 0
                && Double.compare(report.getLongitude(), lon) == 0
                && java.util.Objects.equals(pendingCreateResidentId, residentId);
    }

    private void finishOperation() {
        operationInFlight.set(false);
        submittingLiveData.postValue(false);
    }

    /** A one-shot value so rotation/recreation cannot replay an old result. */
    public static final class Event<T> {
        private final T content;
        private boolean handled;

        public Event(T content) {
            this.content = content;
        }

        public T getContentIfNotHandled() {
            if (handled) return null;
            handled = true;
            return content;
        }
    }

    public void deleteReport(Report report) {
        repository.deleteReport(report, null);
    }

    public void refreshReports() {
        repository.fetchMyReports();
    }
}
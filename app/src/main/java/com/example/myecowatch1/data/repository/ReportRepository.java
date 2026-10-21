package com.example.myecowatch1.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.myecowatch1.data.local.AppDatabase;
import com.example.myecowatch1.data.local.ReportDao;
import com.example.myecowatch1.data.local.HistoryDao;
import com.example.myecowatch1.data.local.HistoryTombstoneDao;
import com.example.myecowatch1.data.models.Report;
import com.example.myecowatch1.data.models.History;
import com.example.myecowatch1.data.models.HistoryTombstone;
import com.example.myecowatch1.data.remote.ApiService;
import com.example.myecowatch1.data.remote.RetrofitClient;
import com.example.myecowatch1.utils.NearbyReportPayload;
import com.example.myecowatch1.utils.SessionManager;

import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportRepository {
    /** Posts refresh-completion callbacks back to the UI thread (API 24 safe). */
    private static final android.os.Handler MAIN_HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private ReportDao reportDao;
    private HistoryDao historyDao;
    private HistoryTombstoneDao historyTombstoneDao;
    private ApiService apiService;
    private SessionManager sessionManager;
    private ExecutorService executorService;
    private Application application;

    public ReportRepository(Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getInstance(application);
        reportDao = db.reportDao();
        historyDao = db.historyDao();
        historyTombstoneDao = db.historyTombstoneDao();
        apiService = RetrofitClient.getApiService(application);
        sessionManager = new SessionManager(application);
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Report>> getAllReports() {
        String residentId = sessionManager.fetchResidentId();
        return reportDao.getAllReports(residentId != null ? residentId : "");
    }

    public LiveData<List<Report>> getInboxReports() {
        String residentId = sessionManager.fetchResidentId();
        return reportDao.getInboxReports(residentId != null ? residentId : "");
    }

    public LiveData<List<Report>> getArchivedReports() {
        String residentId = sessionManager.fetchResidentId();
        return reportDao.getArchivedReports(residentId != null ? residentId : "");
    }

    public LiveData<List<Report>> getTrashReports() {
        String residentId = sessionManager.fetchResidentId();
        return reportDao.getTrashReports(residentId != null ? residentId : "");
    }

    public LiveData<List<History>> getHistoryByResidentId(String residentId) {
        return historyDao.getHistoryByResidentId(residentId);
    }

    public LiveData<List<History>> getTrashHistoryByResidentId(String residentId) {
        return historyDao.getTrashHistoryByResidentId(residentId);
    }

    public void softDeleteReport(Report report) {
        executorService.execute(() -> {
            long deletedAt = System.currentTimeMillis();
            reportDao.softDelete(report.getId(), deletedAt);
            
            // Log to history
            History history = new History();
            history.setResidentId(sessionManager.fetchResidentId());
            history.setReportId(report.getReportId());
            history.setAction("MOVED_TO_TRASH");
            history.setOldStatus(report.getStatus());
            history.setNewStatus(report.getStatus());
            history.setDescription("Report moved to trash: " + report.getTitle());
            history.setTimestampValue(deletedAt);
            historyDao.insertHistory(history);
            
            // Sync to server move to trash collection
            String targetId = getServerTargetId(report);
            if (targetId != null) {
                Log.d("ReportRepository", "Moving to trash: " + targetId);
                apiService.moveToTrash(targetId).enqueue(new Callback<Map<String, Object>>() {
                    @Override 
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            Log.d("ReportRepository", "Successfully moved to trash collection in MongoDB");
                        } else {
                            Log.e("ReportRepository", "Failed to move to trash. Code: " + response.code() + " ID used: " + targetId);
                        }
                    }
                    @Override 
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Log.e("ReportRepository", "Network error moving to trash", t);
                    }
                });
            } else {
                Log.e("ReportRepository", "Cannot move to trash: MongoDB ID is null for " + report.getReportId());
            }
        });
    }

    public void restoreReport(Report report) {
        executorService.execute(() -> {
            reportDao.restoreReport(report.getId());
            
            // Log to history
            History history = new History();
            history.setResidentId(sessionManager.fetchResidentId());
            history.setReportId(report.getReportId());
            history.setAction("RESTORED");
            history.setOldStatus("Trashed");
            history.setNewStatus(report.getStatus());
            history.setDescription("Report restored: " + report.getTitle());
            history.setTimestampValue(System.currentTimeMillis());
            historyDao.insertHistory(history);
            
            String targetId = getServerTargetId(report);
            if (targetId != null) {
                apiService.restoreFromCollection(targetId).enqueue(new Callback<Map<String, Object>>() {
                    @Override 
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            Log.d("ReportRepository", "Successfully restored report in MongoDB");
                        } else {
                            Log.e("ReportRepository", "Failed to restore: " + response.code());
                        }
                    }
                    @Override 
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Log.e("ReportRepository", "Network error restoring report", t);
                    }
                });
            }
        });
    }

    public void archiveReport(Report report) {
        executorService.execute(() -> {
            reportDao.archiveReport(report.getId());
            
            // Log to history
            History history = new History();
            history.setResidentId(sessionManager.fetchResidentId());
            history.setReportId(report.getReportId());
            history.setAction("ARCHIVED");
            history.setOldStatus(report.getStatus());
            history.setNewStatus(report.getStatus());
            history.setDescription("Report archived: " + report.getTitle());
            history.setTimestampValue(System.currentTimeMillis());
            historyDao.insertHistory(history);
            
            String targetId = getServerTargetId(report);
            if (targetId != null) {
                Log.d("ReportRepository", "Moving to archive: " + targetId);
                apiService.moveToArchive(targetId).enqueue(new Callback<Map<String, Object>>() {
                    @Override 
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            Log.d("ReportRepository", "Successfully moved to archive collection in MongoDB");
                        } else {
                            Log.e("ReportRepository", "Failed to move to archive. Code: " + response.code() + " ID used: " + targetId);
                        }
                    }
                    @Override 
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Log.e("ReportRepository", "Network error archiving report", t);
                    }
                });
            } else {
                Log.e("ReportRepository", "Cannot archive: MongoDB ID is null for " + report.getReportId());
            }
        });
    }

    public void bulkSoftDelete(List<Integer> ids) {
        executorService.execute(() -> {
            long deletedAt = System.currentTimeMillis();
            reportDao.bulkSoftDelete(ids, deletedAt);
            
            List<Report> reports = reportDao.getReportsByIds(ids);
            String residentId = sessionManager.fetchResidentId();
            for (Report r : reports) {
                History history = new History();
                history.setResidentId(residentId);
                history.setReportId(r.getReportId());
                history.setAction("MOVED_TO_TRASH");
                history.setOldStatus(r.getStatus());
                history.setNewStatus(r.getStatus());
                history.setDescription("Report moved to trash: " + r.getTitle());
                history.setTimestampValue(deletedAt);
                historyDao.insertHistory(history);
            }
            
            List<String> mongoIds = new ArrayList<>();
            for (Report r : reports) {
                String serverId = getServerTargetId(r);
                if (serverId != null) {
                    mongoIds.add(serverId);
                }
            }
            
            if (!mongoIds.isEmpty()) {
                Map<String, List<String>> body = new HashMap<>();
                body.put("ids", mongoIds);
                apiService.bulkMoveToTrash(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
        });
    }

    public void bulkPermanentDelete(List<Integer> ids) {
        executorService.execute(() -> {
            List<Report> reports = reportDao.getReportsByIds(ids);
            String residentId = sessionManager.fetchResidentId();
            long timestamp = System.currentTimeMillis();
            for (Report r : reports) {
                History history = new History();
                history.setResidentId(residentId);
                history.setReportId(r.getReportId());
                history.setAction("PERMANENTLY_DELETED");
                history.setOldStatus(r.getStatus());
                history.setNewStatus("Permanently Deleted");
                history.setDescription("Report permanently deleted: " + r.getTitle());
                history.setTimestampValue(timestamp);
                historyDao.insertHistory(history);
            }
            
            List<String> mongoIds = new ArrayList<>();
            for (Report r : reports) {
                String serverId = getServerTargetId(r);
                if (serverId != null) {
                    mongoIds.add(serverId);
                }
            }
            
            reportDao.bulkDeleteByIds(ids); // I need to add this to DAO

            if (!mongoIds.isEmpty()) {
                Map<String, List<String>> body = new HashMap<>();
                body.put("ids", mongoIds);
                apiService.bulkDelete(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
        });
    }

    public void bulkArchive(List<Integer> ids) {
        executorService.execute(() -> {
            reportDao.bulkArchive(ids);
            
            List<Report> reports = reportDao.getReportsByIds(ids);
            String residentId = sessionManager.fetchResidentId();
            long timestamp = System.currentTimeMillis();
            for (Report r : reports) {
                History history = new History();
                history.setResidentId(residentId);
                history.setReportId(r.getReportId());
                history.setAction("ARCHIVED");
                history.setOldStatus(r.getStatus());
                history.setNewStatus(r.getStatus());
                history.setDescription("Report archived: " + r.getTitle());
                history.setTimestampValue(timestamp);
                historyDao.insertHistory(history);
            }

            List<String> mongoIds = new ArrayList<>();
            for (Report r : reports) {
                String serverId = getServerTargetId(r);
                if (serverId != null) {
                    mongoIds.add(serverId);
                }
            }

            if (!mongoIds.isEmpty()) {
                Map<String, List<String>> body = new HashMap<>();
                body.put("ids", mongoIds);
                apiService.bulkMoveToArchive(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
        });
    }

    public void bulkRestore(List<Integer> ids) {
        executorService.execute(() -> {
            reportDao.bulkRestore(ids);
            
            List<Report> reports = reportDao.getReportsByIds(ids);
            String residentId = sessionManager.fetchResidentId();
            long timestamp = System.currentTimeMillis();
            for (Report r : reports) {
                History history = new History();
                history.setResidentId(residentId);
                history.setReportId(r.getReportId());
                history.setAction("RESTORED");
                history.setOldStatus("Trashed/Archived");
                history.setNewStatus(r.getStatus());
                history.setDescription("Report restored: " + r.getTitle());
                history.setTimestampValue(timestamp);
                historyDao.insertHistory(history);
            }

            List<String> mongoIds = new ArrayList<>();
            for (Report r : reports) {
                String serverId = getServerTargetId(r);
                if (serverId != null) {
                    mongoIds.add(serverId);
                }
            }

            if (!mongoIds.isEmpty()) {
                Map<String, List<String>> body = new HashMap<>();
                body.put("ids", mongoIds);
                apiService.bulkRestore(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
        });
    }

    public void deleteHistoryByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        String residentId = sessionManager.fetchResidentId();
        executorService.execute(() -> {
            List<History> historyItems = historyDao.getHistoryByIds(ids);
            List<String> collected = new ArrayList<>();
            for (History h : historyItems) {
                collected.add(h.getStoredMongoId());
            }
            // One normalisation pass so the tombstone set, the local purge and
            // the upstream payload all reference exactly the same ids.
            List<String> mongoIds = HistorySyncPolicy.normalizeIds(collected);

            // Persist the intent to delete BEFORE removing the rows. This is
            // what stops the next refresh from re-importing these ids: the sync
            // treats a missing local row as "new", so the absence of the row has
            // to be backed by a durable record that the row was deleted on
            // purpose. Writing the tombstone first also means a crash between
            // the two steps cannot resurrect the entries.
            recordHistoryTombstones(mongoIds, residentId);

            historyDao.deleteHistoryByIds(ids);
            // Drop any additional local copies of the same server records so no
            // duplicate survives the delete.
            if (!mongoIds.isEmpty() && residentId != null) {
                historyDao.deleteByMongoIds(mongoIds, residentId);
            }

            // The tombstone already hides these ids from the UI, so an upstream
            // failure can no longer surface deleted rows. The ids stay
            // tombstoned and fetchHistory() re-asserts the delete on the next
            // sync, which makes this self-healing without a rollback.
            if (!mongoIds.isEmpty()) {
                pushHistoryDelete(mongoIds);
            }
        });
    }

    /** Writes one tombstone per server id, ignoring blank/duplicate values. */
    private void recordHistoryTombstones(List<String> mongoIds, String residentId) {
        List<String> normalized = HistorySyncPolicy.normalizeIds(mongoIds);
        if (normalized.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<HistoryTombstone> tombstones = new ArrayList<>();
        for (String mongoId : normalized) {
            tombstones.add(new HistoryTombstone(mongoId, residentId, now));
        }
        historyTombstoneDao.insertAll(tombstones);
    }

    /**
     * Permanently deletes history logs upstream. Failures are logged and
     * deliberately not rolled back: the local tombstone already keeps the
     * entries hidden, and the next sync re-issues the delete for any id the
     * server still returns.
     */
    private void pushHistoryDelete(List<String> mongoIds) {
        Map<String, List<String>> body = new HashMap<>();
        body.put("ids", mongoIds);
        apiService.bulkDeleteHistory(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Log.d("ReportRepository", "History logs permanently deleted from server");
                } else {
                    Log.w("ReportRepository", "Server refused history delete (code "
                            + response.code() + "); entries stay tombstoned and will be retried on sync");
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e("ReportRepository", "Failed to permanently delete history from server; "
                        + "entries stay tombstoned and will be retried on sync", t);
            }
        });
    }

    public void softDeleteHistoryByIds(List<Integer> ids) {
        executorService.execute(() -> {
            List<History> historyItems = historyDao.getHistoryByIds(ids);
            List<String> mongoIds = new ArrayList<>();
            for (History h : historyItems) {
                String serverId = h.getStoredMongoId();
                if (serverId != null && !serverId.trim().isEmpty()) {
                    mongoIds.add(serverId);
                }
            }

            historyDao.bulkSoftDelete(ids, System.currentTimeMillis());

            if (!mongoIds.isEmpty()) {
                Map<String, List<String>> body = new HashMap<>();
                body.put("ids", mongoIds);
                apiService.bulkSoftDeleteHistory(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
        });
    }

    public void restoreHistoryByIds(List<Integer> ids) {
        executorService.execute(() -> {
            List<History> historyItems = historyDao.getHistoryByIds(ids);
            List<String> mongoIds = new ArrayList<>();
            for (History h : historyItems) {
                String serverId = h.getStoredMongoId();
                if (serverId != null && !serverId.trim().isEmpty()) {
                    mongoIds.add(serverId);
                }
            }

            historyDao.bulkRestore(ids);

            if (!mongoIds.isEmpty()) {
                Map<String, List<String>> body = new HashMap<>();
                body.put("ids", mongoIds);
                apiService.bulkRestoreHistory(body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
        });
    }

    public void cleanupTrash() {
        executorService.execute(() -> {
            long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
            reportDao.permanentDeleteOldTrash(thirtyDaysAgo);
        });
    }

    public void submitReport(Report report, File imageFile, ApiCallback<Report> callback) {
        executorService.execute(() -> {
            String residentId = sessionManager.fetchResidentId();
            if (residentId == null || residentId.trim().isEmpty()) {
                notifyError(callback, "Your session has expired. Please log in again.");
                return;
            }

            // A report is not a valid submission without a readable image.  Do
            // this check before touching Room so a failed request cannot leave
            // behind a report that looks submitted to the resident.
            if (!isUsableImage(imageFile)) {
                notifyError(callback, "Please select an image before submitting.");
                return;
            }

            report.setResidentId(residentId);
            report.setSynced(false);

            // Keep a local copy for offline/retry support, but do not create the
            // SUBMITTED history entry until Render has confirmed the upload.
            String localPath = saveImageToInternalStorage(imageFile);
            File uploadFile = imageFile;
            if (localPath != null) {
                report.setLocalPhotoPath(localPath);
                uploadFile = new File(localPath);
            }

            // Keep the operation network-first.  A local row is inserted only
            // after Render returns a confirmed server report ID.
            syncReportToServer(report, uploadFile, callback);
        });
    }

    /**
     * Submits a report with multiple photos. The server may answer with a
     * nearby-report hand-off instead of a created report.
     *
     * @param report       the report to submit
     * @param imageFiles   list of photo files (1-10)
     * @param lat          latitude
     * @param lon          longitude
     * @param callback     callback for success/error
     */
    public void submitReportMultiple(Report report, List<File> imageFiles, double lat, double lon,
                                     ApiCallback<Report> callback) {
        executorService.execute(() -> {
            String residentId = sessionManager.fetchResidentId();
            if (residentId == null || residentId.trim().isEmpty()) {
                notifyError(callback, "Your session has expired. Please log in again.");
                return;
            }

            if (imageFiles == null || imageFiles.isEmpty()) {
                notifyError(callback, "Please select at least one photo before submitting.");
                return;
            }

            // Validate all files before starting
            for (File f : imageFiles) {
                if (!isUsableImage(f)) {
                    notifyError(callback, "One or more photos are invalid. Please re-select them.");
                    return;
                }
            }

            report.setResidentId(residentId);
            report.setSynced(false);
            report.setLatitude(lat);
            report.setLongitude(lon);

            // Save local copies of all photos
            List<File> uploadFiles = new ArrayList<>();
            for (File f : imageFiles) {
                String localPath = saveImageToInternalStorage(f);
                if (localPath != null) {
                    uploadFiles.add(new File(localPath));
                } else {
                    uploadFiles.add(f);
                }
            }

            // Track the on-device copies so a report detail opened before the
            // server responds can still show every selected photo.
            report.setLocalPhotoPath(uploadFiles.isEmpty() ? null : uploadFiles.get(0).getAbsolutePath());

            syncReportToServerMultiple(report, uploadFiles, callback);
        });
    }

    /**
     * Files the held submission alongside the nearby reports by resending with
     * the continue flag and the server's grouping id.
     *
     * <p>The grouping id is a server-side detail and stays on the request and
     * the cached report, never in anything the resident reads.
     */
    public void submitReportWithNearbyGroup(Report report, List<File> imageFiles, double lat, double lon,
                                            String nearbyGroupId, ApiCallback<Report> callback) {
        executorService.execute(() -> {
            String residentId = sessionManager.fetchResidentId();
            if (residentId == null || residentId.trim().isEmpty()) {
                notifyError(callback, "Your session has expired. Please log in again.");
                return;
            }

            if (imageFiles == null || imageFiles.isEmpty()) {
                notifyError(callback, "Please select at least one photo before submitting.");
                return;
            }

            report.setResidentId(residentId);
            report.setSynced(false);
            report.setLatitude(lat);
            report.setLongitude(lon);
            report.setClusterId(nearbyGroupId);

            List<File> uploadFiles = new ArrayList<>();
            for (File f : imageFiles) {
                String localPath = saveImageToInternalStorage(f);
                if (localPath != null) {
                    uploadFiles.add(new File(localPath));
                } else {
                    uploadFiles.add(f);
                }
            }

            syncReportWithNearbyGroup(report, uploadFiles, nearbyGroupId, callback);
        });
    }

    private boolean isUsableImage(File imageFile) {
        return imageFile != null
                && imageFile.isFile()
                && imageFile.canRead()
                && imageFile.length() > 0L;
    }

    private void notifyError(ApiCallback<Report> callback, String message) {
        if (callback != null) {
            callback.onError(message);
        }
    }

    private boolean isCurrentSession(String residentId) {
        return residentId != null
                && residentId.equals(sessionManager.fetchResidentId())
                && sessionManager.isLoggedIn();
    }

    private String getServerTargetId(Report report) {
        if (report == null || !report.isSynced()) return null;
        String storedId = report.getStoredMongoId();
        if (storedId != null && !storedId.trim().isEmpty()) return storedId;
        String reportId = report.getReportId();
        return reportId != null && !reportId.trim().isEmpty() ? reportId : null;
    }

    /**
     * Persists a resident edit to the real backend.
     *
     * <p>A report that already has a server identity is always written through
     * PATCH /reports/{id}, so the backend preserves the original reportId,
     * residentId and createdAt and only the editable fields plus updatedAt
     * change. Local state is written exclusively after the server confirms the
     * change, and the follow-up refresh pulls in the "UPDATED" history entry
     * that the backend recorded for this edit.
     */
    public void updateReport(Report report, File imageFile, ApiCallback<Report> callback) {
        executorService.execute(() -> {
            if (report == null) {
                notifyError(callback, "Report is missing.");
                return;
            }

            final String residentId = report.getResidentId() != null
                    ? report.getResidentId()
                    : sessionManager.fetchResidentId();
            if (residentId == null || residentId.trim().isEmpty()) {
                notifyError(callback, "Your session has expired. Please log in again.");
                return;
            }

            // A newly picked photo is copied into internal storage first so the
            // upload survives the temporary camera/gallery file being reclaimed.
            String localPhotoPath = report.getLocalPhotoPath();
            File uploadFile = null;
            if (isUsableImage(imageFile)) {
                String storedPath = saveImageToInternalStorage(imageFile);
                if (storedPath == null) {
                    notifyError(callback, "Unable to read the selected photo. Please try again.");
                    return;
                }
                localPhotoPath = storedPath;
                uploadFile = new File(storedPath);
            }

            String targetId = getServerTargetId(report);
            if (targetId != null && !targetId.trim().isEmpty()) {
                syncUpdateToServer(targetId, report, residentId, uploadFile, localPhotoPath, callback);
                return;
            }

            // This report has no server identity yet, so it has to be created
            // rather than updated. Do not silently fall through to a create for a
            // report the resident believes is already filed.
            if (uploadFile == null
                    && (report.getPhotoUrl() == null || report.getPhotoUrl().trim().isEmpty())) {
                notifyError(callback, "Please select an image before submitting.");
                return;
            }
            report.setResidentId(residentId);
            report.setLocalPhotoPath(localPhotoPath);
            syncReportToServer(report, uploadFile, callback);
        });
    }

    private void syncUpdateToServer(String targetId, Report original, String residentId,
                                    File imageFile, String localPhotoPath,
                                    ApiCallback<Report> callback) {
        MultipartBody.Part body = null;
        if (isUsableImage(imageFile)) {
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), imageFile);
            body = MultipartBody.Part.createFormData("image", imageFile.getName(), requestFile);
        }

        Map<String, RequestBody> fields = new HashMap<>();
        fields.put("title", textField(original.getTitle()));
        fields.put("category", textField(original.getWasteType()));
        fields.put("description", textField(original.getDescription()));

        // (0, 0) is the client's "no location" marker. Omitting the pair keeps
        // the stored position untouched, so a title-only edit is never recorded
        // as a location change.
        if (original.getLatitude() != 0d || original.getLongitude() != 0d) {
            fields.put("latitude", textField(String.valueOf(original.getLatitude())));
            fields.put("longitude", textField(String.valueOf(original.getLongitude())));
        }

        apiService.updateReport(targetId, body, fields).enqueue(new Callback<Report>() {
            @Override
            public void onResponse(Call<Report> call, Response<Report> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    notifyError(callback, updateErrorMessage(response));
                    return;
                }
                if (!isCurrentSession(residentId)) {
                    Log.w("ReportRepository", "Discarding update response after session changed");
                    notifyError(callback, "Your session changed. Please log in again.");
                    return;
                }

                // The server is the source of truth for every editable field.
                Report serverReport = response.body();
                executorService.execute(() -> {
                    // Restore the local-only identity that the response cannot
                    // carry: the Room key, the server id and the local photo.
                    serverReport.setId(original.getId());
                    serverReport.setMongoId(original.getStoredMongoId());
                    serverReport.setLocalPhotoPath(localPhotoPath);
                    // An edit response that carries no photo list would otherwise
                    // wipe the photos the resident originally submitted.
                    if (serverReport.getPhotoUrls().isEmpty()) {
                        serverReport.setPhotoUrls(original.getPhotoUrls());
                    }
                    serverReport.setResidentId(residentId);
                    serverReport.setSynced(true);
                    try {
                        reportDao.insertReport(serverReport);
                    } catch (RuntimeException cacheError) {
                        // The edit already landed upstream. Keep reporting
                        // success, but leave the cache failure diagnosable.
                        Log.e("ReportRepository", "Report " + targetId
                                + " was updated on the server but the local cache write failed", cacheError);
                    }
                    // Pull the server-recorded "UPDATED" history entry (and any
                    // change made elsewhere meanwhile) into Room.
                    fetchMyReports(null);
                    if (callback != null) callback.onSuccess(serverReport);
                });
            }

            @Override
            public void onFailure(Call<Report> call, Throwable t) {
                Log.e("ReportRepository", "Update of report " + targetId + " failed: " + t.getMessage(), t);
                notifyError(callback, "Update failed: " + t.getMessage());
            }
        });
    }

    private RequestBody textField(String value) {
        return RequestBody.create(MediaType.parse("text/plain"), value != null ? value : "");
    }

    private String updateErrorMessage(Response<Report> response) {
        if (response.isSuccessful()) {
            return "The server accepted the update but did not return the report.";
        }

        String raw = null;
        if (response.errorBody() != null) {
            try {
                raw = response.errorBody().string();
            } catch (IOException ignored) {
                // Fall through to the status-only message.
            }
        }
        if (raw != null && !raw.trim().isEmpty()) {
            return "Update failed (" + response.code() + "): " + raw.trim();
        }
        return "Update failed (" + response.code() + ")";
    }

    private String saveImageToInternalStorage(File sourceFile) {
        File directory = application.getDir("report_images", Application.MODE_PRIVATE);
        String fileName = "IMG_" + UUID.randomUUID().toString() + ".jpg";
        File destinationFile = new File(directory, fileName);

        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(destinationFile);
             FileChannel source = fis.getChannel();
             FileChannel destination = fos.getChannel()) {
            long copied = destination.transferFrom(source, 0, source.size());
            if (copied != source.size() || destinationFile.length() != source.size()) {
                throw new IOException("Image copy was incomplete");
            }
            return destinationFile.getAbsolutePath();
        } catch (IOException e) {
            if (destinationFile.exists()) {
                // Do not leave a truncated file that could be mistaken for a
                // valid upload on a later retry.
                destinationFile.delete();
            }
            Log.e("ReportRepository", "Error saving image locally", e);
            return null;
        }
    }

    public void deleteReport(Report report, ApiCallback<String> callback) {
        executorService.execute(() -> {
            // Log to history before deletion if possible, or at least record it
            History history = new History();
            history.setResidentId(sessionManager.fetchResidentId());
            history.setReportId(report.getReportId());
            history.setAction("PERMANENTLY_DELETED");
            history.setOldStatus(report.getStatus());
            history.setNewStatus("Permanently Deleted");
            history.setDescription("Report permanently deleted: " + report.getTitle());
            history.setTimestampValue(System.currentTimeMillis());
            historyDao.insertHistory(history);

            String targetId = getServerTargetId(report);
            if (targetId != null) {
                apiService.deleteReport(targetId).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            executorService.execute(() -> reportDao.deleteByLocalId(report.getId()));
                            if (callback != null) callback.onSuccess("Deleted successfully");
                        } else {
                            if (callback != null) callback.onError("Delete failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (callback != null) callback.onError(t.getMessage());
                    }
                });
            } else {
                reportDao.deleteByLocalId(report.getId());
                if (callback != null) callback.onSuccess("Deleted locally");
            }
        });
    }

    private void syncReportToServer(Report report, File imageFile, ApiCallback<Report> callback) {
        final String localReportId = report.getReportId();
        final String residentId = report.getResidentId() != null
                ? report.getResidentId()
                : sessionManager.fetchResidentId();

        if (residentId == null || residentId.trim().isEmpty()) {
            notifyError(callback, "Your session has expired. Please log in again.");
            return;
        }

        MultipartBody.Part body = null;
        if (isUsableImage(imageFile)) {
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), imageFile);
            body = MultipartBody.Part.createFormData("image", imageFile.getName(), requestFile);
        }

        Map<String, RequestBody> fields = new HashMap<>();
        fields.put("title", RequestBody.create(MediaType.parse("text/plain"), report.getTitle() != null ? report.getTitle() : ""));
        fields.put("category", RequestBody.create(MediaType.parse("text/plain"), report.getWasteType() != null ? report.getWasteType() : ""));
        fields.put("waste_type", RequestBody.create(MediaType.parse("text/plain"), report.getWasteType() != null ? report.getWasteType() : ""));
        fields.put("description", RequestBody.create(MediaType.parse("text/plain"), report.getDescription() != null ? report.getDescription() : ""));
        fields.put("latitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLatitude())));
        fields.put("longitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLongitude())));
        fields.put("resident_id", RequestBody.create(MediaType.parse("text/plain"), residentId));

        if (localReportId != null) {
            fields.put("report_id", RequestBody.create(MediaType.parse("text/plain"), localReportId));
        }

        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isEmpty()) {
            fields.put("image_url", RequestBody.create(MediaType.parse("text/plain"), report.getPhotoUrl()));
        }

        apiService.createReport(body, fields).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> bodyMap = response.body();
                if (!response.isSuccessful() || bodyMap == null || !isSuccessfulResponse(bodyMap)) {
                    String errorMsg = responseErrorMessage(response, bodyMap);
                    Log.e("ReportRepository", "createReport failed for resident "
                            + residentId + ": " + errorMsg);
                    notifyError(callback, errorMsg);
                    return;
                }
                if (!isCurrentSession(residentId)) {
                    Log.w("ReportRepository", "Discarding create response after session changed for resident "
                            + residentId);
                    notifyError(callback, "Your session changed. Please log in again.");
                    return;
                }

                Map<String, Object> reportData = extractReportData(bodyMap);
                String serverReportId = stringValue(reportData, "report_id", "reportId");
                if (serverReportId == null || serverReportId.trim().isEmpty()) {
                    String errorMsg = "The server accepted the request without returning a report ID.";
                    Log.e("ReportRepository", "createReport returned no report ID for resident " + residentId);
                    notifyError(callback, errorMsg);
                    return;
                }

                String mongoId = stringValue(reportData, "id", "_id", "mongoId");
                String photoUrl = stringValue(reportData, "image_url", "imageUrl", "photoUrl");
                String createdAt = stringValue(reportData, "created_at", "createdAt");
                String updatedAt = stringValue(reportData, "updated_at", "updatedAt");

                if (photoUrl != null && !photoUrl.trim().isEmpty()) {
                    report.setPhotoUrl(photoUrl);
                }
                if (createdAt != null && !createdAt.trim().isEmpty()) {
                    report.setCreatedAt(createdAt);
                }
                if (updatedAt != null && !updatedAt.trim().isEmpty()) {
                    report.setUpdatedAt(updatedAt);
                }

                report.setReportId(serverReportId);
                if (mongoId != null && !mongoId.trim().isEmpty()) {
                    report.setMongoId(mongoId);
                }
                report.setResidentId(residentId);
                report.setSynced(true);

                final String finalServerReportId = serverReportId;
                executorService.execute(() -> {
                    try {
                        // The report is inserted only after Render has
                        // returned a confirmed server report ID.
                        long localId = reportDao.insertReport(report);
                        report.setId((int) localId);
                        if (localReportId != null && !localReportId.equals(finalServerReportId)) {
                            historyDao.updateReportId(localReportId, finalServerReportId, residentId);
                        }
                        if (historyDao.countByReportId(finalServerReportId, residentId) == 0) {
                            History history = new History();
                            history.setResidentId(residentId);
                            history.setReportId(finalServerReportId);
                            history.setAction("SUBMITTED");
                            history.setOldStatus(null);
                            history.setNewStatus("Pending");
                            history.setDescription("New report submitted: " + report.getTitle());
                            history.setTimestampValue(System.currentTimeMillis());
                            historyDao.insertHistory(history);
                        }
                    } catch (RuntimeException cacheError) {
                        // The server has already accepted the report. Keep the
                        // success result, but make the local-cache failure
                        // visible for diagnostics and later reconciliation.
                        Log.e("ReportRepository", "Report " + finalServerReportId
                                + " was accepted by the server but local reconciliation failed", cacheError);
                    }
                    if (callback != null) callback.onSuccess(report);
                });
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e("ReportRepository", "createReport network failure for resident "
                        + residentId + ": " + t.getMessage(), t);
                notifyError(callback, "Network error: " + t.getMessage());
            }
        });
    }

    /**
 * Syncs a report with multiple photos to the server. Handles the nearby-report
 * hand-off, plus the created and duplicate-submission responses.
 */
    private void syncReportToServerMultiple(Report report, List<File> imageFiles,
                                           ApiCallback<Report> callback) {
        final String localReportId = report.getReportId();
        final String residentId = report.getResidentId() != null
                ? report.getResidentId()
                : sessionManager.fetchResidentId();

        if (residentId == null || residentId.trim().isEmpty()) {
            notifyError(callback, "Your session has expired. Please log in again.");
            return;
        }

        // Build multipart photo parts
        List<MultipartBody.Part> photoParts = new ArrayList<>();
        for (int i = 0; i < imageFiles.size(); i++) {
            File f = imageFiles.get(i);
            if (isUsableImage(f)) {
                RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), f);
                photoParts.add(MultipartBody.Part.createFormData("photos", f.getName(), requestFile));
            }
        }

        if (photoParts.isEmpty()) {
            notifyError(callback, "No valid photos to upload.");
            return;
        }

        Map<String, RequestBody> fields = buildBaseFields(report, residentId, localReportId);

        apiService.createReportMultiple(photoParts, fields).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> bodyMap = response.body();
                int code = response.code();

                // Handle duplicate_submission (429)
                if (code == 429) {
                    String msg = "Your previous submission is still being processed.";
                    if (bodyMap != null) {
                        String serverMsg = stringValue(bodyMap, "message", "error");
                        if (serverMsg != null && !serverMsg.trim().isEmpty()) {
                            msg = serverMsg.trim();
                        }
                    }
                    Log.w("ReportRepository", "Duplicate submission detected for resident " + residentId);
                    notifyError(callback, msg);
                    return;
                }

                // A report already exists near this spot. Not an error, but the resident
                // has to decide, so it is handed back as a typed payload.
                if (bodyMap != null && "existing_cluster".equals(stringValue(bodyMap, "status"))) {
                    Log.i("ReportRepository", "Nearby existing report detected");
                    notifyError(callback, NearbyReportPayload.encode(bodyMap));
                    return;
                }

                if (!response.isSuccessful() || bodyMap == null) {
                    String errorMsg = responseErrorMessage(response, bodyMap);
                    Log.e("ReportRepository", "createReportMultiple failed for resident "
                            + residentId + ": " + errorMsg);
                    notifyError(callback, errorMsg);
                    return;
                }

                if (!isCurrentSession(residentId)) {
                    Log.w("ReportRepository", "Discarding create response after session changed");
                    notifyError(callback, "Your session changed. Please log in again.");
                    return;
                }

                // Handle the created-report responses
                handleSuccessfulCreateResponse(report, bodyMap, residentId, localReportId, callback);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e("ReportRepository", "createReportMultiple network failure for resident "
                        + residentId + ": " + t.getMessage(), t);
                notifyError(callback, "Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Resends a report submission with the continue flag and the nearby group id.
     * Called when the resident chooses "Send My Report Anyway".
     */
    private void syncReportWithNearbyGroup(Report report, List<File> imageFiles,
                                           String nearbyGroupId, ApiCallback<Report> callback) {
        final String localReportId = report.getReportId();
        final String residentId = report.getResidentId() != null
                ? report.getResidentId()
                : sessionManager.fetchResidentId();

        if (residentId == null || residentId.trim().isEmpty()) {
            notifyError(callback, "Your session has expired. Please log in again.");
            return;
        }

        List<MultipartBody.Part> photoParts = new ArrayList<>();
        for (int i = 0; i < imageFiles.size(); i++) {
            File f = imageFiles.get(i);
            if (isUsableImage(f)) {
                RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), f);
                photoParts.add(MultipartBody.Part.createFormData("photos", f.getName(), requestFile));
            }
        }

        if (photoParts.isEmpty()) {
            notifyError(callback, "No valid photos to upload.");
            return;
        }

        Map<String, RequestBody> fields = buildBaseFields(report, residentId, localReportId);
        // Wire field names are the backend's, so they keep the server's vocabulary.
        fields.put("continue_in_cluster", RequestBody.create(MediaType.parse("text/plain"), "true"));
        fields.put("clusterId", RequestBody.create(MediaType.parse("text/plain"), nearbyGroupId));

        apiService.createReportMultiple(photoParts, fields).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> bodyMap = response.body();
                int code = response.code();

                if (code == 429) {
                    notifyError(callback, "Your previous submission is still being processed.");
                    return;
                }

                // The server can still answer with the nearby hand-off, for
                // instance when the resident tapped Send My Report Anyway and
                // the spot changed in between. Treating that 200 as success
                // would leave the report unsaved, because the response carries
                // no report to read.
                if (bodyMap != null && "existing_cluster".equals(stringValue(bodyMap, "status"))) {
                    Log.i("ReportRepository", "Nearby existing report detected on resend");
                    notifyError(callback, NearbyReportPayload.encode(bodyMap));
                    return;
                }

                if (!response.isSuccessful() || bodyMap == null) {
                    String errorMsg = responseErrorMessage(response, bodyMap);
                    Log.e("ReportRepository", "submitReportWithNearbyGroup failed: " + errorMsg);
                    notifyError(callback, errorMsg);
                    return;
                }

                if (!isCurrentSession(residentId)) {
                    notifyError(callback, "Your session changed. Please log in again.");
                    return;
                }

                handleSuccessfulCreateResponse(report, bodyMap, residentId, localReportId, callback);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e("ReportRepository", "submitReportWithNearbyGroup network failure: " + t.getMessage(), t);
                notifyError(callback, "Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Builds the standard field map for report creation.
     */
    private Map<String, RequestBody> buildBaseFields(Report report, String residentId, String localReportId) {
        Map<String, RequestBody> fields = new HashMap<>();
        fields.put("title", RequestBody.create(MediaType.parse("text/plain"), report.getTitle() != null ? report.getTitle() : ""));
        fields.put("category", RequestBody.create(MediaType.parse("text/plain"), report.getWasteType() != null ? report.getWasteType() : ""));
        fields.put("waste_type", RequestBody.create(MediaType.parse("text/plain"), report.getWasteType() != null ? report.getWasteType() : ""));
        fields.put("description", RequestBody.create(MediaType.parse("text/plain"), report.getDescription() != null ? report.getDescription() : ""));
        fields.put("latitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLatitude())));
        fields.put("longitude", RequestBody.create(MediaType.parse("text/plain"), String.valueOf(report.getLongitude())));
        fields.put("resident_id", RequestBody.create(MediaType.parse("text/plain"), residentId));

        if (localReportId != null) {
            fields.put("report_id", RequestBody.create(MediaType.parse("text/plain"), localReportId));
        }

        if (report.getPhotoUrl() != null && !report.getPhotoUrl().isEmpty()) {
            fields.put("image_url", RequestBody.create(MediaType.parse("text/plain"), report.getPhotoUrl()));
        }

        return fields;
    }

    /**
     * Processes a successful (201) create response, extracting the server
     * report data and persisting it locally.
     */
    private void handleSuccessfulCreateResponse(Report report, Map<String, Object> bodyMap,
                                                String residentId, String localReportId,
                                                ApiCallback<Report> callback) {
        Map<String, Object> reportData = extractReportData(bodyMap);
        String serverReportId = stringValue(reportData, "report_id", "reportId");
        if (serverReportId == null || serverReportId.trim().isEmpty()) {
            String errorMsg = "The server accepted the request without returning a report ID.";
            Log.e("ReportRepository", "createReport returned no report ID for resident " + residentId);
            notifyError(callback, errorMsg);
            return;
        }

        String mongoId = stringValue(reportData, "id", "_id", "mongoId");
        String photoUrl = stringValue(reportData, "image_url", "imageUrl", "photoUrl");
        String createdAt = stringValue(reportData, "created_at", "createdAt");
        String updatedAt = stringValue(reportData, "updated_at", "updatedAt");
        // Server-side grouping detail, kept on the cached report so a later edit or
// resend still targets the right nearby group. Never rendered.
        String nearbyGroupId = stringValue(bodyMap, "clusterId", "cluster_id");

        if (photoUrl != null && !photoUrl.trim().isEmpty()) {
            report.setPhotoUrl(photoUrl);
        }

        // Keep the full photo set from the create response so the report detail
        // can render every uploaded image without waiting for the next refresh.
        List<String> uploadedPhotos = stringListValue(bodyMap, "photos", "photoUrls", "imageUrls");
        if (!uploadedPhotos.isEmpty()) {
            report.setPhotoUrls(uploadedPhotos);
        }
        if (createdAt != null && !createdAt.trim().isEmpty()) {
            report.setCreatedAt(createdAt);
        }
        if (updatedAt != null && !updatedAt.trim().isEmpty()) {
            report.setUpdatedAt(updatedAt);
        }
        if (nearbyGroupId != null && !nearbyGroupId.trim().isEmpty()) {
            report.setClusterId(nearbyGroupId);
        }

        report.setReportId(serverReportId);
        if (mongoId != null && !mongoId.trim().isEmpty()) {
            report.setMongoId(mongoId);
        }
        report.setResidentId(residentId);
        report.setSynced(true);

        final String finalServerReportId = serverReportId;
        executorService.execute(() -> {
            try {
                long localId = reportDao.insertReport(report);
                report.setId((int) localId);
                if (localReportId != null && !localReportId.equals(finalServerReportId)) {
                    historyDao.updateReportId(localReportId, finalServerReportId, residentId);
                }
                if (historyDao.countByReportId(finalServerReportId, residentId) == 0) {
                    History history = new History();
                    history.setResidentId(residentId);
                    history.setReportId(finalServerReportId);
                    history.setAction("SUBMITTED");
                    history.setOldStatus(null);
                    history.setNewStatus("Pending");
                    history.setDescription("New report submitted: " + report.getTitle());
                    history.setTimestampValue(System.currentTimeMillis());
                    historyDao.insertHistory(history);
                }
            } catch (RuntimeException cacheError) {
                Log.e("ReportRepository", "Report " + finalServerReportId
                        + " was accepted by the server but local reconciliation failed", cacheError);
            }
            if (callback != null) callback.onSuccess(report);
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractReportData(Map<String, Object> bodyMap) {
        Object report = bodyMap.get("report");
        if (report instanceof Map) {
            return (Map<String, Object>) report;
        }

        Object data = bodyMap.get("data");
        if (data instanceof Map) {
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object nestedReport = dataMap.get("report");
            if (nestedReport instanceof Map) {
                return (Map<String, Object>) nestedReport;
            }
            return dataMap;
        }
        return bodyMap;
    }

    private boolean isSuccessfulResponse(Map<String, Object> bodyMap) {
        Object success = bodyMap.get("success");
        return success == null || Boolean.parseBoolean(String.valueOf(success));
    }

    private String stringValue(Map<String, Object> values, String... keys) {
        if (values == null) return null;
        for (String key : keys) {
            Object value = values.get(key);
            if (value == null) continue;
            if (value instanceof Number) {
                double number = ((Number) value).doubleValue();
                if (number == Math.rint(number) && !Double.isInfinite(number)) {
                    return String.valueOf(((Number) value).longValue());
                }
            }
            return String.valueOf(value);
        }
        return null;
    }

    /**
     * Reads a list of strings from the first matching key, tolerating the shapes
     * Gson can hand back for a JSON array.
     */
    @SuppressWarnings("unchecked")
    private List<String> stringListValue(Map<String, Object> values, String... keys) {
        List<String> result = new ArrayList<>();
        if (values == null) return result;
        for (String key : keys) {
            Object value = values.get(key);
            if (!(value instanceof List)) continue;
            for (Object entry : (List<Object>) value) {
                if (entry == null) continue;
                String text = String.valueOf(entry).trim();
                if (!text.isEmpty()) result.add(text);
            }
            if (!result.isEmpty()) return result;
        }
        return result;
    }

    private String responseErrorMessage(Response<?> response, Map<String, Object> bodyMap) {
        if (bodyMap != null) {
            String message = stringValue(bodyMap, "message", "error");
            if (message != null && !message.trim().isEmpty()) {
                return "Report creation failed (" + response.code() + "): " + message.trim();
            }
        }

        String raw = null;
        if (response.errorBody() != null) {
            try {
                raw = response.errorBody().string();
            } catch (IOException ignored) {
                // Fall through to the status-only message.
            }
        }
        if (raw != null && !raw.trim().isEmpty()) {
            return "Report creation failed (" + response.code() + "): " + raw.trim();
        }
        return "Report creation failed (" + response.code() + ")";
    }

    public void fetchMyReports() {
        fetchMyReports(null);
    }

    /**
     * @param onComplete optional callback invoked on the main thread once the
     *                   refresh has fully settled (including the history
     *                   reconcile). Lets the caller stop a progress indicator
     *                   on real completion instead of guessing a duration.
     */
    public void fetchMyReports(final Runnable onComplete) {
        final String requestedResidentId = sessionManager.fetchResidentId();
        final String requestedToken = sessionManager.fetchAuthToken();
        if (!sessionManager.isLoggedIn() || requestedResidentId == null
                || requestedResidentId.trim().isEmpty() || requestedToken == null
                || requestedToken.trim().isEmpty()) {
            Log.w("ReportRepository", "Skipping report refresh because there is no active session");
            notifyComplete(onComplete);
            return;
        }

        apiService.getMyReports().enqueue(new Callback<List<Report>>() {
            @Override
            public void onResponse(Call<List<Report>> call, Response<List<Report>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executorService.execute(() -> {
                        if (!isCurrentSession(requestedResidentId)
                                || !requestedToken.equals(sessionManager.fetchAuthToken())) {
                            Log.w("ReportRepository", "Discarding report refresh after session changed");
                            notifyComplete(onComplete);
                            return;
                        }
                        List<Report> serverReports = response.body();
                        Log.d("ReportRepository", "Fetched " + serverReports.size() + " reports from server");
                        for (Report serverReport : serverReports) {
                            if (serverReport.getResidentId() != null
                                    && !requestedResidentId.equals(serverReport.getResidentId())) {
                                Log.w("ReportRepository", "Ignoring report for a different resident");
                                continue;
                            }
                            serverReport.setResidentId(requestedResidentId);
                            Log.d("ReportRepository", "Report " + serverReport.getReportId() + ": isArchived=" + serverReport.isArchived() + ", isDeleted=" + serverReport.isDeleted());

                            // Check if we already have this report locally to preserve localPhotoPath
                            String serverMongoId = serverReport.getStoredMongoId();
                            Report localReport = serverMongoId == null
                                    ? null
                                    : reportDao.getReportByMongoId(serverMongoId);
                            if (localReport == null) {
                                localReport = reportDao.getReportByServerId(serverReport.getReportId());
                            }

                            if (localReport != null) {
                                serverReport.setId(localReport.getId());
                                if (serverReport.getLocalPhotoPath() == null) {
                                    serverReport.setLocalPhotoPath(localReport.getLocalPhotoPath());
                                }
                                // Keep the locally stored photo set when a server
                                // response omits it, so a report synced from an
                                // older backend build still renders every photo.
                                if (serverReport.getPhotoUrls().isEmpty()
                                        && !localReport.getPhotoUrls().isEmpty()) {
                                    serverReport.setPhotoUrls(localReport.getPhotoUrls());
                                }

                                // Record status change to history
                                if (serverReport.getStatus() != null && !serverReport.getStatus().equals(localReport.getStatus())) {
                                    History history = new History();
                                    history.setResidentId(requestedResidentId);
                                    history.setReportId(serverReport.getReportId());
                                    history.setAction("STATUS_CHANGED");
                                    history.setOldStatus(localReport.getStatus());
                                    history.setNewStatus(serverReport.getStatus());
                                    history.setDescription("Report status updated to " + serverReport.getStatus() + ": " + serverReport.getTitle());
                                    history.setTimestampValue(System.currentTimeMillis());
                                    historyDao.insertHistory(history);
                                }
                            }
                            serverReport.setSynced(true);
                            reportDao.insertReport(serverReport);
                        }

                        // Also fetch history logs
                        fetchHistory(requestedResidentId, requestedToken, onComplete);
                    });
                } else {
                    Log.e("ReportRepository", "Failed to fetch reports. Server responded with code: " + response.code());
                    notifyComplete(onComplete);
                }
            }

            @Override
            public void onFailure(Call<List<Report>> call, Throwable t) {
                Log.e("ReportRepository", "Failed to fetch reports: " + t.getMessage(), t);
                notifyComplete(onComplete);
            }
        });
    }

    /**
     * Delivers a completion callback on the main thread.
     *
     * <p>Uses a {@link android.os.Handler} rather than {@code Context.getMainExecutor()}
     * because this module supports API 24 and that method only exists from 28.
     */
    private void notifyComplete(final Runnable onComplete) {
        if (onComplete == null) return;
        MAIN_HANDLER.post(onComplete);
    }

    private void fetchHistory(String requestedResidentId, String requestedToken) {
        fetchHistory(requestedResidentId, requestedToken, null);
    }

    private void fetchHistory(String requestedResidentId, String requestedToken,
                              final Runnable onComplete) {
        apiService.getMyHistory().enqueue(new Callback<List<History>>() {
            @Override
            public void onResponse(Call<List<History>> call, Response<List<History>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executorService.execute(() -> {
                        if (!isCurrentSession(requestedResidentId)
                                || !requestedToken.equals(sessionManager.fetchAuthToken())) {
                            Log.w("ReportRepository", "Discarding history refresh after session changed");
                            notifyComplete(onComplete);
                            return;
                        }

                        // Only clean unresolved rows belonging to this
                        // resident; never delete another account's history.
                        historyDao.deleteLocalDuplicates(requestedResidentId);
                        historyDao.updateNullResidentIds(requestedResidentId);

                        // Ids this resident permanently deleted. The server may
                        // still return them (the upstream delete is
                        // best-effort), so the local tombstone is authoritative
                        // and those rows are never re-imported.
                        Set<String> tombstoned = new HashSet<>(
                                historyTombstoneDao.getTombstonedMongoIds(requestedResidentId)
                        );

                        for (History h : response.body()) {
                            if (h.getMongoId() == null) continue;
                            h.setResidentId(requestedResidentId);

                            // Re-assert deletion instead of re-importing. This
                            // is what makes a permanent delete permanent across
                            // pull-to-refresh, fragment recreation, app restart
                            // and process death.
                            if (!HistorySyncPolicy.shouldImport(h.getMongoId(), tombstoned)) {
                                continue;
                            }

                            History localHistory = historyDao.getHistoryByMongoId(h.getMongoId());
                            if (localHistory == null) {
                                if (h.getTimestampValue() == null) {
                                    h.setTimestampValue(System.currentTimeMillis());
                                }
                                historyDao.insertHistory(h);
                            }
                        }

                        List<String> stillOnServer = HistorySyncPolicy
                                .collectTombstonedStillOnServer(response.body(), tombstoned);
                        if (!stillOnServer.isEmpty()) {
                            Log.d("ReportRepository", "Re-asserting permanent delete for "
                                    + stillOnServer.size() + " tombstoned history id(s)");
                            pushHistoryDelete(stillOnServer);
                        }
                        notifyComplete(onComplete);
                    });
                } else {
                    Log.e("ReportRepository", "Failed to fetch history. Server responded with code: " + response.code());
                    notifyComplete(onComplete);
                }
            }
            @Override public void onFailure(Call<List<History>> call, Throwable t) {
                Log.e("ReportRepository", "Failed to fetch history: " + t.getMessage(), t);
                notifyComplete(onComplete);
            }
        });
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }
}
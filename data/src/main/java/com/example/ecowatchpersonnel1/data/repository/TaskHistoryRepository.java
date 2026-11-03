package com.example.ecowatchpersonnel1.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.ecowatchpersonnel1.data.local.PersonnelDatabase;
import com.example.ecowatchpersonnel1.data.local.TaskHistoryDao;
import com.example.ecowatchpersonnel1.data.local.TaskHistoryEntity;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.utils.SessionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Task history: the server is the source of truth, this device keeps the copy.
 *
 * <p>The screen reads from Room through {@link #observeHistory(String)} and calls
 * {@link #refresh(String, RefreshCallback)} to pull. Splitting it this way is
 * what makes history usable in the field: the list is already on screen before
 * the request finishes, and it still renders with no connection at all.
 *
 * <p>Writes happen on a single background thread. Room refuses main-thread writes,
 * and one thread keeps the rows of a sync together, so a history list can never
 * be observed half-updated.
 */
public class TaskHistoryRepository {

    /** Told when a refresh finished, and how much it changed. */
    public interface RefreshCallback {
        void onRefreshed(int savedCount);
    }

    /**
     * Shared across every repository instance.
     *
     * <p>A repository is built per screen, and a task detail screen builds one to
     * save a single row. One executor per instance would leave a thread behind for
     * each of those, and a field phone does not have threads to spare.
     */
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private final TaskHistoryDao dao;
    private final TaskRepository taskRepository;
    private final SessionManager sessionManager;

    public TaskHistoryRepository(Context context) {
        Context appContext = context.getApplicationContext();
        this.dao = PersonnelDatabase.getInstance(appContext).taskHistoryDao();
        this.taskRepository = new TaskRepository(appContext);
        this.sessionManager = new SessionManager(appContext);
    }

    /** The signed-in cleaner's id, or null before the session has been written. */
    public String currentPersonnelId() {
        return sessionManager.fetchPersonnelId();
    }

    /**
     * The saved history for one cleaner.
     *
     * <p>Returns an empty list rather than null when nobody is signed in, so the
     * observer has something to render instead of a crash on a cold open.
     */
    public LiveData<List<TaskHistoryEntity>> observeHistory(@Nullable String personnelId) {
        if (personnelId == null || personnelId.trim().isEmpty()) {
            return dao.observeForPersonnel("");
        }
        return dao.observeForPersonnel(personnelId.trim());
    }

    public LiveData<Integer> observeTotal(@Nullable String personnelId) {
        return dao.observeTotal(personnelId == null ? "" : personnelId.trim());
    }

    public LiveData<List<TaskHistoryDao.TaskStatusCount>> observeStatusCounts(
            @Nullable String personnelId) {
        return dao.observeStatusCounts(personnelId == null ? "" : personnelId.trim());
    }

    /**
     * Pulls every assignment from the server and writes it to the database.
     *
     * <p>The callback runs on the main thread, so the caller can touch views
     * directly. It is invoked with the number of rows now stored, which is what
     * the screen uses for its heading count.
     */
    public void refresh(@Nullable String personnelId, @NonNull RefreshCallback callback) {
        final String owner = personnelId == null ? currentPersonnelId() : personnelId;

        taskRepository.fetchTasks(null, new ApiCallback<List<PersonnelTask>>() {
            @Override
            public void onSuccess(List<PersonnelTask> tasks) {
                final List<TaskHistoryEntity> rows = new ArrayList<>();
                if (tasks != null) {
                    for (PersonnelTask task : tasks) {
                        if (task == null || task.assignmentId == null
                                || task.assignmentId.trim().isEmpty()) {
                            continue;
                        }
                        rows.add(TaskHistoryEntity.from(task, owner));
                    }
                }

                IO_EXECUTOR.execute(() -> {
                    if (!rows.isEmpty()) {
                        dao.upsertAll(rows);

                        // Prune only after a sync that actually returned something.
                        // An empty page after a dropped connection is not evidence
                        // that the cleaner has no history.
                        if (owner != null && !owner.trim().isEmpty()) {
                            List<String> ids = new ArrayList<>(rows.size());
                            for (TaskHistoryEntity row : rows) {
                                ids.add(row.assignmentId);
                            }
                            dao.deleteMissing(owner.trim(), ids);
                        }
                    }

                    final int total = owner == null || owner.trim().isEmpty()
                            ? 0
                            : dao.countForPersonnel(owner.trim());

                    CallbackDispatcher.post(callback, total);
                });
            }

            @Override
            public void onError(String message) {
                // The database is left exactly as it was, so a failed pull shows the
                // last known history instead of an empty screen.
                CallbackDispatcher.post(callback, -1);
            }
        });
    }

    /**
     * Saves one task the app already holds, without a network round trip.
     *
     * <p>Used right after an action in the task detail screen, so accepting or
     * completing a task shows up in history immediately rather than on the next
     * pull.
     */
    public void save(@Nullable PersonnelTask task) {
        if (task == null || task.assignmentId == null || task.assignmentId.trim().isEmpty()) {
            return;
        }
        String owner = currentPersonnelId();
        TaskHistoryEntity row = TaskHistoryEntity.from(task, owner);
        IO_EXECUTOR.execute(() -> dao.upsertAll(Collections.singletonList(row)));
    }

    /**
     * Forgets one cleaner's saved history.
     *
     * <p>Called when that account signs out, so a shared device does not leave
     * one cleaner's assignments on it for the next one to see.
     */
    public void clear(@Nullable String personnelId) {
        if (personnelId == null || personnelId.trim().isEmpty()) {
            return;
        }
        IO_EXECUTOR.execute(() -> dao.clearForPersonnel(personnelId.trim()));
    }

    }

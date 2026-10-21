package com.example.ecowatchpersonnel1.data.repository;

import android.content.Context;

import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.remote.ApiCalls;
import com.example.ecowatchpersonnel1.data.remote.ApiService;
import com.example.ecowatchpersonnel1.data.remote.EvidenceResponse;
import com.example.ecowatchpersonnel1.data.remote.RetrofitClient;
import com.example.ecowatchpersonnel1.data.remote.TaskCountsResponse;
import com.example.ecowatchpersonnel1.data.remote.TaskListResponse;
import com.example.ecowatchpersonnel1.data.remote.TaskResponse;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * The cleaner's task workflow.
 *
 * <p>Every method delegates to the server, which owns the ASSIGNED -> ACCEPTED
 * -> IN_PROGRESS -> COMPLETED rules. The app never guesses whether an action is
 * allowed; it renders the flags the server returned and surfaces the server's
 * message when an action is refused.
 */
public class TaskRepository {

    private final ApiService api;

    public TaskRepository(Context context) {
        this.api = RetrofitClient.getApiService(context.getApplicationContext());
    }

    public void fetchCurrentTask(ApiCallback<PersonnelTask> callback) {
        ApiCalls.enqueue(api.getCurrentTask(), taskCallback(callback));
    }

    /**
     * The cleaner's task totals, counted by the server.
     *
     * <p>Used by the profile cards rather than a recount of the task list: the
     * list is one page, so counting it client-side silently drops completed
     * tasks for a cleaner with a long history.
     */
    public void fetchTaskCounts(ApiCallback<TaskCountsResponse.Counts> callback) {
        ApiCalls.enqueue(api.getTaskCounts(), new ApiCallback<TaskCountsResponse>() {
            @Override
            public void onSuccess(TaskCountsResponse result) {
                callback.onSuccess(result == null ? new TaskCountsResponse.Counts()
                        : result.getCounts());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void fetchTasks(String status, ApiCallback<List<PersonnelTask>> callback) {
        ApiCalls.enqueue(api.listTasks(status, 1, 100), new ApiCallback<TaskListResponse>() {
            @Override
            public void onSuccess(TaskListResponse result) {
                callback.onSuccess(result == null ? null : result.getTasks());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void fetchTask(String assignmentId, ApiCallback<PersonnelTask> callback) {
        ApiCalls.enqueue(api.getTask(assignmentId), taskCallback(callback));
    }

    public void acceptTask(String assignmentId, ApiCallback<PersonnelTask> callback) {
        ApiCalls.enqueue(api.acceptTask(assignmentId), taskCallback(callback));
    }

    public void startTask(String assignmentId, ApiCallback<PersonnelTask> callback) {
        ApiCalls.enqueue(api.startTask(assignmentId), taskCallback(callback));
    }

    public void completeTask(String assignmentId, String notes, ApiCallback<PersonnelTask> callback) {
        Map<String, String> body = new HashMap<>();
        if (notes != null && !notes.trim().isEmpty()) {
            body.put("notes", notes.trim());
        }
        ApiCalls.enqueue(api.completeTask(assignmentId, body), taskCallback(callback));
    }

    /**
     * Uploads one cleaning photo. The phase is a plain multipart field; the
     * server rejects a phase the task's current status does not allow.
     */
    public void uploadEvidence(String assignmentId, String phase, File file, String mimeType,
                               ApiCallback<PersonnelTask> callback) {
        RequestBody phasePart = RequestBody.create(phase, MediaType.parse("text/plain"));
        MediaType type = MediaType.parse(mimeType == null ? "image/jpeg" : mimeType);
        RequestBody filePart = RequestBody.create(file, type);
        MultipartBody.Part image = MultipartBody.Part.createFormData("image", file.getName(), filePart);

        ApiCalls.enqueue(api.uploadEvidence(assignmentId, phasePart, image),
                new ApiCallback<EvidenceResponse>() {
                    @Override
                    public void onSuccess(EvidenceResponse result) {
                        callback.onSuccess(result == null ? null : result.task);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    public void deleteEvidence(String assignmentId, String evidenceId,
                               ApiCallback<PersonnelTask> callback) {
        ApiCalls.enqueue(api.deleteEvidence(assignmentId, evidenceId), taskCallback(callback));
    }

    private ApiCallback<TaskResponse> taskCallback(ApiCallback<PersonnelTask> callback) {
        return new ApiCallback<TaskResponse>() {
            @Override
            public void onSuccess(TaskResponse result) {
                callback.onSuccess(result == null ? null : result.task);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }
}

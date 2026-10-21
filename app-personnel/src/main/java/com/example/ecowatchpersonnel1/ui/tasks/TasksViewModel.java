package com.example.ecowatchpersonnel1.ui.tasks;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.ecowatchpersonnel1.data.models.PersonnelTask;
import com.example.ecowatchpersonnel1.data.remote.ApiCallback;
import com.example.ecowatchpersonnel1.data.repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

/** Loads the cleaner's full task list once; the fragment filters it in memory. */
public class TasksViewModel extends AndroidViewModel {

    private final TaskRepository repository;
    private final MutableLiveData<List<PersonnelTask>> tasks = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public TasksViewModel(@NonNull Application application) {
        super(application);
        repository = new TaskRepository(application);
    }

    public LiveData<List<PersonnelTask>> getTasks() {
        return tasks;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadTasks() {
        loading.setValue(true);
        error.setValue(null);

        repository.fetchTasks(null, new ApiCallback<List<PersonnelTask>>() {
            @Override
            public void onSuccess(List<PersonnelTask> result) {
                tasks.setValue(result == null ? new ArrayList<>() : result);
                loading.setValue(false);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
                loading.setValue(false);
            }
        });
    }
}

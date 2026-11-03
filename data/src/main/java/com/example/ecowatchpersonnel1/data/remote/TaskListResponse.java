package com.example.ecowatchpersonnel1.data.remote;

import com.example.ecowatchpersonnel1.data.models.PersonnelTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for {@code GET /api/personnel/tasks}.
 *
 * <p>The list is paged, so it is not a reliable basis for the profile's task
 * totals; {@link TaskCountsResponse} is.
 */
public class TaskListResponse {

    public boolean success;
    public List<PersonnelTask> tasks = new ArrayList<>();
    public int count;

    public List<PersonnelTask> getTasks() {
        return tasks == null ? new ArrayList<>() : tasks;
    }
}

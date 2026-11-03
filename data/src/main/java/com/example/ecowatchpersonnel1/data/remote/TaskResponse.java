package com.example.ecowatchpersonnel1.data.remote;

import com.example.ecowatchpersonnel1.data.models.PersonnelTask;

/** Response for the single-task, accept, start, complete and delete-evidence routes. */
public class TaskResponse {

    public boolean success;
    public String message;
    public PersonnelTask task;

    public PersonnelTask getTask() {
        return task;
    }
}

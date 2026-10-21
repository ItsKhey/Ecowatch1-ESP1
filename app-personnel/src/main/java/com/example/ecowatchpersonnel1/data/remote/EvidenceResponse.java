package com.example.ecowatchpersonnel1.data.remote;

import com.example.ecowatchpersonnel1.data.models.PersonnelEvidence;
import com.example.ecowatchpersonnel1.data.models.PersonnelTask;

/** Response for {@code POST /api/personnel/tasks/{id}/evidence}. */
public class EvidenceResponse {

    public boolean success;
    public String message;
    public PersonnelEvidence evidence;
    public PersonnelTask task;

    public PersonnelTask getTask() {
        return task;
    }
}

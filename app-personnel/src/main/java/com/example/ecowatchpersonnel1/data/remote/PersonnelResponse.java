package com.example.ecowatchpersonnel1.data.remote;

import com.example.ecowatchpersonnel1.data.models.Personnel;

/** Response for {@code GET /api/personnel/me}. */
public class PersonnelResponse {

    public boolean success;
    public Personnel personnel;

    public Personnel getPersonnel() {
        return personnel;
    }
}

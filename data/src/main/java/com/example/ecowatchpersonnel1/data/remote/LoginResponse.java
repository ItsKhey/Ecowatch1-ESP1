package com.example.ecowatchpersonnel1.data.remote;

import com.example.ecowatchpersonnel1.data.models.Personnel;

/** Response for {@code POST /api/personnel/login}. */
public class LoginResponse {

    public boolean success;
    public String token;
    public Personnel personnel;

    public String getToken() {
        return token;
    }

    public Personnel getPersonnel() {
        return personnel;
    }
}

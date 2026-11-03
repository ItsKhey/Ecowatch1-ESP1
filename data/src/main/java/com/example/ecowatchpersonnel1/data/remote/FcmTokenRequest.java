package com.example.ecowatchpersonnel1.data.remote;

/** Body for {@code POST /api/personnel/fcm-token}. */
public class FcmTokenRequest {

    public String token;

    public FcmTokenRequest(String token) {
        this.token = token;
    }
}

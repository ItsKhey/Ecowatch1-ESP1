package com.example.myecowatch1.data.remote;

public class FcmTokenRequest {
    private final String token;

    public FcmTokenRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}

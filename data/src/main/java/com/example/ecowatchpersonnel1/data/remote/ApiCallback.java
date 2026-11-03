package com.example.ecowatchpersonnel1.data.remote;

/** Minimal success/failure callback used by the personnel repositories. */
public interface ApiCallback<T> {

    void onSuccess(T result);

    void onError(String message);
}

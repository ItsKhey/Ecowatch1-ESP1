package com.example.ecowatchpersonnel1.data.remote;

import androidx.annotation.NonNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * One place that turns a failed HTTP call into a message a cleaner can act on.
 *
 * <p>The backend always answers errors with {@code {success:false,message}},
 * so the message is preferred over a raw status code; a network failure gets a
 * plain-language replacement because "failed to connect to /10.0.2.2" is not
 * useful on a phone.
 */
public final class ApiCalls {

    private ApiCalls() {
    }

    public static <T> void enqueue(Call<T> call, ApiCallback<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> ignored, @NonNull Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(messageFor(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> ignored, @NonNull Throwable throwable) {
                callback.onError(messageFor(throwable));
            }
        });
    }

    private static String messageFor(Response<?> response) {
        if (response.errorBody() != null) {
            try {
                String raw = response.errorBody().string();
                if (raw != null && !raw.trim().isEmpty()) {
                    JsonObject object = new JsonParser().parse(raw).getAsJsonObject();
                    if (object.has("message") && !object.get("message").isJsonNull()) {
                        return object.get("message").getAsString();
                    }
                }
            } catch (Exception ignored) {
                // Fall through to the generic message.
            }
        }
        return "Request failed (" + response.code() + ").";
    }

    private static String messageFor(Throwable throwable) {
        if (throwable instanceof IOException) {
            return "Cannot reach the EcoWatch server. Check your connection.";
        }
        if (throwable != null && throwable.getMessage() != null
                && !throwable.getMessage().trim().isEmpty()) {
            return throwable.getMessage();
        }
        return "Something went wrong. Please try again.";
    }
}

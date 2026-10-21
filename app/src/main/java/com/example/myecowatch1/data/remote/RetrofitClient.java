package com.example.myecowatch1.data.remote;

import android.content.Context;

import com.example.myecowatch1.utils.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // Resolved per call rather than held in a constant, because the user can
    // point the app at a different server from the login screen. See ServerConfig.
    private static Retrofit retrofit = null;

    /**
     * Discards the cached Retrofit instance.
     *
     * Called when the server address changes, so the next request is built
     * against the new address instead of the one captured at first use.
     */
    public static synchronized void reset() {
        retrofit = null;
    }

    public static ApiService getApiService(Context context) {
        if (retrofit == null) {
            final Context appContext = context.getApplicationContext();
            SessionManager sessionManager = new SessionManager(appContext);

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            // BASIC keeps request diagnostics without writing JWTs, passwords,
            // multipart image bytes, or other sensitive body data to logcat.
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .addInterceptor(logging)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request.Builder requestBuilder = original.newBuilder()
                                .method(original.method(), original.body());
                        
                        if (requestBuilder.build().header("Authorization") == null) {
                            String token = sessionManager.fetchAuthToken();
                            if (token != null) {
                                requestBuilder.addHeader("Authorization", "Bearer " + token);
                            }
                        }

                        return chain.proceed(requestBuilder.build());
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(ServerConfig.apiBaseUrl(appContext))
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(okHttpClient)
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}
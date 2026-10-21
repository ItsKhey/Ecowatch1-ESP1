package com.example.myecowatch1.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.myecowatch1.data.local.AnnouncementDao;
import com.example.myecowatch1.data.local.AppDatabase;
import com.example.myecowatch1.data.models.Announcement;
import com.example.myecowatch1.data.remote.AnnouncementFeedResponse;
import com.example.myecowatch1.data.remote.ApiService;
import com.example.myecowatch1.data.remote.RetrofitClient;
import com.example.myecowatch1.utils.SessionManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Announcement feed for the resident app.
 *
 * <p>The server decides which announcements a resident may read; this class
 * only caches that answer. The cached rows back the dashboard card, so the
 * card renders offline and never shows an announcement the backend has since
 * removed.
 */
public class AnnouncementRepository {

    private static final String TAG = "AnnouncementRepository";

    private final AnnouncementDao announcementDao;
    private final ApiService apiService;
    private final SessionManager sessionManager;
    private final ExecutorService executorService;

    public AnnouncementRepository(@NonNull Application application) {
        this.announcementDao = AppDatabase.getInstance(application).announcementDao();
        this.apiService = RetrofitClient.getApiService(application);
        this.sessionManager = new SessionManager(application);
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Announcement>> getAnnouncements() {
        return announcementDao.getAllAnnouncements();
    }

    /**
     * Refreshes the feed from the backend.
     *
     * <p>The cache is replaced rather than merged so an announcement that is no
     * longer returned cannot linger on the dashboard. A failed refresh keeps
     * the previous rows: stale announcements are better than an empty card.
     */
    public void fetchAnnouncements(@Nullable Runnable onComplete) {
        final String residentId = sessionManager.fetchResidentId();
        final String token = sessionManager.fetchAuthToken();

        if (!sessionManager.isLoggedIn() || residentId == null || residentId.trim().isEmpty()
                || token == null || token.trim().isEmpty()) {
            Log.w(TAG, "Skipping announcement refresh because there is no active session");
            notifyComplete(onComplete);
            return;
        }

        apiService.getMyAnnouncements().enqueue(new Callback<AnnouncementFeedResponse>() {
            @Override
            public void onResponse(Call<AnnouncementFeedResponse> call,
                                   Response<AnnouncementFeedResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Announcement> announcements = response.body().getAnnouncements();
                    final List<Announcement> cacheable = new java.util.ArrayList<>();
                    for (Announcement announcement : announcements) {
                        if (announcement == null) continue;
                        if (announcement.getAnnouncementId() == null
                                || announcement.getAnnouncementId().trim().isEmpty()) {
                            // Without a server id the row could never be
                            // de-duplicated, so it is not cached.
                            continue;
                        }
                        cacheable.add(announcement);
                    }

                    executorService.execute(() -> {
                        if (!isCurrentSession(residentId, token)) {
                            Log.w(TAG, "Discarding announcement refresh after session changed");
                            notifyComplete(onComplete);
                            return;
                        }
                        try {
                            // Replace rather than merge so an announcement the
                            // server no longer returns cannot linger.
                            announcementDao.deleteAll();
                            if (!cacheable.isEmpty()) {
                                announcementDao.insertAnnouncements(cacheable);
                            }
                        } catch (RuntimeException cacheError) {
                            Log.e(TAG, "Announcements were fetched but the local cache "
                                    + "write failed", cacheError);
                        }
                        notifyComplete(onComplete);
                    });
                } else {
                    Log.e(TAG, "Failed to fetch announcements. Code: " + response.code());
                    notifyComplete(onComplete);
                }
            }

            @Override
            public void onFailure(Call<AnnouncementFeedResponse> call, Throwable t) {
                Log.e(TAG, "Failed to fetch announcements: " + t.getMessage(), t);
                notifyComplete(onComplete);
            }
        });
    }

    public void fetchAnnouncements() {
        fetchAnnouncements(null);
    }

    private boolean isCurrentSession(String residentId, String token) {
        return residentId != null
                && residentId.equals(sessionManager.fetchResidentId())
                && token != null
                && token.equals(sessionManager.fetchAuthToken())
                && sessionManager.isLoggedIn();
    }

    private void notifyComplete(@Nullable final Runnable onComplete) {
        if (onComplete == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
    }
}

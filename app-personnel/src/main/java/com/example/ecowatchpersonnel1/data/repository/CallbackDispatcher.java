package com.example.ecowatchpersonnel1.data.repository;

import android.os.Handler;
import android.os.Looper;

/**
 * Moves a repository callback back to the main thread.
 *
 * <p>The history write finishes on a background thread, but the fragment that
 * asked for it updates views. Handing it over here keeps every repository
 * callback on the same thread the fragments already expect, instead of each
 * caller remembering to post.
 */
final class CallbackDispatcher {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CallbackDispatcher() {
    }

    static void post(TaskHistoryRepository.RefreshCallback callback, int count) {
        MAIN.post(() -> callback.onRefreshed(count));
    }
}
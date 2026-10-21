package com.example.ecowatchpersonnel1.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Applies the cleaner's sound and vibration choices to the notification channel.
 *
 * <p>Those two are properties of the channel rather than of each notification, so
 * they cannot be checked per alert the way the categories are. Changing either one
 * means re-describing the channel.
 *
 * <p>Android only honours a change to a channel's sound when the channel is
 * recreated: {@link NotificationChannel#setSound} on an existing channel is
 * accepted and then ignored on most versions. So when the desired sound differs
 * from the one in place, the channel is deleted and rebuilt. That also discards a
 * sound the cleaner picked in system settings, which is the intended trade — the
 * in-app setting is meant to be the one that counts.
 */
public final class NotificationChannelTuning {

    private static final String TAG = "PersonnelNotifChannel";

    /** One short pulse, enough to be noticed in a pocket without being alarming. */
    private static final long[] VIBRATION_PATTERN = {0, 250, 250, 250};

    private NotificationChannelTuning() {
    }

    /** Must match {@code PERSONNEL_CHANNEL_ID} in the backend's fcmService. */
    private static final String CHANNEL_ID = "ecowatch_personnel_notifications";

    /** Recreates the channel to match the stored choices. Safe to call repeatedly. */
    public static void apply(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        applyFromO(context);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private static void applyFromO(@NonNull Context context) {
        NotificationManager manager = context.getApplicationContext()
                .getSystemService(NotificationManager.class);
        if (manager == null) return;

        String channelId = CHANNEL_ID;
        boolean soundEnabled = NotificationPreferences.isSoundEnabled(context);
        boolean vibrationEnabled = NotificationPreferences.isVibrationEnabled(context);

        NotificationChannel existing = manager.getNotificationChannel(channelId);
        Uri desiredSound = soundEnabled ? NotificationPreferences.soundUri(context) : null;

        // Compare what is in place against what is wanted. If the sound changed,
        // only a rebuild will take effect.
        boolean soundMatches = existing != null
                && equal(existing.getSound(), desiredSound);

        if (existing != null && soundMatches) {
            existing.enableVibration(vibrationEnabled);
            if (vibrationEnabled) {
                existing.setVibrationPattern(VIBRATION_PATTERN);
            }
            manager.createNotificationChannel(existing);
            return;
        }

        if (existing != null) {
            manager.deleteNotificationChannel(channelId);
        }

        NotificationChannel channel = new NotificationChannel(
                channelId,
                context.getString(com.example.ecowatchpersonnel1.R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(
                context.getString(com.example.ecowatchpersonnel1.R.string.notif_channel_description));

        if (desiredSound != null) {
            channel.setSound(desiredSound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } else {
            // No custom sound means the system's default notification sound, which
            // is what an empty URI expresses.
            channel.setSound(null, null);
        }

        channel.enableVibration(vibrationEnabled);
        if (vibrationEnabled) {
            channel.setVibrationPattern(VIBRATION_PATTERN);
        }

        manager.createNotificationChannel(channel);
        Log.d(TAG, "Notification channel rebuilt for the stored sound and vibration");
    }

    private static boolean equal(Uri left, Uri right) {
        if (left == null) return right == null;
        return left.equals(right);
    }
}
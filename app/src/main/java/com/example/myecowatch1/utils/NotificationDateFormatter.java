package com.example.myecowatch1.utils;

import androidx.annotation.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Shared formatter for notification-center timestamps. */
public final class NotificationDateFormatter {
    private static final TimeZone DISPLAY_TIME_ZONE = TimeZone.getTimeZone("Asia/Manila");

    private NotificationDateFormatter() {
    }

    @Nullable
    public static Date parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim().replace("Z", "+00:00");
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss.SSSXXX",
                "yyyy-MM-dd HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                parser.setTimeZone(pattern.endsWith("X")
                        ? TimeZone.getTimeZone("UTC")
                        : DISPLAY_TIME_ZONE);
                return parser.parse(value);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    public static String format(@Nullable String raw) {
        Date date = parse(raw);
        if (date == null) return raw == null || raw.trim().isEmpty() ? "—" : raw;
        SimpleDateFormat formatter = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US);
        formatter.setTimeZone(DISPLAY_TIME_ZONE);
        return formatter.format(date);
    }

    /**
     * Compact "Today · 10:42 AM" / "Mar 3 · 8:15 AM" label used by the
     * announcement feed. Falls back to the absolute format when the timestamp
     * cannot be parsed, and to a dash when there is nothing to show.
     */
    public static String formatRelative(@Nullable String raw) {
        Date date = parse(raw);
        if (date == null) return raw == null || raw.trim().isEmpty() ? "—" : format(raw);

        Calendar now = Calendar.getInstance(DISPLAY_TIME_ZONE);
        Calendar target = Calendar.getInstance(DISPLAY_TIME_ZONE);
        target.setTime(date);

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.US);
        timeFormat.setTimeZone(DISPLAY_TIME_ZONE);
        String time = timeFormat.format(date);

        boolean sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR);

        if (sameDay) return "Today · " + time;

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.US);
        dateFormat.setTimeZone(DISPLAY_TIME_ZONE);
        return dateFormat.format(date) + " · " + time;
    }
}

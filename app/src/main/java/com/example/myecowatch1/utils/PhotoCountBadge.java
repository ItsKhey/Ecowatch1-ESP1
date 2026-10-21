package com.example.myecowatch1.utils;

import android.view.View;
import android.widget.TextView;

import java.util.List;

/**
 * Formats the "how many photos did this report carry" badge shown on report rows.
 *
 * <p>Both the resident and the admin app render report lists, and the count has
 * to mean the same thing in both, so the rule lives here rather than being
 * re-derived per adapter.
 */
public final class PhotoCountBadge {

    /**
     * A report shows the badge only when it carries more than one photo. A
     * single-photo report already communicates its photo by showing one, and a
     * "1" badge on every row is noise.
     */
    public static final int MIN_PHOTOS_FOR_BADGE = 2;

    private PhotoCountBadge() {
    }

    /** The number to display, or 0 when the badge should stay hidden. */
    public static int displayCount(List<String> photoUrls) {
        return photoUrls == null ? 0 : photoUrls.size();
    }

    /** True when the badge should be visible for this photo list. */
    public static boolean shouldShow(List<String> photoUrls) {
        return displayCount(photoUrls) >= MIN_PHOTOS_FOR_BADGE;
    }

    /** "5", or an empty string when the count is too low to display. */
    public static String format(List<String> photoUrls) {
        int count = displayCount(photoUrls);
        return count >= MIN_PHOTOS_FOR_BADGE ? String.valueOf(count) : "";
    }

    /**
     * Shows or hides the badge and writes the count into it.
     *
     * <p>Callers must always invoke this for every bind so a recycled row from a
     * multi-photo report cannot keep its badge when it is reused for a
     * single-photo one.
     */
    public static void bind(View badge, TextView countText, List<String> photoUrls) {
        if (badge == null) return;

        if (shouldShow(photoUrls)) {
            badge.setVisibility(View.VISIBLE);
            if (countText != null) {
                countText.setText(format(photoUrls));
            }
        } else {
            badge.setVisibility(View.GONE);
        }
    }
}

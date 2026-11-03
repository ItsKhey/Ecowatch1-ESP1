package com.example.ecowatchpersonnel1.data.models;

import java.util.Locale;

/**
 * The waste-scale vocabulary the server speaks.
 *
 * <p>A report is filed under one of three sizes, and the size decides what the
 * administrator sends to the site:
 *
 *   Small-Scale Waste  -> one individual personnel member walks in and clears it.
 *   Medium-Scale Waste -> a cleaning team turns out, but no truck is needed.
 *   Large-Scale Waste  -> a cleaning team turns out with a collection vehicle.
 *
 * <p>A cleaner opening a task needs to know which of those their job is, because
 * that is what tells them whether a truck is coming and whether they are one
 * person or half of a crew.
 *
 * <p>The values here are the canonical display text the server stores and sends.
 * Anything unrecognised is folded onto the small scale, which is the only size a
 * report could have carried before the field existed.
 */
public final class WasteScale {

    public static final String SMALL = "Small-Scale Waste";
    public static final String MEDIUM = "Medium-Scale Waste";
    public static final String LARGE = "Large-Scale Waste";

    /** The folded key of each scale, e.g. "large-scale-waste". */
    private static final String MEDIUM_KEY = "medium-scale-waste";
    private static final String LARGE_KEY = "large-scale-waste";

    /** The bare adjective, e.g. "medium". */
    private static final String MEDIUM_ALIAS = "medium";
    private static final String LARGE_ALIAS = "large";

    private WasteScale() {
    }

    /**
     * The canonical scale for a raw server value.
     *
     * <p>The server always sends the canonical text, but the spellings are
     * folded anyway — the bare adjective and the snake_case field name are both
     * accepted — so a payload that arrives in an older spelling still lands on
     * the right scale instead of quietly reading as the smallest one.
     */
    public static String canonical(String raw) {
        if (raw == null) return SMALL;
        String value = raw.trim();
        if (value.isEmpty()) return SMALL;

        // The same folding the server does: lower case, with every run of
        // underscores or spaces collapsed into a single hyphen. "Large-Scale
        // Waste", "large-scale-waste" and "large_scale_waste" all land on the
        // same key.
        String key = value.toLowerCase(Locale.US).replaceAll("[_\\s]+", "-");

        if (MEDIUM_KEY.equals(key) || MEDIUM_ALIAS.equals(key)) {
            return MEDIUM;
        }
        if (LARGE_KEY.equals(key) || LARGE_ALIAS.equals(key)) {
            return LARGE;
        }
        return SMALL;
    }

    /** True when the scale needs a crew rather than a single cleaner. */
    public static boolean requiresTeam(String raw) {
        String scale = canonical(raw);
        return MEDIUM.equals(scale) || LARGE.equals(scale);
    }

    /** True when the scale needs a collection vehicle on site. */
    public static boolean requiresVehicle(String raw) {
        return LARGE.equals(canonical(raw));
    }
}

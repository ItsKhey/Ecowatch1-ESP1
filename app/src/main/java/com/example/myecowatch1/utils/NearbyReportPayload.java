package com.example.myecowatch1.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Wire format for handing a nearby existing report from the repository to the
 * ViewModel.
 *
 * <p>A submission that lands close to a report already on file is not a
 * failure, but the only callback the repository has for it is the error
 * channel, so the nearby-report data travels as a prefixed string the
 * ViewModel recognises and turns back into a map.
 *
 * <p>The payload must be encoded as real JSON. A {@link Map#toString()} yields
 * Java syntax (unquoted keys, '=' separators, unquoted string values), which no
 * JSON parser accepts, and decoding then fails and the resident gets an
 * unexplained error instead of the "a report is already near here" prompt.
 */
public final class NearbyReportPayload {

    public static final String PREFIX = "NEARBY_REPORT:";

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private NearbyReportPayload() {
    }

    /** Wraps a response map into the prefixed error string. */
    public static String encode(Map<String, Object> nearbyData) {
        return PREFIX + (nearbyData == null ? "{}" : GSON.toJson(nearbyData));
    }

    /** True when the error string carries a nearby-report hand-off. */
    public static boolean isNearbyReportPayload(String error) {
        return error != null && error.startsWith(PREFIX);
    }

    /**
     * Recovers the nearby-report map from an error string.
     *
     * @return the nearby data, or {@code null} when this is not a nearby-report
     *         payload or its JSON cannot be read.
     */
    public static Map<String, Object> decode(String error) {
        if (!isNearbyReportPayload(error)) return null;

        String json = error.substring(PREFIX.length()).trim();
        if (json.isEmpty()) return null;

        try {
            Map<String, Object> parsed = GSON.fromJson(json, MAP_TYPE);
            return parsed;
        } catch (RuntimeException e) {
            // Malformed or non-JSON payload. The caller reports a retryable
            // message rather than surfacing a raw parse failure.
            return null;
        }
    }

    /**
     * Whether the hand-off is worth showing the resident, i.e. whether the spot
     * really still has work behind it.
     *
     * <p>The server counts only the reports at that spot which are still
     * outstanding. A count of zero means every report there has already been
     * completed or dealt with, so there is no duplicate left to warn about, and
     * a sheet reading "A Report Is Already Near Here / Existing Reports 0" would
     * ask the resident to decide about nothing. The cluster row is not advanced
     * to a terminal state when the last report is completed, so a server still
     * matching on that stale row produces exactly this hand-off.
     *
     * <p>{@code reportCount} and {@code spotCount} are the same figure on the
     * server and either one counts. A missing or unreadable count is treated as
     * "something is there": silence is only justified by a positive statement
     * that nothing is.
     *
     * @return true when at least one outstanding report sits at the spot.
     */
    public static boolean hasOutstandingReports(Map<String, Object> nearbyData) {
        if (nearbyData == null) return true;

        Object reportCount = nearbyData.get("reportCount");
        if (reportCount == null) reportCount = nearbyData.get("spotCount");
        if (reportCount == null) return true;

        try {
            return Double.parseDouble(reportCount.toString()) > 0d;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
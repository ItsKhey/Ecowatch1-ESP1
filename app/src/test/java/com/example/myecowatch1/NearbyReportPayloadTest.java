package com.example.myecowatch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.myecowatch1.utils.NearbyReportPayload;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Guards the nearby-report hand-off between the repository and the ViewModel.
 *
 * <p>A submission that lands near an existing report is not a failure, but it
 * still has to travel to the ViewModel somehow, and it has to survive the trip
 * as readable data. If the encoding produced Java's {@code Map#toString()}
 * instead of JSON, decoding would fail and the resident would get an
 * unexplained error instead of being told a report already exists nearby.
 */
public class NearbyReportPayloadTest {

    @Test
    public void aRoundTripReturnsTheSameFields() {
        Map<String, Object> nearby = new HashMap<>();
        nearby.put("clusterId", "CL-123456");
        nearby.put("clusterStatus", "Pending");
        nearby.put("distance", 8.4);
        nearby.put("reportCount", 2);
        nearby.put("previewPhoto", "https://example.test/photo.png");

        Map<String, Object> decoded = NearbyReportPayload.decode(
                NearbyReportPayload.encode(nearby));

        assertEquals("CL-123456", decoded.get("clusterId"));
        assertEquals("Pending", decoded.get("clusterStatus"));
        assertEquals(8.4, ((Number) decoded.get("distance")).doubleValue(), 0.001);
        assertEquals(2, ((Number) decoded.get("reportCount")).intValue());
    }

    /** The payload must be real JSON, not Java map syntax. */
    @Test
    public void theEncodingIsJsonRatherThanJavaMapSyntax() {
        Map<String, Object> nearby = new LinkedHashMap<>();
        nearby.put("reportCount", 2);

        String encoded = NearbyReportPayload.encode(nearby);
        String json = encoded.substring(NearbyReportPayload.PREFIX.length());

        assertTrue("a JSON object, not {a=b}", json.startsWith("{"));
        assertTrue("keys must be quoted", json.contains("\"reportCount\""));
    }

    @Test
    public void anUnknownErrorIsNotTreatedAsAHandOff() {
        assertFalse(NearbyReportPayload.isNearbyReportPayload("Network error: timeout"));
        assertNull(NearbyReportPayload.decode("Network error: timeout"));
    }

    /** A malformed payload must return null so the caller can report a retry. */
    @Test
    public void malformedPayloadsDecodeToNull() {
        assertNull(NearbyReportPayload.decode(NearbyReportPayload.PREFIX));
        assertNull(NearbyReportPayload.decode(NearbyReportPayload.PREFIX + "{not json"));
    }

    /**
     * The payload arrives as generic JSON, so whole numbers come back as doubles
     * and would otherwise render as "2.0" in the bottom sheet.
     */
    @Test
    public void countsArriveAsWholeValuedDoubles() {
        Map<String, Object> decoded = NearbyReportPayload.decode(
                NearbyReportPayload.PREFIX + "{\"reportCount\":2}");

        Object count = decoded.get("reportCount");
        assertTrue(count instanceof Number);
        assertEquals(2, ((Number) count).doubleValue(), 0.0);
    }

    @Test
    public void aNullPayloadStillProducesAValidHandOff() {
        String encoded = NearbyReportPayload.encode(null);

        assertTrue(NearbyReportPayload.isNearbyReportPayload(encoded));
        assertTrue(NearbyReportPayload.decode(encoded).isEmpty());
    }

    /**
     * The cluster row is never advanced to a terminal state, so a spot whose
     * last report was completed can still come back as a nearby hand-off. With
     * nothing outstanding behind it, the sheet would read "A Report Is Already
     * Near Here / Existing Reports 0" and ask the resident to decide about
     * nothing, so the hand-off has to be dismissed instead.
     */
    @Test
    public void aHandOffWithNothingOutstandingIsNotWorthShowing() {
        Map<String, Object> nearby = NearbyReportPayload.decode(
                NearbyReportPayload.PREFIX
                        + "{\"clusterId\":\"CL-123456\",\"clusterStatus\":\"Pending\","
                        + "\"distance\":1.1,\"reportCount\":0,\"spotCount\":0}");

        assertFalse(NearbyReportPayload.hasOutstandingReports(nearby));
    }

    @Test
    public void aHandOffWithWorkBehindItIsStillWorthShowing() {
        Map<String, Object> nearby = NearbyReportPayload.decode(
                NearbyReportPayload.PREFIX + "{\"clusterId\":\"CL-123456\",\"reportCount\":1}");

        assertTrue(NearbyReportPayload.hasOutstandingReports(nearby));
    }

    /** spotCount is the same figure under another name, so it counts too. */
    @Test
    public void spotCountIsAcceptedInPlaceOfReportCount() {
        assertFalse(NearbyReportPayload.hasOutstandingReports(
                NearbyReportPayload.decode(
                        NearbyReportPayload.PREFIX + "{\"spotCount\":0}")));
        assertTrue(NearbyReportPayload.hasOutstandingReports(
                NearbyReportPayload.decode(
                        NearbyReportPayload.PREFIX + "{\"spotCount\":3}")));
    }

    /**
     * Only a positive statement that nothing is there justifies going quiet, so
     * a missing or unreadable count leaves the resident in charge.
     */
    @Test
    public void anUnknownCountLeavesTheDecisionToTheResident() {
        assertTrue(NearbyReportPayload.hasOutstandingReports(null));
        assertTrue(NearbyReportPayload.hasOutstandingReports(new HashMap<>()));
        assertTrue(NearbyReportPayload.hasOutstandingReports(
                NearbyReportPayload.decode(NearbyReportPayload.PREFIX + "{\"reportCount\":\"x\"}")));
    }
}
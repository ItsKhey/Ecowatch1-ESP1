package com.example.myecowatch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.myecowatch1.data.models.Notification;
import com.example.myecowatch1.data.repository.NotificationRepository;
import com.example.myecowatch1.utils.NotificationDateFormatter;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class NotificationBackendContractTest {
    @Test
    public void mapsPostgresNotificationContract() {
        String json = "{"
                + "\"notification_id\":\"42\","
                + "\"resident_id\":\"RES-001\","
                + "\"report_id\":\"REP-001\","
                + "\"title\":\"Personnel Assigned\","
                + "\"message\":\"Cleanup personnel have been assigned.\","
                + "\"type\":\"PERSONNEL_ASSIGNED\","
                + "\"is_read\":false,"
                + "\"created_at\":\"2026-09-25T05:53:12.123+00:00\""
                + "}";

        Notification notification = new Gson().fromJson(json, Notification.class);

        assertNotNull(notification.getMongoId());
        assertEquals("42", notification.getMongoId());
        assertEquals("RES-001", notification.getResidentId());
        assertEquals("REP-001", notification.getReportId());
        assertEquals("PERSONNEL_ASSIGNED", notification.getType());
        assertTrue(!notification.isRead());
        assertEquals("Sep 25, 2026 • 1:53 PM",
                NotificationDateFormatter.format(notification.getCreatedAt()));
    }

    @Test
    public void convertsDataOnlyFcmPayloadWithoutInventingAnId() {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", "42");
        data.put("reportId", "REP-001");
        data.put("type", "REPORT_COMPLETED");
        data.put("title", "Report Completed");
        data.put("message", "Cleanup is complete.");
        data.put("createdAt", "2026-09-25T05:53:12.123+00:00");

        Notification notification = NotificationRepository.fromPushData("RES-001", data);

        assertNotNull(notification);
        assertEquals("42", notification.getMongoId());
        assertEquals("REP-001", notification.getReportId());
        int stableId = NotificationRepository.stableNotificationId("42");
        assertTrue(stableId > 0);
        assertEquals(stableId, NotificationRepository.stableNotificationId("42"));
    }
}

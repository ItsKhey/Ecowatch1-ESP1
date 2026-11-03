package com.example.ecowatchpersonnel1.data.remote;

import com.example.ecowatchpersonnel1.data.models.PersonnelNotification;

import java.util.ArrayList;
import java.util.List;

/** Response for {@code GET /api/personnel/notifications}. */
public class PersonnelNotificationsResponse {

    public boolean success;
    public List<PersonnelNotification> notifications = new ArrayList<>();
    public int count;

    public List<PersonnelNotification> getNotifications() {
        return notifications == null ? new ArrayList<>() : notifications;
    }
}

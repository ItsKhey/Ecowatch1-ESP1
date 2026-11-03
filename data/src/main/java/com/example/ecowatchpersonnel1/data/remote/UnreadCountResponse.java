package com.example.ecowatchpersonnel1.data.remote;

/** Response for {@code GET /api/personnel/notifications/unread-count}. */
public class UnreadCountResponse {

    public boolean success;
    public int unreadCount;

    public int getUnreadCount() {
        return unreadCount;
    }
}

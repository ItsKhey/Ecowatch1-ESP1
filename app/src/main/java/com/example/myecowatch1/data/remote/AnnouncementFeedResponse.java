package com.example.myecowatch1.data.remote;

import com.example.myecowatch1.data.models.Announcement;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * Envelope returned by {@code GET /api/announcements/my-announcements}.
 *
 * <p>The backend answers with a wrapper object rather than a bare array:
 * {@code { success, announcements, total, limit, offset }}. Declaring the call as
 * {@code Call<List<Announcement>>} made Gson try to read that object as a list,
 * which fails the whole response, so the feed never reached the local cache.
 * Modelled explicitly to match the wire format.
 */
public class AnnouncementFeedResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("announcements")
    private List<Announcement> announcements;

    @SerializedName("total")
    private int total;

    public boolean isSuccess() {
        return success;
    }

    /**
     * The announcement rows. Never null, so a response that omitted the field
     * empties the feed rather than throwing a NullPointerException.
     */
    public List<Announcement> getAnnouncements() {
        return announcements == null ? Collections.<Announcement>emptyList() : announcements;
    }

    /** Total rows matching the request, which can exceed the returned page. */
    public int getTotal() {
        return total;
    }
}

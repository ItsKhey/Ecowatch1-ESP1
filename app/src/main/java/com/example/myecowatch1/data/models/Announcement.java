package com.example.myecowatch1.data.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * A CENRO announcement cached locally so the dashboard card renders offline.
 *
 * <p>Rows are keyed on the server id, which makes a re-sync idempotent: the
 * same announcement is replaced rather than duplicated.
 */
@Entity(tableName = "announcements", indices = {@Index(value = "announcementId", unique = true)})
public class Announcement implements Serializable {

    /** Only used for a row that has not been identified by the server yet. */
    @PrimaryKey(autoGenerate = true)
    @SerializedName("localId")
    private int id;

    @SerializedName(value = "announcementId", alternate = {"id"})
    private String announcementId;

    private String title;
    private String message;

    @SerializedName(value = "audience", alternate = {"audience_type"})
    private String audience;

    private String barangay;

    /**
     * Stored as a comma-separated list. The resident feed only ever returns
     * audience-matched rows, so this is display metadata rather than something
     * the app filters on, and a type converter is not worth the extra schema
     * surface.
     *
     * <p>The backend sends a JSON array here. Gson's default String reader throws
     * on an array, which failed the entire response, so the ids are flattened on
     * the way in by {@link TargetResidentIdsDeserializer}.
     */
    @SerializedName(value = "targetResidentIds", alternate = {"target_resident_ids"})
    @JsonAdapter(TargetResidentIdsDeserializer.class)
    private String targetResidentIds;

    @SerializedName(value = "priority", alternate = {"announcement_priority"})
    private String priority;

    @SerializedName(value = "createdByAdminId", alternate = {"created_by_admin_id"})
    private String createdByAdminId;

    @SerializedName(value = "createdAt", alternate = {"created_at"})
    private String createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAnnouncementId() {
        if (announcementId != null && !announcementId.trim().isEmpty()) return announcementId;
        return id > 0 ? String.valueOf(id) : null;
    }
    public void setAnnouncementId(String announcementId) { this.announcementId = announcementId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public String getBarangay() { return barangay; }
    public void setBarangay(String barangay) { this.barangay = barangay; }

    public String getTargetResidentIds() { return targetResidentIds; }
    public void setTargetResidentIds(String targetResidentIds) { this.targetResidentIds = targetResidentIds; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(String createdByAdminId) { this.createdByAdminId = createdByAdminId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    /** True when CENRO marked the announcement as important. */
    public boolean isImportant() {
        return "IMPORTANT".equalsIgnoreCase(priority)
                || "Important".equalsIgnoreCase(priority);
    }

    /**
     * Flattens the {@code targetResidentIds} JSON array into the comma-separated
     * string the Room column holds.
     *
     * <p>Accepts an array, a bare string, and null so one unexpected shape from
     * the server cannot discard an announcement the resident should have read.
     */
    public static class TargetResidentIdsDeserializer
            implements JsonDeserializer<String> {

        @Override
        public String deserialize(JsonElement element, Type type,
                                  JsonDeserializationContext context)
                throws JsonParseException {
            if (element == null || element.isJsonNull()) return null;

            // Tolerate a plain string so an older or future server shape still
            // deserializes instead of failing the whole announcement.
            if (element.isJsonPrimitive()) return element.getAsString();

            if (!element.isJsonArray()) return null;

            JsonArray array = element.getAsJsonArray();
            List<String> values = new ArrayList<>(array.size());
            for (JsonElement item : array) {
                if (item == null || item.isJsonNull() || !item.isJsonPrimitive()) continue;
                String text = item.getAsString();
                if (text != null && !text.trim().isEmpty()) values.add(text);
            }

            return values.isEmpty() ? null : String.join(",", values);
        }
    }
}

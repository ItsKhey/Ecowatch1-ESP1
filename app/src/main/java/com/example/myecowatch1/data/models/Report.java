package com.example.myecowatch1.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.example.myecowatch1.utils.LongDateAdapter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "reports")
public class Report implements Serializable {
    @PrimaryKey(autoGenerate = true)
    @SerializedName("localId")
    private int id;

    @SerializedName(value = "mongoId", alternate = {"_id", "id", "serverId"})
    private String mongoId;
    
    @SerializedName(value = "reportId", alternate = {"report_id"})
    private String reportId;
    
    @SerializedName(value = "residentId", alternate = {"resident_id"})
    private String residentId;

    private String title;
    private String description;
    
    @SerializedName(value = "category", alternate = {"wasteType"})
    private String wasteType;

    @SerializedName(value = "imageUrl", alternate = {"photoUrl", "image_url"})
    private String photoUrl;

    /**
     * Every photo attached to the report, in upload order. The server sends this
     * as {@code photos}; {@link #photoUrl} only holds the cover image, so this
     * list is what a multi-photo report needs in order to render every image.
     */
    @SerializedName(value = "photos", alternate = {"photoUrls", "imageUrls"})
    private List<String> photoUrls = new ArrayList<>();

    private String localPhotoPath; // New field for local storage
    
    private double latitude;
    private double longitude;
    private String status;

    @SerializedName(value = "adminResponse", alternate = {"admin_response"})
    private String adminResponse;

    /**
     * What the assigned cleaner wrote when they finished the work, joined onto
     * the report for display. Read separately from {@link #adminResponse}
     * because it lives on the assignment, not on the report row. Null when no
     * cleaner has completed the work yet.
     */
    @SerializedName(value = "personnelResponse", alternate = {"personnel_response"})
    private String personnelResponse;

    /**
     * Assignment, as the server reports it to the resident.
     *
     * <p>The server deliberately withholds personnel <em>names</em> from resident
     * payloads and sends only the identifiers, so nothing here is ever rendered
     * as text. It exists so the details dialog can tell the resident that a crew
     * has been put on the job, and when, rather than showing an empty card.
     */
    @SerializedName(value = "assignedPersonnelId", alternate = {"assigned_personnel_id"})
    private String assignedPersonnelId;

    @SerializedName(value = "assignedPersonnelIds", alternate = {"assigned_personnel_ids"})
    private List<String> assignedPersonnelIds = new ArrayList<>();

    @SerializedName(value = "assignedAt", alternate = {"assigned_at"})
    private String assignedAt;

    @SerializedName(value = "handledById", alternate = {"handled_by_id"})
    private String handledById;

    /** When the cleaning was signed off, if it has been. */
    @SerializedName(value = "resolvedAt", alternate = {"resolved_at"})
    private String resolvedAt;

    @SerializedName(value = "createdAt", alternate = {"created_at"})
    private String createdAt;
    
    @SerializedName(value = "updatedAt", alternate = {"updated_at"})
    private String updatedAt;
    
    private boolean isSynced;
    
    @SerializedName(value = "isDeleted", alternate = {"is_deleted"})
    private boolean isDeleted;
    
    @SerializedName(value = "deletedAt", alternate = {"deleted_at"})
    @JsonAdapter(LongDateAdapter.class)
    private Long deletedAt;
    
    @SerializedName(value = "isArchived", alternate = {"is_archived"})
    private boolean isArchived;
    
    @SerializedName(value = "isRestored", alternate = {"is_restored"})
    private boolean isRestored;

    @SerializedName(value = "clusterId", alternate = {"cluster_id"})
    private String clusterId;

    public Report(String title, String description, String wasteType, double latitude, double longitude) {
        this.title = title;
        this.description = description;
        this.wasteType = wasteType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = "Pending";
        this.isSynced = false;
        this.reportId = "REP-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        this.createdAt = sdf.format(new java.util.Date());
        this.updatedAt = this.createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMongoId() {
        return (mongoId != null && !mongoId.isEmpty()) ? mongoId : reportId;
    }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    /** Returns only an actual server identifier; never a provisional client ID. */
    public String getStoredMongoId() { return mongoId; }

    public String getServerReportId() { return reportId; }
    public void setServerReportId(String reportId) { this.reportId = reportId; }

    public String getReportId() { 
        if (reportId != null && !reportId.isEmpty()) return reportId;
        if (mongoId != null && !mongoId.isEmpty()) return mongoId;
        return id > 0 ? "REP-LOCAL-" + id : null;
    } 
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getWasteType() { return wasteType; }
    public void setWasteType(String wasteType) { this.wasteType = wasteType; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public List<String> getPhotoUrls() {
        return photoUrls == null ? new ArrayList<>() : photoUrls;
    }

    /**
     * Stores the uploaded photo list, dropping blanks and any repeat of the cover
     * image. The server already orders the list with the cover first; keeping the
     * cover out of the tail avoids rendering the same image twice when an older
     * cached row is refreshed.
     */
    public void setPhotoUrls(List<String> urls) {
        List<String> cleaned = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                if (url == null) continue;
                String trimmed = url.trim();
                if (trimmed.isEmpty() || cleaned.contains(trimmed)) continue;
                cleaned.add(trimmed);
            }
        }
        this.photoUrls = cleaned;
    }

    /**
     * Every image to render for this report, cover first. Falls back to the
     * single cover image so reports stored before the photo list existed still
     * display correctly.
     */
    public List<String> getDisplayPhotoUrls() {
        List<String> urls = new ArrayList<>(getPhotoUrls());
        if (urls.isEmpty() && photoUrl != null && !photoUrl.trim().isEmpty()) {
            urls.add(photoUrl.trim());
        }
        return urls;
    }

    /** True when the report carries more than one image. */
    public boolean hasMultiplePhotos() {
        return getPhotoUrls().size() > 1
                || (getPhotoUrls().size() == 1
                    && photoUrl != null && !photoUrl.trim().isEmpty()
                    && !photoUrl.trim().equals(getPhotoUrls().get(0)));
    }

    public String getLocalPhotoPath() { return localPhotoPath; }
    public void setLocalPhotoPath(String localPhotoPath) { this.localPhotoPath = localPhotoPath; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminResponse() { return adminResponse; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }

    public String getPersonnelResponse() { return personnelResponse; }
    public void setPersonnelResponse(String personnelResponse) { this.personnelResponse = personnelResponse; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }

    public boolean isRestored() { return isRestored; }
    public void setRestored(boolean restored) { isRestored = restored; }

    public String getClusterId() { return clusterId; }
    public void setClusterId(String clusterId) { this.clusterId = clusterId; }

    public String getAssignedPersonnelId() { return assignedPersonnelId; }
    public void setAssignedPersonnelId(String assignedPersonnelId) { this.assignedPersonnelId = assignedPersonnelId; }

    public List<String> getAssignedPersonnelIds() {
        return assignedPersonnelIds == null ? new ArrayList<>() : assignedPersonnelIds;
    }

    public void setAssignedPersonnelIds(List<String> ids) {
        this.assignedPersonnelIds = ids == null ? new ArrayList<>() : ids;
    }

    public String getAssignedAt() { return assignedAt; }
    public void setAssignedAt(String assignedAt) { this.assignedAt = assignedAt; }

    public String getHandledById() { return handledById; }
    public void setHandledById(String handledById) { this.handledById = handledById; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }

    /**
     * Whether an administrator has actually put a crew on this report.
     *
     * <p>The details dialog shows the assignment section only when this is true.
     * Any one of the three signals is enough, because the columns are written
     * independently: a report can carry an assignment timestamp with no
     * identifier, or an identifier with no timestamp.
     */
    public boolean isAssigned() {
        return hasText(assignedAt) || hasText(assignedPersonnelId)
                || !getAssignedPersonnelIds().isEmpty();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

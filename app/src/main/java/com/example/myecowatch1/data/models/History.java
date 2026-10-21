package com.example.myecowatch1.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.example.myecowatch1.utils.LongDateAdapter;
import java.io.Serializable;

@Entity(tableName = "history")
public class History implements Serializable {
    @PrimaryKey(autoGenerate = true)
    @SerializedName("localId")
    private int id;

    @SerializedName(value = "mongoId", alternate = {"_id", "id", "serverId", "history_id"})
    private String mongoId;

    @SerializedName(value = "residentId", alternate = {"resident_id"})
    private String residentId;

    @SerializedName(value = "reportId", alternate = {"report_id"})
    private String reportId;

    private String action;

    @SerializedName(value = "oldStatus", alternate = {"old_status"})
    private String oldStatus;

    @SerializedName(value = "newStatus", alternate = {"new_status"})
    private String newStatus;

    private String description;
    
    @SerializedName(value = "timestampValue", alternate = {"timestamp", "created_at"})
    @JsonAdapter(LongDateAdapter.class)
    private Long timestampValue; 
    
    @SerializedName(value = "isDeleted", alternate = {"is_deleted"})
    private boolean isDeleted;

    @SerializedName(value = "deletedAt", alternate = {"deleted_at"})
    @JsonAdapter(LongDateAdapter.class)
    private Long deletedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMongoId() {
        return (mongoId != null && !mongoId.isEmpty()) ? mongoId : (id > 0 ? String.valueOf(id) : null);
    }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    /** Returns only a server history ID; local Room IDs must not be sent upstream. */
    public String getStoredMongoId() { return mongoId; }

    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId; }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getTimestampValue() { return timestampValue; }
    public void setTimestampValue(Long timestampValue) { this.timestampValue = timestampValue; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public Long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Long deletedAt) { this.deletedAt = deletedAt; }
}

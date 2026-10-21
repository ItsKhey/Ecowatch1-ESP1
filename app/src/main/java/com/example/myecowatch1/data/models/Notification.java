package com.example.myecowatch1.data.models;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

@Entity(
        tableName = "notifications",
        indices = {
                @Index(
                        value = {"residentId", "mongoId"},
                        unique = true,
                        name = "index_notifications_residentId_mongoId"
                ),
                @Index(value = {"residentId", "reportId"}, name = "index_notifications_residentId_reportId")
        }
)
public class Notification implements Serializable {
    @PrimaryKey(autoGenerate = true)
    @SerializedName("localId")
    private int id;

    /** Stable PostgreSQL notification_id; retained under the legacy Room name. */
    @SerializedName(
            value = "mongoId",
            alternate = {"_id", "id", "serverId", "notification_id", "notificationId"}
    )
    private String mongoId;

    @SerializedName(value = "residentId", alternate = {"resident_id"})
    private String residentId;

    @SerializedName(value = "reportId", alternate = {"report_id"})
    private String reportId;

    private String title;
    private String message;
    private String type;

    @SerializedName(value = "isRead", alternate = {"is_read"})
    private boolean isRead;

    @SerializedName(value = "isDeleted", alternate = {"is_deleted"})
    private boolean isDeleted;

    @SerializedName(value = "createdAt", alternate = {"created_at"})
    private String createdAt;

    @SerializedName(value = "deletedAt", alternate = {"deleted_at"})
    private String deletedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMongoId() { return mongoId; }
    public void setMongoId(String mongoId) { this.mongoId = mongoId; }

    public String getResidentId() { return residentId; }
    public void setResidentId(String residentId) { this.residentId = residentId; }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}

package com.example.myecowatch1.data.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "users")
public class User {
    @PrimaryKey(autoGenerate = true)
    @SerializedName("localId")
    private int id;
    
    @SerializedName(value = "serverId", alternate = {"resident_id", "residentId", "_id", "id"})
    private String serverId;

    @SerializedName(value = "firstName", alternate = {"first_name"})
    private String firstName;

    @SerializedName(value = "lastName", alternate = {"last_name"})
    private String lastName;

    private String username; // REQUIRED
    private String email;
    private String address;

    /** Contact details the admin app renders on a reporter's Contact Information card. */
    private String phoneNumber;

    private String messengerLink;

    @SerializedName(value = "profileImageUrl", alternate = {"profile_image", "profileImage", "profile_image_url"})
    private String profileImageUrl;

    @SerializedName(value = "createdAt", alternate = {"created_at"})
    private String createdAt;

    @SerializedName(value = "updatedAt", alternate = {"updated_at"})
    private String updatedAt;

    public User(String serverId, String firstName, String lastName, String username, String email, String address, String profileImageUrl) {
        this.serverId = serverId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.email = email;
        this.address = address;
        this.profileImageUrl = profileImageUrl;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getMessengerLink() { return messengerLink; }
    public void setMessengerLink(String messengerLink) { this.messengerLink = messengerLink; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

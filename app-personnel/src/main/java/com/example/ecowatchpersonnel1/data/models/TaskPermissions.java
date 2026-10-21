package com.example.ecowatchpersonnel1.data.models;

/**
 * Server-computed workflow permissions for one task.
 *
 * <p>The app renders buttons straight from these flags instead of re-implementing
 * the ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED rules, so a button can
 * never be shown that the backend would reject.
 */
public class TaskPermissions {

    public boolean canAccept;
    public boolean canUploadBefore;
    public boolean canStart;
    public boolean canUploadAfter;
    public boolean canComplete;
    public boolean canDeleteEvidence;
}

# EcoWatch Firebase Cloud Messaging Setup

Phase 2C notification persistence, unread state, report navigation, and the Render
FCM sender are implemented. Actual push delivery requires the Firebase project
credentials that are intentionally not stored in source control.

## 1. Register the Resident Android app

1. Open the EcoWatch Firebase project (or create one).
2. Add an Android app with package name:

   ```text
   com.example.myecowatch1
   ```

3. Download the generated configuration and place it at:

   ```text
   MyEcoWatch1/app/google-services.json
   ```

The real file is ignored by Git. A non-functional template is available at
`app/google-services.json.example`.

4. Rebuild the Resident app. The Google Services Gradle plugin is applied only
when the real file exists, so source builds remain possible without secrets.

## 2. Configure Render

Create a Firebase service account with the Firebase Cloud Messaging API enabled.
Set one of these in Render:

```text
FIREBASE_SERVICE_ACCOUNT_JSON={...complete JSON...}
```

or:

```text
FIREBASE_PROJECT_ID=...
FIREBASE_CLIENT_EMAIL=...
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

Do not commit the service account JSON or private key.

## 3. Verify deployment

After Render restarts, this endpoint should report:

```json
{
  "pushNotifications": "CONFIGURED"
}
```

```text
GET https://ecowatch-backend1.onrender.com/api/health
```

After a resident logs in, the Android app obtains an FCM token and WorkManager
retries `PUT /api/notifications/fcm-token` with the resident JWT. Token
rotation is handled by `onNewToken`; the backend moves a token to the newly
authenticated resident when an account switches on the same device.

## 4. Production verification

1. Keep the Resident app installed and logged in.
2. Assign active personnel in Admin.
3. Confirm an in-app row and an Android system notification appear.
4. Tap the system notification and confirm the existing Report Details dialog
   opens for the matching report ID.
5. Repeat for response update, Cleaning Started, Denied, and Completed events.

FCM messages include server-owned notification content plus data. Foreground
messages use the existing EcoWatch green channel and unread deduplication;
background messages use the same channel as an OS fallback. Both paths carry
report-aware click data so the existing report-details destination is reused.

# Update Backend to Support Username Editing

This plan outlines the changes required in the `MyEcoWatchBackend1` project to allow residents to update their usernames while ensuring uniqueness.

## User Review Required

> [!IMPORTANT]
> Changing a username is a sensitive operation. If the username is used as a unique identifier for sessions or other relationships, ensure that those remain intact. In this project, `resident._id` is used for the token, so changing `username` is safe.

## Proposed Changes

### Controllers

#### [MODIFY] [authController.js](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatchBackend1/controllers/authController.js)
- Update the `updateProfile` function:
    - Extract `username` from `req.body`.
    - If a new `username` is provided and it differs from the current one:
        - Check if the new `username` already exists in the `Resident` collection.
        - If it exists, return a `400 Bad Request` error with the message "Username is already taken".
    - Save the new `username` to the resident document.

## Verification Plan

### Manual Verification (Postman/API Test)
1. Perform a `PATCH` request to `/api/auth/profile` with a new `username`.
2. Verify that the response contains the updated username.
3. Perform the same request using a username that already belongs to another resident.
4. Verify that the server returns a `400` error with "Username is already taken".
5. Log in again with the new username to ensure authentication still works.

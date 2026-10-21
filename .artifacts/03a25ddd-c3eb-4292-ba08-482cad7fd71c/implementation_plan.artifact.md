# Implementation Plan - Database Structure Realignment and History Support

This plan outlines the steps to align the Android application with the new MongoDB database structure (separate collections for residents, reports, trash, archives, and history) and introduces the local "History" infrastructure for future use.

## User Review Required

> [!IMPORTANT]
> **Data Synchronization Strategy**: Currently, the app uses `isDeleted` and `isArchived` flags within a single local `reports` table. While MongoDB will use separate collections (`trashreports`, `archivedreports`), I recommend maintaining a single local table with state flags for efficiency, while updating the `ApiService` to target the appropriate backend endpoints.
>
> **History Triggering**: I will implement "preparations" for History. This means creating history entries locally when reports are created, archived, or deleted. Please confirm if you want specific actions (like status updates) to be logged as well.

## Proposed Changes

---

### 1. Data Models [NEW]

#### [NEW] [History.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/models/History.java)
- Define a model to track actions performed on reports.
- Fields: `id` (int), `mongoId` (String), `residentId` (String), `reportId` (String), `action` (String - e.g., "SUBMITTED", "ARCHIVED", "DELETED"), `description` (String), `timestamp` (Long).

---

### 2. Local Database (Room)

#### [NEW] [HistoryDao.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/local/HistoryDao.java)
- Basic operations: `insertHistory`, `getHistoryByResidentId`, `deleteHistory`.

#### [MODIFY] [AppDatabase.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/local/AppDatabase.java)
- Register the `History` entity.
- Add `historyDao()` abstract method.
- Increment database version (if required, though `fallbackToDestructiveMigration` is enabled).

---

### 3. API Integration

#### [MODIFY] [ApiService.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/remote/ApiService.java)
- Update existing endpoints to ensure they align with the new MongoDB collection logic (e.g., ensuring `moveToTrash` actually moves the document to `trashreports` collection on the server).
- [Future-proofing]: Add placeholder methods for fetching `history` if the backend supports it.

---

### 4. Repository Logic

#### [MODIFY] [ReportRepository.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/repository/ReportRepository.java)
- Update `submitReport`, `softDeleteReport`, and `archiveReport` to also insert a record into the `History` table.
- Initialize `HistoryDao`.

## Verification Plan

### Automated Tests
- I will verify the Room database migration by building the app and checking if the new `history` table is created.
- I will verify that `ReportRepository` successfully triggers history insertion logic (via logging).

### Manual Verification
- Deploy the app to a device/emulator.
- Perform a report submission and deletion, then use `App Inspection` in Android Studio to verify that a new entry appears in the `history` table.

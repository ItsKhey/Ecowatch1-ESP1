package com.example.ecowatchpersonnel1.data.local;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * The Personnel app's local store.
 *
 * <p>Separate from the resident app's database, and keyed by a different file
 * name on purpose: the two apps share a codebase but never share an account, so
 * one database must not be able to show the other's rows.
 *
 * <p>Migrations are written by hand rather than falling back to a destructive
 * one. Both tables here are the only copy of something the device holds: task
 * history for a cleaner who may never reconnect, and the biometric registrations
 * they had to prove a password to create. Recreating either table would quietly
 * destroy that.
 */
@Database(
        entities = {
                TaskHistoryEntity.class,
                BiometricSetting.class,
                PersonnelNotificationEntity.class,
                PersonnelNotificationTombstone.class
        },
        version = 5,
        exportSchema = false
)
public abstract class PersonnelDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "ecowatch_personnel_db";

    /**
     * Adds the biometric registrations table.
     *
     * <p>Created rather than dropped-and-rebuilt so an existing install keeps its
     * task history across the upgrade. Existing cleaners have no row here yet;
     * {@link BiometricDao} treats a missing row as "not registered", which is
     * exactly right, so nothing is back-filled.
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `biometric_settings` ("
                            + "`personnelId` TEXT NOT NULL, "
                            + "`username` TEXT, "
                            + "`fingerprintEnabled` INTEGER NOT NULL, "
                            + "`createdAt` INTEGER NOT NULL, "
                            + "`updatedAt` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`personnelId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_biometric_settings_fingerprintEnabled "
                            + "ON biometric_settings(fingerprintEnabled)"
            );
        }
    };

    /**
     * Adds the notification cache and its tombstones.
     *
     * <p>Created rather than dropped-and-rebuilt so an existing install keeps its
     * task history and biometric registrations across the upgrade. There is
     * nothing to back-fill: the tables are a device-side cache, and the first
     * refresh repopulates the notifications from the server.
     *
     * <p>The composite indices match the {@code @Entity} declarations exactly.
     * Room compares the full index set on every open, so an index present here
     * but absent from the entity aborts the upgrade with "Migration didn't
     * properly handle".
     */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personnel_notifications` ("
                            + "`notificationId` TEXT NOT NULL, "
                            + "`personnelId` TEXT, "
                            + "`assignmentId` TEXT, "
                            + "`reportId` TEXT, "
                            + "`title` TEXT, "
                            + "`message` TEXT, "
                            + "`type` TEXT, "
                            + "`isRead` INTEGER NOT NULL, "
                            + "`isDeleted` INTEGER NOT NULL, "
                            + "`deletedAt` INTEGER, "
                            + "`createdAt` TEXT, "
                            + "`syncedAt` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`notificationId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_personnel_notifications_personnelId_isDeleted "
                            + "ON personnel_notifications(personnelId, isDeleted)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_personnel_notifications_personnelId_isRead "
                            + "ON personnel_notifications(personnelId, isRead)"
            );
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personnel_notification_tombstones` ("
                            + "`notificationId` TEXT NOT NULL, "
                            + "`personnelId` TEXT, "
                            + "`deletedAt` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`notificationId`))"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_personnel_notification_tombstones_personnelId "
                            + "ON personnel_notification_tombstones(personnelId)"
            );
        }
    };

    /**
     * Adds the waste scale to a saved assignment.
     *
     * <p>Added as a nullable column rather than by rebuilding the table: a
     * cleaner who has never connected since the upgrade still has their history
     * on the device, and this table is the only copy of it. Rows written before
     * the upgrade carry no scale, and {@link com.example.ecowatchpersonnel1.data.models.WasteScale#canonical}
     * reads a missing scale as the smallest one, which is what those jobs were.
     */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Added rather than rebuilt, so the cleaner's saved history survives
            // the upgrade. The guard keeps the statement re-runnable: Room rolls
            // a failed upgrade back, but a device that was interrupted part-way
            // through one can be left with the column already in place, and
            // "duplicate column name" would then abort every later launch.
            if (!hasColumn(database, "task_history", "wasteScale")) {
                database.execSQL(
                        "ALTER TABLE `task_history` ADD COLUMN `wasteScale` TEXT"
                );
            }
        }
    };

    /**
     * Adds the revision flag and timestamp to a saved assignment.
     *
     * <p>Both are nullable/zero for existing rows: a history written before this
     * upgrade cannot know whether the work was ever sent back, and guessing from
     * a status that reads IN_PROGRESS would mark fresh work as revised. The next
     * sync fills them from the server.
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            if (!hasColumn(database, "task_history", "revisionRequested")) {
                database.execSQL(
                        "ALTER TABLE `task_history` ADD COLUMN `revisionRequested` "
                                + "INTEGER NOT NULL DEFAULT 0"
                );
            }
            if (!hasColumn(database, "task_history", "revisionRequestedAt")) {
                database.execSQL(
                        "ALTER TABLE `task_history` ADD COLUMN `revisionRequestedAt` TEXT"
                );
            }
        }
    };

    /**
     * True when the table already has the column.
     *
     * <p>Read with PRAGMA rather than a plain query, because the answer has to
     * hold even for a table this database has not touched yet.
     */
    private static boolean hasColumn(@NonNull SupportSQLiteDatabase database,
                                      @NonNull String table,
                                      @NonNull String column) {
        try (Cursor cursor = database.query("PRAGMA table_info(`" + table + "`)", new Object[0])) {
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex < 0) return false;
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(nameIndex))) return true;
            }
        }
        return false;
    }

    private static volatile PersonnelDatabase instance;

    public abstract TaskHistoryDao taskHistoryDao();

    public abstract BiometricDao biometricDao();

    public abstract PersonnelNotificationDao personnelNotificationDao();

    public abstract PersonnelNotificationTombstoneDao personnelNotificationTombstoneDao();

    public static PersonnelDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (PersonnelDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    PersonnelDatabase.class,
                                    DATABASE_NAME
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                                    MIGRATION_4_5)
                            .build();
                }
            }
        }
        return instance;
    }

    }
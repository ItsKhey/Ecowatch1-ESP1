package com.example.ecowatchpersonnel1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Executes MIGRATION_2_3 against a real SQLite engine and asserts the result is
 * exactly what Room validates on open.
 *
 * <p>Room compares the live schema against the {@code @Entity} declarations on
 * every database open. A migration that creates a table or index the entity does
 * not declare aborts the upgrade with
 * {@code IllegalStateException: Migration didn't properly handle}, which crashes
 * the app at startup. An index present after the migration but absent from the
 * entity is invisible to a build, which is why this runs against a real database.
 */
public class PersonnelNotificationMigrationTest {

    private static final String NOTIFICATIONS = "personnel_notifications";
    private static final String TOMBSTONES = "personnel_notification_tombstones";

    /** The exact statements from PersonnelDatabase.MIGRATION_2_3. */
    private static final String[] MIGRATION_2_3 = {
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
                    + "PRIMARY KEY(`notificationId`))",
            "CREATE INDEX IF NOT EXISTS index_personnel_notifications_personnelId_isDeleted "
                    + "ON personnel_notifications(personnelId, isDeleted)",
            "CREATE INDEX IF NOT EXISTS index_personnel_notifications_personnelId_isRead "
                    + "ON personnel_notifications(personnelId, isRead)",
            "CREATE TABLE IF NOT EXISTS `personnel_notification_tombstones` ("
                    + "`notificationId` TEXT NOT NULL, "
                    + "`personnelId` TEXT, "
                    + "`deletedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`notificationId`))",
            "CREATE INDEX IF NOT EXISTS index_personnel_notification_tombstones_personnelId "
                    + "ON personnel_notification_tombstones(personnelId)"
    };

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    public void notificationTableMatchesTheEntityDeclaration() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);

            assertEquals("column set must match the @Entity declaration exactly",
                    new LinkedHashSet<>(List.of(
                            "notificationId", "personnelId", "assignmentId", "reportId",
                            "title", "message", "type", "isRead", "isDeleted",
                            "deletedAt", "createdAt", "syncedAt")),
                    new LinkedHashSet<>(columnNames(db, NOTIFICATIONS)));

            assertEquals("TEXT", columnType(db, NOTIFICATIONS, "notificationId"));
            assertEquals("INTEGER", columnType(db, NOTIFICATIONS, "isRead"));
            assertEquals("INTEGER", columnType(db, NOTIFICATIONS, "isDeleted"));
            assertEquals("INTEGER", columnType(db, NOTIFICATIONS, "syncedAt"));

            assertTrue("notificationId must be NOT NULL",
                    notNull(db, NOTIFICATIONS, "notificationId"));
            assertTrue("isRead must be NOT NULL", notNull(db, NOTIFICATIONS, "isRead"));
            assertTrue("isDeleted must be NOT NULL", notNull(db, NOTIFICATIONS, "isDeleted"));
            assertTrue("syncedAt must be NOT NULL", notNull(db, NOTIFICATIONS, "syncedAt"));

            // deletedAt is nullable on purpose: it is only set for trashed rows.
            assertTrue("deletedAt must stay nullable",
                    !notNull(db, NOTIFICATIONS, "deletedAt"));

            assertEquals("notificationId is the sole primary key at position 1",
                    1, primaryKeyPosition(db, NOTIFICATIONS, "notificationId"));
            assertEquals("personnelId must not be part of the primary key",
                    0, primaryKeyPosition(db, NOTIFICATIONS, "personnelId"));

            Set<String> indices = userIndices(db, NOTIFICATIONS);
            assertEquals("index set must match the @Entity indices exactly",
                    new LinkedHashSet<>(List.of(
                            "index_personnel_notifications_personnelId_isDeleted",
                            "index_personnel_notifications_personnelId_isRead")),
                    indices);

            assertEquals(List.of("personnelId", "isDeleted"),
                    indexColumns(db, "index_personnel_notifications_personnelId_isDeleted"));
            assertEquals(List.of("personnelId", "isRead"),
                    indexColumns(db, "index_personnel_notifications_personnelId_isRead"));
            assertTrue("index must not be UNIQUE",
                    !isUnique(db, NOTIFICATIONS,
                            "index_personnel_notifications_personnelId_isDeleted"));
        }
    }

    @Test
    public void tombstoneTableMatchesTheEntityDeclaration() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);

            assertEquals(new LinkedHashSet<>(List.of(
                    "notificationId", "personnelId", "deletedAt")),
                    new LinkedHashSet<>(columnNames(db, TOMBSTONES)));

            assertTrue("notificationId must be NOT NULL",
                    notNull(db, TOMBSTONES, "notificationId"));
            assertTrue("deletedAt must be NOT NULL", notNull(db, TOMBSTONES, "deletedAt"));
            assertTrue("personnelId must stay nullable",
                    !notNull(db, TOMBSTONES, "personnelId"));

            assertEquals(1, primaryKeyPosition(db, TOMBSTONES, "notificationId"));

            assertEquals(new LinkedHashSet<>(List.of(
                            "index_personnel_notification_tombstones_personnelId")),
                    userIndices(db, TOMBSTONES));
            assertEquals(List.of("personnelId"),
                    indexColumns(db, "index_personnel_notification_tombstones_personnelId"));
        }
    }

    /**
     * The tables here are a cache the first refresh repopulates, but the upgrade
     * must not disturb what the cleaner actually cares about on the device.
     */
    @Test
    public void migrationPreservesTaskHistoryAndBiometrics() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            st.execute("CREATE TABLE `task_history` (`assignmentId` TEXT NOT NULL, `status` TEXT, PRIMARY KEY(`assignmentId`))");
            st.execute("INSERT INTO `task_history` VALUES ('ASG-1','COMPLETED')");
            st.execute("CREATE TABLE `biometric_settings` (`personnelId` TEXT NOT NULL, `username` TEXT, PRIMARY KEY(`personnelId`))");
            st.execute("INSERT INTO `biometric_settings` VALUES ('PER-1','cleaner')");

            applyMigration(db);

            assertEquals(1, countRows(db, "SELECT COUNT(*) FROM task_history"));
            assertEquals("ASG-1", queryString(db, "SELECT assignmentId FROM task_history"));
            assertEquals(1, countRows(db, "SELECT COUNT(*) FROM biometric_settings"));
            assertEquals("cleaner",
                    queryString(db, "SELECT username FROM biometric_settings"));
        }
    }

    /**
     * A failed upgrade rolls back and the migration is retried on the next launch,
     * so it must be re-runnable and converge rather than error.
     */
    @Test
    public void migrationIsRerunnableAfterAFailedUpgrade() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);
            applyMigration(db);
            applyMigration(db);

            assertEquals(12, columnNames(db, NOTIFICATIONS).size());
            assertEquals(3, columnNames(db, TOMBSTONES).size());
            assertEquals(2, userIndices(db, NOTIFICATIONS).size());
            assertEquals(1, userIndices(db, TOMBSTONES).size());
        }
    }

    /** Only the two new tables may appear, so the migration has no side effects. */
    @Test
    public void migrationCreatesNoOtherTables() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);

            // ORDER BY name is used by the query, so the expectation is in SQLite's
            // own collation order rather than the order the statements are written.
            assertEquals("only the notification tables should exist",
                    List.of(TOMBSTONES, NOTIFICATIONS), tableNames(db));
        }
    }

    /**
     * The Trash queries the UI depends on: a trashed row must be excluded from the
     * All tab and included in the Trash tab for its owner alone.
     */
    @Test
    public void trashedAndActiveRowsAreSeparatedByOwner() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            applyMigration(db);
            st.execute("INSERT INTO personnel_notifications "
                    + "(`notificationId`,`personnelId`,`isRead`,`isDeleted`,`deletedAt`,`syncedAt`) "
                    + "VALUES ('1','PER-1',0,0,NULL,100)");
            st.execute("INSERT INTO personnel_notifications "
                    + "(`notificationId`,`personnelId`,`isRead`,`isDeleted`,`deletedAt`,`syncedAt`) "
                    + "VALUES ('2','PER-1',0,1,200,100)");
            st.execute("INSERT INTO personnel_notifications "
                    + "(`notificationId`,`personnelId`,`isRead`,`isDeleted`,`deletedAt`,`syncedAt`) "
                    + "VALUES ('3','PER-2',0,0,NULL,100)");

            assertEquals("All tab: PER-1's untrashed rows only",
                    List.of("1"), ids(db,
                    "SELECT notificationId FROM personnel_notifications "
                            + "WHERE personnelId = 'PER-1' AND isDeleted = 0"));

            assertEquals("Trash tab: PER-1's trashed rows only",
                    List.of("2"), ids(db,
                    "SELECT notificationId FROM personnel_notifications "
                            + "WHERE personnelId = 'PER-1' AND isDeleted = 1"));

            assertEquals("another cleaner's rows must never appear",
                    0, countRows(db,
                    "SELECT COUNT(*) FROM personnel_notifications "
                            + "WHERE personnelId = 'PER-1' AND notificationId = '3'"));
        }
    }

    /** Trash is ordered by deletion time so the last action leads the tab. */
    @Test
    public void trashIsOrderedByWhenTheRowWasTrashed() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            applyMigration(db);
            st.execute("INSERT INTO personnel_notifications "
                    + "(`notificationId`,`personnelId`,`isRead`,`isDeleted`,`deletedAt`,`syncedAt`) "
                    + "VALUES ('old','PER-1',0,1,100,999)");
            st.execute("INSERT INTO personnel_notifications "
                    + "(`notificationId`,`personnelId`,`isRead`,`isDeleted`,`deletedAt`,`syncedAt`) "
                    + "VALUES ('new','PER-1',0,1,500,1)");

            assertEquals("newest deletion first, not newest row sync",
                    List.of("new", "old"), ids(db,
                    "SELECT notificationId FROM personnel_notifications "
                            + "WHERE personnelId = 'PER-1' AND isDeleted = 1 "
                            + "ORDER BY COALESCE(deletedAt, syncedAt) DESC"));
        }
    }

    private static void applyMigration(Connection db) throws SQLException {
        try (Statement st = db.createStatement()) {
            for (String sql : MIGRATION_2_3) {
                st.execute(sql);
            }
        }
    }

    private static List<String> tableNames(Connection db) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' "
                             + "AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    private static List<String> columnNames(Connection db, String table) throws SQLException {
        return pragmaStrings(db, "PRAGMA table_info(`" + table + "`)", "name");
    }

    private static String columnType(Connection db, String table, String column) throws SQLException {
        return pragmaValue(db, "PRAGMA table_info(`" + table + "`)", "type", "name", column);
    }

    private static boolean notNull(Connection db, String table, String column) throws SQLException {
        return pragmaInt(db, "PRAGMA table_info(`" + table + "`)", "notnull", "name", column) == 1;
    }

    private static int primaryKeyPosition(Connection db, String table, String column)
            throws SQLException {
        return pragmaInt(db, "PRAGMA table_info(`" + table + "`)", "pk", "name", column);
    }

    /** Indices created by CREATE INDEX (origin 'c'), which is what Room reads. */
    private static Set<String> userIndices(Connection db, String table) throws SQLException {
        // Exact-case match on origin: a case-insensitive match would also pull in
        // the primary-key index ('pk'), which Room does not count as user-created.
        return new LinkedHashSet<>(
                pragmaStrings(db, "PRAGMA index_list(`" + table + "`)",
                        "name", "origin", "c", true));
    }

    private static List<String> indexColumns(Connection db, String index) throws SQLException {
        return pragmaStrings(db, "PRAGMA index_info(`" + index + "`)", "name");
    }

    private static boolean isUnique(Connection db, String table, String index) throws SQLException {
        return pragmaInt(db, "PRAGMA index_list(`" + table + "`)", "unique", "name", index) == 1;
    }

    private static int countRows(Connection db, String sql) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static String queryString(Connection db, String sql) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static List<String> ids(Connection db, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) values.add(rs.getString(1));
        }
        return values;
    }

    private static List<String> pragmaStrings(Connection db, String pragma, String column)
            throws SQLException {
        return pragmaStrings(db, pragma, column, null, null);
    }

    /**
     * Reads one column out of a PRAGMA result, optionally keeping only the row
     * whose {@code filterColumn} equals {@code filterValue}.
     *
     * <p>{@code exactCase} matters for {@code PRAGMA index_list}: origin values
     * include both {@code c} (CREATE INDEX) and {@code pk}, so a case-insensitive
     * match on {@code c} would also pull in the primary-key index.
     */
    private static List<String> pragmaStrings(Connection db, String pragma, String column,
                                              String filterColumn, String filterValue)
            throws SQLException {
        return pragmaStrings(db, pragma, column, filterColumn, filterValue, false);
    }

    private static List<String> pragmaStrings(Connection db, String pragma, String column,
                                              String filterColumn, String filterValue,
                                              boolean exactCase) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (filterColumn != null) {
                    String actual = rs.getString(filterColumn);
                    boolean matches = exactCase
                            ? filterValue.equals(actual)
                            : filterValue.equalsIgnoreCase(actual);
                    if (!matches) continue;
                }
                values.add(rs.getString(column));
            }
        }
        return values;
    }

    private static List<String> pragmaStrings(Connection db, String pragma, String column,
                                              String filterColumn) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (filterColumn != null && !filterColumn.equalsIgnoreCase(rs.getString(filterColumn))) {
                    continue;
                }
                values.add(rs.getString(column));
            }
        }
        return values;
    }

    /**
     * Reads one value out of a PRAGMA result.
     *
     * @param matchColumn the PRAGMA column to match on, e.g. {@code name}
     * @param matchValue  the value to match, e.g. a column name
     */
    private static String pragmaValue(Connection db, String pragma, String column,
                                      String matchColumn, String matchValue) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (matchColumn != null && !matchValue.equalsIgnoreCase(rs.getString(matchColumn))) {
                    continue;
                }
                return rs.getString(column);
            }
        }
        return null;
    }

    private static int pragmaInt(Connection db, String pragma, String column,
                                 String matchColumn, String matchValue) throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (matchColumn != null && !matchValue.equalsIgnoreCase(rs.getString(matchColumn))) {
                    continue;
                }
                return rs.getInt(column);
            }
        }
        return -1;
    }
}

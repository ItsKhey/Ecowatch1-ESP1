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

/**
 * Executes MIGRATION_3_4 against a real SQLite engine and asserts the result is
 * exactly what Room validates on open.
 *
 * <p>Room compares the live schema against the {@code @Entity} declarations on
 * every database open, so a column the entity declares but the migration never
 * adds aborts the upgrade with
 * {@code IllegalStateException: Migration didn't properly handle} and crashes the
 * app at startup. That only shows up on a device, which is why this runs the
 * statements against a real database.
 *
 * <p>The table here is the only copy of a cleaner's history, so the upgrade has
 * to add a column to it and leave every existing row exactly as it was.
 */
public class WasteScaleMigrationTest {

    private static final String TASK_HISTORY = "task_history";

    /** The exact statement from PersonnelDatabase.MIGRATION_3_4. */
    private static final String MIGRATION_3_4 =
            "ALTER TABLE `task_history` ADD COLUMN `wasteScale` TEXT";

    /**
     * The columns the table had at version 3, in the order the entity declared
     * them. Taken from the entity as it stood before the scale was added.
     */
    private static final List<String> V3_COLUMNS = List.of(
            "assignmentId", "personnelId", "reportId", "clusterId", "status",
            "reportStatus", "reportTitle", "reportCategory", "reportDescription",
            "reportPriority", "reportLatitude", "reportLongitude", "reportImageUrl",
            "beforeCount", "afterCount", "assignedAt", "acceptedAt", "startedAt",
            "completedAt", "createdAt", "updatedAt", "completionNotes", "syncedAt");

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    public void migrationAddsExactlyTheScaleColumn() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createVersion3Table(db);
            applyMigration(db);

            List<String> expected = new ArrayList<>(V3_COLUMNS);
            expected.add("wasteScale");

            assertEquals("column set must match the @Entity declaration exactly",
                    new LinkedHashSet<>(expected),
                    new LinkedHashSet<>(columnNames(db, TASK_HISTORY)));
        }
    }

    @Test
    public void scaleColumnIsNullableText() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createVersion3Table(db);
            applyMigration(db);

            assertEquals("TEXT", columnType(db, TASK_HISTORY, "wasteScale"));
            assertTrue("wasteScale must stay nullable",
                    !notNull(db, TASK_HISTORY, "wasteScale"));
        }
    }

    /**
     * A cleaner's saved history is the only copy of it on the device, so the
     * upgrade must not disturb a single row.
     */
    @Test
    public void migrationPreservesExistingRows() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            createVersion3Table(db);
            st.execute("INSERT INTO `task_history` "
                    + "(`assignmentId`,`personnelId`,`status`,`reportTitle`,"
                    + "`beforeCount`,`afterCount`,`syncedAt`) "
                    + "VALUES ('ASG-1','PERS-0001','COMPLETED','Overflowing garbage',2,3,100)");
            st.execute("INSERT INTO `task_history` "
                    + "(`assignmentId`,`personnelId`,`status`,`reportTitle`,"
                    + "`beforeCount`,`afterCount`,`syncedAt`) "
                    + "VALUES ('ASG-2','PERS-0001','ASSIGNED','Bottles by the drain',0,0,200)");

            applyMigration(db);

            assertEquals("both rows must survive the upgrade",
                    2, countRows(db, "SELECT COUNT(*) FROM task_history"));
            assertEquals("Overflowing garbage",
                    queryString(db, "SELECT reportTitle FROM task_history "
                            + "WHERE assignmentId = 'ASG-1'"));
            assertEquals("100",
                    queryString(db, "SELECT syncedAt FROM task_history "
                            + "WHERE assignmentId = 'ASG-1'"));
        }
    }

    /**
     * Rows written before the upgrade carry no scale. A missing scale has to
     * read as the smallest one, which is the only size those jobs could have
     * been, so the column is left null rather than back-filled with a guess.
     */
    @Test
    public void preUpgradeRowsCarryNoScale() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            createVersion3Table(db);
            st.execute("INSERT INTO `task_history` "
                    + "(`assignmentId`,`personnelId`,`status`,`beforeCount`,`afterCount`,`syncedAt`) "
                    + "VALUES ('ASG-1','PERS-0001','COMPLETED',1,1,100)");

            applyMigration(db);

            assertEquals(null, queryString(db,
                    "SELECT wasteScale FROM task_history WHERE assignmentId = 'ASG-1'"));
        }
    }

    /**
     * A device interrupted part-way through the upgrade can be left with the
     * column already in place. The migration must then converge rather than
     * abort on "duplicate column name" and lock the cleaner out of the app.
     */
    @Test
    public void migrationIsRerunnableAfterAnInterruptedUpgrade() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createVersion3Table(db);
            applyMigration(db);
            applyMigration(db);
            applyMigration(db);

            assertEquals(V3_COLUMNS.size() + 1, columnNames(db, TASK_HISTORY).size());
        }
    }

    /**
     * The upgrade adds a column to one table and nothing else, so it cannot
     * disturb the notification cache or the biometric registrations.
     */
    @Test
    public void migrationTouchesNoOtherTable() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            createVersion3Table(db);
            st.execute("CREATE TABLE `biometric_settings` (`personnelId` TEXT NOT NULL, "
                    + "`username` TEXT, PRIMARY KEY(`personnelId`))");
            st.execute("INSERT INTO `biometric_settings` VALUES ('PERS-0001','cleaner')");

            applyMigration(db);

            assertEquals(List.of("biometric_settings", TASK_HISTORY), tableNames(db));
            assertEquals("cleaner",
                    queryString(db, "SELECT username FROM biometric_settings"));
        }
    }

    /** The table as it existed at version 3, with one row's worth of history. */
    private static void createVersion3Table(Connection db) throws SQLException {
        try (Statement st = db.createStatement()) {
            st.execute("CREATE TABLE `task_history` ("
                    + "`assignmentId` TEXT NOT NULL, "
                    + "`personnelId` TEXT, "
                    + "`reportId` TEXT, "
                    + "`clusterId` TEXT, "
                    + "`status` TEXT, "
                    + "`reportStatus` TEXT, "
                    + "`reportTitle` TEXT, "
                    + "`reportCategory` TEXT, "
                    + "`reportDescription` TEXT, "
                    + "`reportPriority` TEXT, "
                    + "`reportLatitude` REAL, "
                    + "`reportLongitude` REAL, "
                    + "`reportImageUrl` TEXT, "
                    + "`beforeCount` INTEGER NOT NULL, "
                    + "`afterCount` INTEGER NOT NULL, "
                    + "`assignedAt` TEXT, "
                    + "`acceptedAt` TEXT, "
                    + "`startedAt` TEXT, "
                    + "`completedAt` TEXT, "
                    + "`createdAt` TEXT, "
                    + "`updatedAt` TEXT, "
                    + "`completionNotes` TEXT, "
                    + "`syncedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`assignmentId`))");
            st.execute("CREATE INDEX IF NOT EXISTS index_task_history_personnelId_status "
                    + "ON task_history(personnelId, status)");
            st.execute("CREATE INDEX IF NOT EXISTS index_task_history_personnelId_completedAt "
                    + "ON task_history(personnelId, completedAt)");
        }
    }

    /** The migration as the database runs it, guard included. */
    private static void applyMigration(Connection db) throws SQLException {
        if (columnNames(db, TASK_HISTORY).contains("wasteScale")) {
            return;
        }
        try (Statement st = db.createStatement()) {
            st.execute(MIGRATION_3_4);
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
        List<String> values = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`" + table + "`)")) {
            while (rs.next()) values.add(rs.getString("name"));
        }
        return values;
    }

    private static String columnType(Connection db, String table, String column)
            throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`" + table + "`)")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return rs.getString("type");
                }
            }
        }
        return null;
    }

    private static boolean notNull(Connection db, String table, String column)
            throws SQLException {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(`" + table + "`)")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return rs.getInt("notnull") == 1;
                }
            }
        }
        return false;
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
}

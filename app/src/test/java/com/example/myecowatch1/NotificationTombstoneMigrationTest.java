package com.example.myecowatch1;

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
 * Executes MIGRATION_28_29 against a real SQLite engine and asserts the result
 * is exactly what Room will validate on open.
 *
 * <p>Room validates the live schema against the {@code @Entity} declaration on
 * every database open. A migration that creates a table/index the entity does
 * not declare aborts the upgrade with
 * {@code IllegalStateException: Migration didn't properly handle}, which
 * crashes the app at startup — the exact failure mode that hit the history
 * tombstones earlier.
 *
 * <p>This runs against a real database because an index that is present after
 * the migration but absent from the entity is invisible to a build.
 */
public class NotificationTombstoneMigrationTest {

    private static final String TABLE = "notification_tombstones";
    private static final String INDEX = "index_notification_tombstones_residentId";

    /**
     * The exact statements from AppDatabase.MIGRATION_28_29. Kept literal so this
     * test fails if the migration is edited into an invalid state.
     */
    private static final String[] MIGRATION_28_29 = {
            "CREATE TABLE IF NOT EXISTS `notification_tombstones` ("
                    + "`mongoId` TEXT NOT NULL, "
                    + "`residentId` TEXT, "
                    + "`deletedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`mongoId`))",
            "CREATE INDEX IF NOT EXISTS `index_notification_tombstones_residentId` "
                    + "ON notification_tombstones(residentId)"
    };

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    public void migrationProducesExactlyTheSchemaRoomExpects() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);

            assertEquals(
                    "column set must match the @Entity declaration exactly",
                    new LinkedHashSet<>(List.of("mongoId", "residentId", "deletedAt")),
                    new LinkedHashSet<>(columnNames(db)));

            assertEquals("TEXT", columnType(db, "mongoId"));
            assertEquals("TEXT", columnType(db, "residentId"));
            assertEquals("INTEGER", columnType(db, "deletedAt"));

            assertTrue("mongoId must be NOT NULL", notNull(db, "mongoId"));
            assertTrue("deletedAt must be NOT NULL", notNull(db, "deletedAt"));
            assertTrue("residentId must stay nullable", !notNull(db, "residentId"));

            assertEquals("mongoId is the sole primary key at position 1",
                    1, primaryKeyPosition(db, "mongoId"));
            assertEquals("residentId must not be part of the primary key",
                    0, primaryKeyPosition(db, "residentId"));

            // Room compares the full index set, so an EXTRA index is a failure
            // just as much as a missing one.
            Set<String> indices = userIndices(db);
            assertEquals("index set must match the @Entity indices exactly",
                    new LinkedHashSet<>(List.of(INDEX)), indices);

            assertEquals("index must cover exactly residentId",
                    List.of("residentId"), indexColumns(db, INDEX));
            assertTrue("index must not be UNIQUE", !isUnique(db, INDEX));
        }
    }

    /**
     * The migration must not disturb notification data the user already has. A
     * hard delete is meaningless if the upgrade wipes the table it reads.
     */
    @Test
    public void migrationPreservesExistingNotifications() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement st = db.createStatement()) {
            st.execute("CREATE TABLE `notifications` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`mongoId` TEXT, "
                    + "`residentId` TEXT, "
                    + "`isDeleted` INTEGER NOT NULL)");
            st.execute("INSERT INTO `notifications` "
                    + "(`mongoId`,`residentId`,`isDeleted`) VALUES ('42','RES-001',1)");

            applyMigration(db);

            assertEquals("the trashed row must survive the upgrade",
                    1, countRows(db, "notifications"));
            assertEquals("42", queryString(db, "SELECT mongoId FROM notifications"));
        }
    }

    /**
     * The failing upgrade rolled back, leaving the database on version 28, so the
     * migration is retried on the next launch. It must be re-runnable and must
     * converge on the same schema rather than erroring.
     */
    @Test
    public void migrationIsRerunnableAfterAFailedUpgrade() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);
            applyMigration(db);
            applyMigration(db);

            assertEquals(new LinkedHashSet<>(List.of("mongoId", "residentId", "deletedAt")),
                    new LinkedHashSet<>(columnNames(db)));
            assertEquals(new LinkedHashSet<>(List.of(INDEX)), userIndices(db));
        }
    }

    /** The table the migration must NOT create is absent, so no side effects. */
    @Test
    public void migrationCreatesNoOtherTables() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);

            assertEquals("only notification_tombstones should exist",
                    List.of(TABLE), tableNames(db));
        }
    }

    private static void applyMigration(Connection db) throws Exception {
        try (Statement st = db.createStatement()) {
            for (String sql : MIGRATION_28_29) {
                st.execute(sql);
            }
        }
    }

    private static List<String> tableNames(Connection db) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' "
                             + "AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    private static List<String> columnNames(Connection db) throws Exception {
        return new ArrayList<>(pragmaStrings(db, "PRAGMA table_info(`" + TABLE + "`)", "name"));
    }

    private static String columnType(Connection db, String column) throws Exception {
        return pragmaValue(db, "PRAGMA table_info(`" + TABLE + "`)", "type", "name", column);
    }

    private static boolean notNull(Connection db, String column) throws Exception {
        return pragmaInt(db, "PRAGMA table_info(`" + TABLE + "`)", "notnull", "name", column) == 1;
    }

    private static int primaryKeyPosition(Connection db, String column) throws Exception {
        return pragmaInt(db, "PRAGMA table_info(`" + TABLE + "`)", "pk", "name", column);
    }

    /** Indices created by CREATE INDEX (origin 'c'), which is what Room reads. */
    private static Set<String> userIndices(Connection db) throws Exception {
        return new LinkedHashSet<>(
                pragmaStrings(db, "PRAGMA index_list(`" + TABLE + "`)", "name", "origin", "c"));
    }

    private static List<String> indexColumns(Connection db, String index) throws Exception {
        return pragmaStrings(db, "PRAGMA index_info(`" + index + "`)", "name");
    }

    private static boolean isUnique(Connection db, String index) throws Exception {
        return pragmaInt(db, "PRAGMA index_list(`" + TABLE + "`)", "unique", "name", index) == 1;
    }

    private static int countRows(Connection db, String table) throws Exception {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static String queryString(Connection db, String sql) throws Exception {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static List<String> pragmaStrings(Connection db, String pragma, String column)
            throws Exception {
        return pragmaStrings(db, pragma, column, null, null);
    }

    private static List<String> pragmaStrings(Connection db, String pragma, String column,
                                              String filterColumn, String filterValue)
            throws Exception {
        List<String> values = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (filterColumn != null
                        && !filterValue.equalsIgnoreCase(rs.getString(filterColumn))) {
                    continue;
                }
                values.add(rs.getString(column));
            }
        }
        return values;
    }

    private static String pragmaValue(Connection db, String pragma, String column,
                                      String filterColumn, String filterValue) throws Exception {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (filterColumn != null
                        && !filterValue.equalsIgnoreCase(rs.getString(filterColumn))) {
                    continue;
                }
                return rs.getString(column);
            }
        }
        return null;
    }

    private static int pragmaInt(Connection db, String pragma, String column,
                                 String filterColumn, String filterValue) throws Exception {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (filterColumn != null
                        && !filterValue.equalsIgnoreCase(rs.getString(filterColumn))) {
                    continue;
                }
                return rs.getInt(column);
            }
        }
        return -1;
    }
}

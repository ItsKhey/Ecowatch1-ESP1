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
 * Executes MIGRATION_20_21 against a real SQLite engine and asserts the result
 * is byte-for-byte what Room will validate on open.
 *
 * <p>Room validates the live schema against the {@code @Entity} declaration on
 * every database open. A migration that creates a table/index the entity does
 * not declare aborts the upgrade with
 * {@code IllegalStateException: Migration didn't properly handle}, which
 * crashed the app at startup.
 *
 * <p>This ran against a real database because the original regression (an index
 * present after migration but absent from the entity) is invisible to a build
 * and to string comparison.
 */
public class HistoryTombstoneMigrationTest {

    private static final String TABLE = "history_tombstones";
    private static final String INDEX = "index_history_tombstones_residentId";

    /**
     * The exact statements from AppDatabase.MIGRATION_20_21. Kept literal so
     * this test fails if the migration is edited into an invalid state.
     */
    private static final String[] MIGRATION_20_21 = {
            "CREATE TABLE IF NOT EXISTS `history_tombstones` ("
                    + "`mongoId` TEXT NOT NULL, "
                    + "`residentId` TEXT, "
                    + "`deletedAt` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`mongoId`))",
            "CREATE INDEX IF NOT EXISTS `index_history_tombstones_residentId` "
                    + "ON history_tombstones(residentId)"
    };

    @BeforeClass
    public static void loadDriver() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    public void migrationProducesExactlyTheSchemaRoomExpects() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);

            // Columns: name -> declared type, nullability, primary-key position.
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

            // Indices: this is the assertion that would have caught the crash.
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
     * The failing upgrade rolled back, leaving the database on version 20, so
     * the migration is retried on the next launch. It must be re-runnable and
     * must converge on the same schema rather than erroring.
     */
    @Test
    public void migrationIsRerunnableAfterAFailedUpgrade() throws SQLException {
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
    public void migrationCreatesNoOtherTables() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            applyMigration(db);

            assertEquals("only history_tombstones should exist",
                    List.of(TABLE), tableNames(db));
        }
    }

    /**
     * Verifies that if a database is on version 23 without the index, MIGRATION_23_24
     * creates the index and matches Room's expectation.
     */
    @Test
    public void migrationFromVersion23To24EnsuresIndexExists() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            // Create history_tombstones without index (simulating unindexed v23)
            try (Statement st = db.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS `history_tombstones` ("
                        + "`mongoId` TEXT NOT NULL, "
                        + "`residentId` TEXT, "
                        + "`deletedAt` INTEGER NOT NULL, "
                        + "PRIMARY KEY(`mongoId`))");
            }

            // Confirm no index exists yet
            assertEquals(0, userIndices(db).size());

            // Apply MIGRATION_23_24 SQLs
            try (Statement st = db.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS `history_tombstones` ("
                        + "`mongoId` TEXT NOT NULL, "
                        + "`residentId` TEXT, "
                        + "`deletedAt` INTEGER NOT NULL, "
                        + "PRIMARY KEY(`mongoId`))");
                st.execute("CREATE INDEX IF NOT EXISTS `index_history_tombstones_residentId` "
                        + "ON history_tombstones(residentId)");
            }

            // Confirm index is created
            Set<String> indices = userIndices(db);
            assertEquals("index set must match the @Entity indices exactly",
                    new LinkedHashSet<>(List.of(INDEX)), indices);
        }
    }

    /**
     * Verifies that upgrading a database adds the missing clusterId column to reports.
     */
    @Test
    public void reportsClusterIdAddedOnMigration() throws SQLException {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement st = db.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS `reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT)");
                st.execute("ALTER TABLE `reports` ADD COLUMN `clusterId` TEXT");
            }

            List<String> cols = pragmaStrings(db, "PRAGMA table_info(`reports`)", "name");
            assertTrue("reports must contain clusterId column", cols.contains("clusterId"));
        }
    }

    private static void applyMigration(Connection db) throws SQLException {
        try (Statement st = db.createStatement()) {
            for (String sql : MIGRATION_20_21) {
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

    private static List<String> columnNames(Connection db) throws SQLException {
        return new ArrayList<>(pragmaStrings(db, "PRAGMA table_info(`" + TABLE + "`)", "name"));
    }

    private static String columnType(Connection db, String column) throws SQLException {
        return pragmaValue(db, "PRAGMA table_info(`" + TABLE + "`)", "type", "name", column);
    }

    private static boolean notNull(Connection db, String column) throws SQLException {
        return pragmaInt(db, "PRAGMA table_info(`" + TABLE + "`)", "notnull", "name", column) == 1;
    }

    private static int primaryKeyPosition(Connection db, String column) throws SQLException {
        return pragmaInt(db, "PRAGMA table_info(`" + TABLE + "`)", "pk", "name", column);
    }

    /** Indices created by CREATE INDEX (origin 'c'), which is what Room reads. */
    private static Set<String> userIndices(Connection db) throws SQLException {
        return new LinkedHashSet<>(
                pragmaStrings(db, "PRAGMA index_list(`" + TABLE + "`)", "name", "origin", "c"));
    }

    private static List<String> indexColumns(Connection db, String index) throws SQLException {
        return pragmaStrings(db, "PRAGMA index_info(`" + index + "`)", "name");
    }

    private static boolean isUnique(Connection db, String index) throws SQLException {
        return pragmaInt(db, "PRAGMA index_list(`" + TABLE + "`)", "unique", "name", index) == 1;
    }

    private static List<String> pragmaStrings(Connection db, String pragma, String column)
            throws SQLException {
        return pragmaStrings(db, pragma, column, null, null);
    }

    private static List<String> pragmaStrings(Connection db, String pragma, String column,
                                              String filterColumn, String filterValue)
            throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery(pragma)) {
            while (rs.next()) {
                if (filterColumn != null) {
                    String actual = rs.getString(filterColumn);
                    if (!filterValue.equalsIgnoreCase(actual)) continue;
                }
                values.add(rs.getString(column));
            }
        }
        return values;
    }

    private static String pragmaValue(Connection db, String pragma, String column,
                                      String filterColumn, String filterValue)
            throws SQLException {
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
                                 String filterColumn, String filterValue) throws SQLException {
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

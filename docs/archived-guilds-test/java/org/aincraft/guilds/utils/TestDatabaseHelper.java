package org.aincraft.guilds.utils;

import org.aincraft.guilds.config.TestConfig;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Database utility class for test environment
 * Provides methods for database setup, cleanup, and verification in tests
 */
public final class TestDatabaseHelper {

    // Private constructor to prevent instantiation
    private TestDatabaseHelper() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    // Connection timeout in seconds
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;

    /**
     * Test if database connection is available
     */
    public static boolean isDatabaseAvailable(@NotNull DataSource dataSource) {
        try {
            Connection connection = dataSource.getConnection();
            boolean isValid = connection.isValid(CONNECTION_TIMEOUT_SECONDS);
            connection.close();
            return isValid;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Wait for database to become available
     */
    public static boolean waitForDatabase(@NotNull DataSource dataSource, long timeoutMillis) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (isDatabaseAvailable(dataSource)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }

        return false;
    }

    /**
     * Clean up all test data from database
     */
    public static void cleanupTestData(@NotNull Connection connection, @NotNull TestConfig testConfig) throws SQLException {
        Objects.requireNonNull(connection, "Connection cannot be null");
        Objects.requireNonNull(testConfig, "TestConfig cannot be null");

        String prefix = testConfig.getPlugin().getDatabasePrefix();

        // Disable foreign key constraints temporarily (if supported)
        disableConstraints(connection, prefix);

        // Clean up data in reverse order of creation to respect dependencies
        cleanupTable(connection, prefix + "town_blocks");
        cleanupTable(connection, prefix + "towns");
        cleanupTable(connection, prefix + "residents");
        cleanupTable(connection, prefix + "permissions");
        cleanupTable(connection, prefix + "schema_migrations");

        // Re-enable constraints
        enableConstraints(connection, prefix);

        connection.commit();
    }

    /**
     * Clean up a specific table
     */
    private static void cleanupTable(@NotNull Connection connection, @NotNull String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + tableName);
        } catch (SQLException e) {
            // Table might not exist, log and continue
            System.err.println("Failed to clean up table " + tableName + ": " + e.getMessage());
        }
    }

    /**
     * Disable foreign key constraints (database-specific)
     */
    private static void disableConstraints(@NotNull Connection connection, @NotNull String prefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // MySQL/MariaDB
            try {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                return;
            } catch (SQLException ignored) {}

            // PostgreSQL
            try {
                statement.execute("SET session_replication_role = replica");
                return;
            } catch (SQLException ignored) {}

            // SQLite
            try {
                statement.execute("PRAGMA foreign_keys = OFF");
                return;
            } catch (SQLException ignored) {}

            // H2
            try {
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
                return;
            } catch (SQLException ignored) {}

        } catch (SQLException e) {
            System.err.println("Failed to disable constraints: " + e.getMessage());
        }
    }

    /**
     * Enable foreign key constraints (database-specific)
     */
    private static void enableConstraints(@NotNull Connection connection, @NotNull String prefix) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // MySQL/MariaDB
            try {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                return;
            } catch (SQLException ignored) {}

            // PostgreSQL
            try {
                statement.execute("SET session_replication_role = DEFAULT");
                return;
            } catch (SQLException ignored) {}

            // SQLite
            try {
                statement.execute("PRAGMA foreign_keys = ON");
                return;
            } catch (SQLException ignored) {}

            // H2
            try {
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
                return;
            } catch (SQLException ignored) {}

        } catch (SQLException e) {
            System.err.println("Failed to enable constraints: " + e.getMessage());
        }
    }

    /**
     * Count rows in a table
     */
    public static int countRows(@NotNull Connection connection, @NotNull String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;
        }
    }

    /**
     * Check if table exists
     */
    public static boolean tableExists(@NotNull Connection connection, @NotNull String tableName) throws SQLException {
        // Try different approaches for different databases
        String[] sqlQueries = {
            "SELECT 1 FROM " + tableName + " WHERE 1=0", // Most databases
            "SELECT 1 FROM information_schema.tables WHERE table_name = '" + tableName + "'", // Some databases
            "SELECT 1 FROM sys.tables WHERE name = '" + tableName + "'" // SQL Server
        };

        for (String sql : sqlQueries) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeQuery();
                return true;
            } catch (SQLException e) {
                // Try next query
            }
        }

        return false;
    }

    /**
     * Get list of table names
     */
    @NotNull
    public static List<String> getTableNames(@NotNull Connection connection, @NotNull String prefix) throws SQLException {
        List<String> tables = new ArrayList<>();

        // Try different approaches for different databases
        String[] sqlQueries = {
            "SELECT table_name FROM information_schema.tables WHERE table_name LIKE '" + prefix + "%'",
            "SELECT name FROM sys.tables WHERE name LIKE '" + prefix + "%'"
        };

        for (String sql : sqlQueries) {
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    tables.add(resultSet.getString(1));
                }
                break; // Found working query
            } catch (SQLException e) {
                // Try next query
            }
        }

        return tables;
    }

    /**
     * Execute a SQL script
     */
    public static void executeScript(@NotNull Connection connection, @NotNull String sqlScript) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            String[] statements = sqlScript.split(";");

            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }

    /**
     * Create a test data set
     */
    public static void createTestData(@NotNull Connection connection, @NotNull TestConfig testConfig) throws SQLException {
        String prefix = testConfig.getPlugin().getDatabasePrefix();

        // Create test town
        String createTownSql = String.format(
            "INSERT INTO %s (id, name, mayor_uuid, founded_at, balance, resident_count) VALUES (?, ?, ?, ?, ?, ?)",
            prefix + "towns"
        );

        try (PreparedStatement statement = connection.prepareStatement(createTownSql)) {
            statement.setString(1, testConfig.getData().getTown().getDefaultName());
            statement.setString(2, testConfig.getData().getTown().getDefaultName());
            statement.setString(3, java.util.UUID.randomUUID().toString());
            statement.setTimestamp(4, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
            statement.setDouble(5, 10000.0);
            statement.setInt(6, testConfig.getData().getTown().getDefaultResidents());
            statement.executeUpdate();
        }
    }

    /**
     * Verify database state after test
     */
    public static DatabaseVerificationResult verifyDatabaseState(@NotNull Connection connection, @NotNull TestConfig testConfig) throws SQLException {
        DatabaseVerificationResult result = new DatabaseVerificationResult();
        String prefix = testConfig.getPlugin().getDatabasePrefix();

        // Check if tables exist
        List<String> expectedTables = List.of(
            prefix + "towns",
            prefix + "town_blocks",
            prefix + "residents",
            prefix + "permissions"
        );

        for (String tableName : expectedTables) {
            boolean exists = tableExists(connection, tableName);
            result.addTableCheck(tableName, exists);

            if (exists) {
                int count = countRows(connection, tableName);
                result.addRowCount(tableName, count);
            }
        }

        return result;
    }

    /**
     * Result of database verification
     */
    public static class DatabaseVerificationResult {
        private final List<TableCheck> tableChecks = new ArrayList<>();

        public void addTableCheck(@NotNull String tableName, boolean exists) {
            tableChecks.add(new TableCheck(tableName, exists, -1));
        }

        public void addRowCount(@NotNull String tableName, int count) {
            for (TableCheck check : tableChecks) {
                if (check.tableName.equals(tableName)) {
                    check.rowCount = count;
                    break;
                }
            }
        }

        public boolean allTablesExist() {
            return tableChecks.stream().allMatch(check -> check.exists);
        }

        public int getTotalRowCount() {
            return tableChecks.stream().mapToInt(check -> check.rowCount).sum();
        }

        public List<TableCheck> getTableChecks() {
            return new ArrayList<>(tableChecks);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("DatabaseVerificationResult{\n");
            for (TableCheck check : tableChecks) {
                sb.append("  ").append(check).append("\n");
            }
            sb.append("}");
            return sb.toString();
        }

        public static class TableCheck {
            public final String tableName;
            public final boolean exists;
            public int rowCount;

            public TableCheck(@NotNull String tableName, boolean exists, int rowCount) {
                this.tableName = tableName;
                this.exists = exists;
                this.rowCount = rowCount;
            }

            @Override
            public String toString() {
                return String.format("TableCheck{name='%s', exists=%s, rows=%d}",
                    tableName, exists, rowCount);
            }
        }
    }
}
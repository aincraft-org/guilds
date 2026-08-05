package org.aincraft.towny.database.migration;

import org.aincraft.towny.models.TownyPermission;

import java.sql.*;
import java.util.*;

/**
 * Database migration utility for converting legacy bitwise permission flags to the new enum system
 */
public class PermissionMigration {

    /**
     * Create permission mapping table for legacy conversion
     */
    public static void createPermissionMappingTable(Connection connection) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS permission_flag_mapping (
                permission_name TEXT PRIMARY KEY,
                legacy_bitmask INTEGER NOT NULL,
                category TEXT NOT NULL,
                description TEXT
            )
        """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }

        // Populate mapping table
        populatePermissionMapping(connection);
    }

    /**
     * Populate the permission mapping table with current TownyPermission enum values
     */
    private static void populatePermissionMapping(Connection connection) throws SQLException {
        String sql = "INSERT OR REPLACE INTO permission_flag_mapping (permission_name, legacy_bitmask, category, description) VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TownyPermission permission : TownyPermission.values()) {
                statement.setString(1, permission.name());
                statement.setInt(2, permission.getLegacyBitwiseValue());
                statement.setString(3, permission.getCategory().name());
                statement.setString(4, permission.getDescription());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Add temporary columns for enum-based storage
     */
    public static void addEnumColumns(Connection connection) throws SQLException {
        String sql = """
            ALTER TABLE permissions ADD COLUMN permissions_enum TEXT DEFAULT NULL;
            ALTER TABLE permissions ADD COLUMN explicit_denials TEXT DEFAULT NULL;
            """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * Convert existing integer flags to enum representation
     */
    public static int convertLegacyFlags(Connection connection) throws SQLException {
        // First ensure mapping table exists
        createPermissionMappingTable(connection);

        // Add enum columns if they don't exist
        addEnumColumns(connection);

        // Convert existing permissions
        String sql = """
            UPDATE permissions
            SET permissions_enum = (
                SELECT GROUP_CONCAT(permission_name)
                FROM permission_flag_mapping
                WHERE (permissions_flags & legacy_bitmask) != 0
            )
            WHERE permissions_flags IS NOT NULL AND permissions_flags != 0
            """;

        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    /**
     * Validate that all legacy flags were converted successfully
     */
    public static boolean validateConversion(Connection connection) throws SQLException {
        String sql = "SELECT COUNT(*) FROM permissions WHERE permissions_flags IS NOT NULL AND permissions_flags != 0 AND (permissions_enum IS NULL OR permissions_enum = '')";

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            if (rs.next()) {
                int unconvertedCount = rs.getInt(1);
                System.out.println("Unconverted permissions: " + unconvertedCount);
                return unconvertedCount == 0;
            }
        }

        return false;
    }

    /**
     * Test conversion by converting a few sample records
     */
    public static void testConversion(Connection connection) throws SQLException {
        // Create test table with sample data
        String createTestSql = """
            CREATE TEMPORARY TABLE permission_test AS
            SELECT * FROM permissions
            WHERE permissions_flags IS NOT NULL AND permissions_flags != 0
            LIMIT 5
            """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTestSql);

            // Show before conversion
            System.out.println("=== BEFORE CONVERSION ===");
            ResultSet beforeRs = statement.executeQuery("SELECT id, permissions_flags FROM permission_test");
            while (beforeRs.next()) {
                System.out.println("ID: " + beforeRs.getString("id") + ", Flags: " + beforeRs.getInt("permissions_flags"));
            }
            beforeRs.close();

            // Convert test data
            String convertSql = """
                UPDATE permission_test
                SET permissions_enum = (
                    SELECT GROUP_CONCAT(permission_name)
                    FROM permission_flag_mapping
                    WHERE (permissions_flags & legacy_bitmask) != 0
                )
                """;
            statement.execute(convertSql);

            // Show after conversion
            System.out.println("\n=== AFTER CONVERSION ===");
            ResultSet afterRs = statement.executeQuery("SELECT id, permissions_flags, permissions_enum FROM permission_test");
            while (afterRs.next()) {
                System.out.println("ID: " + afterRs.getString("id") +
                                 ", Flags: " + afterRs.getInt("permissions_flags") +
                                 ", Enum: " + afterRs.getString("permissions_enum"));
            }
            afterRs.close();

            // Clean up
            statement.execute("DROP TABLE permission_test");
        }
    }

    /**
     * Rollback migration by removing enum columns
     */
    public static void rollback(Connection connection) throws SQLException {
        String sql = """
            ALTER TABLE permissions DROP COLUMN permissions_enum;
            ALTER TABLE permissions DROP COLUMN explicit_denials;
            DROP TABLE IF EXISTS permission_flag_mapping;
            """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * Get statistics about the migration
     */
    public static MigrationStats getMigrationStats(Connection connection) throws SQLException {
        String sql = """
            SELECT
                COUNT(*) as total_permissions,
                COUNT(CASE WHEN permissions_flags IS NOT NULL AND permissions_flags != 0 THEN 1 END) as legacy_flags,
                COUNT(CASE WHEN permissions_enum IS NOT NULL THEN 1 END) as enum_converted,
                COUNT(CASE WHEN permissions_enum IS NULL OR permissions_enum = '' THEN 1 END) as missing_enum
            FROM permissions
            """;

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {

            if (rs.next()) {
                return new MigrationStats(
                    rs.getInt("total_permissions"),
                    rs.getInt("legacy_flags"),
                    rs.getInt("enum_converted"),
                    rs.getInt("missing_enum")
                );
            }
        }

        return new MigrationStats(0, 0, 0, 0);
    }

    /**
     * Migration statistics
     */
    public static class MigrationStats {
        private final int totalPermissions;
        private final int legacyFlags;
        private final int enumConverted;
        private final int missingEnum;

        public MigrationStats(int totalPermissions, int legacyFlags, int enumConverted, int missingEnum) {
            this.totalPermissions = totalPermissions;
            this.legacyFlags = legacyFlags;
            this.enumConverted = enumConverted;
            this.missingEnum = missingEnum;
        }

        public int getTotalPermissions() { return totalPermissions; }
        public int getLegacyFlags() { return legacyFlags; }
        public int getEnumConverted() { return enumConverted; }
        public int getMissingEnum() { return missingEnum; }

        public boolean isComplete() {
            return legacyFlags == enumConverted && missingEnum == 0;
        }

        @Override
        public String toString() {
            return String.format("MigrationStats{total=%d, legacy=%d, converted=%d, missing=%d, complete=%s}",
                               totalPermissions, legacyFlags, enumConverted, missingEnum, isComplete());
        }
    }

    /**
     * Main method for standalone testing
     */
    public static void main(String[] args) {
        String dbUrl = "jdbc:sqlite:towny.db";

        try (Connection connection = DriverManager.getConnection(dbUrl)) {
            System.out.println("=== PERMISSION MIGRATION UTILITY ===");

            // Show current stats
            MigrationStats beforeStats = getMigrationStats(connection);
            System.out.println("Before: " + beforeStats);

            // Test conversion on sample data
            System.out.println("\n=== TESTING CONVERSION ===");
            testConversion(connection);

            // Ask user if they want to proceed with full migration
            System.out.println("\nProceed with full migration? (y/n)");
            // In real usage, you would read user input here

            // Perform full migration
            System.out.println("\n=== PERFORMING FULL MIGRATION ===");
            int convertedRows = convertLegacyFlags(connection);
            System.out.println("Converted " + convertedRows + " permission records");

            // Validate conversion
            boolean isValid = validateConversion(connection);
            System.out.println("Validation passed: " + isValid);

            // Show final stats
            MigrationStats afterStats = getMigrationStats(connection);
            System.out.println("After: " + afterStats);

        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
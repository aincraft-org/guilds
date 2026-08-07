package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.models.GuildPermission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
     * Populate the permission mapping table with current GuildPermission enum values
     */
    private static void populatePermissionMapping(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO permission_flag_mapping (permission_name, legacy_bitmask, category, description)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (permission_name) DO UPDATE SET
                    legacy_bitmask = EXCLUDED.legacy_bitmask,
                    category = EXCLUDED.category,
                    description = EXCLUDED.description
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (GuildPermission permission : GuildPermission.values()) {
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
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permissions_enum TEXT DEFAULT NULL");
            statement.execute("ALTER TABLE permissions ADD COLUMN IF NOT EXISTS explicit_denials TEXT DEFAULT NULL");
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
                SELECT string_agg(permission_name, ',')
                FROM permission_flag_mapping
                WHERE (permissions_flags & legacy_bitmask) <> 0
            )
            WHERE permissions_flags IS NOT NULL AND permissions_flags <> 0
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
                    SELECT string_agg(permission_name, ',')
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

}
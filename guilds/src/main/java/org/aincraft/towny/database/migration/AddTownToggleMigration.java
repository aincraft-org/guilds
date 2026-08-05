package org.aincraft.towny.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Database migration to add town toggle system with dedicated boolean columns
 * Version 6: Adds pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled columns to towns table
 */
public class AddTownToggleMigration implements DatabaseMigration {

    private static final int VERSION = 6;
    private static final String DESCRIPTION = "Add town toggle system with dedicated boolean columns";

    /**
     * Get the migration version number
     */
    public int getVersion() {
        return VERSION;
    }

    /**
     * Get the migration description
     */
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * Execute the migration to add town toggle columns
     */
    public void migrate(Connection connection) throws SQLException {
        Statement statement = null;

        try {
            statement = connection.createStatement();

            // Add town toggle columns
            statement.execute("ALTER TABLE towns ADD COLUMN pvp_enabled BOOLEAN DEFAULT FALSE");
            statement.execute("ALTER TABLE towns ADD COLUMN fire_enabled BOOLEAN DEFAULT FALSE");
            statement.execute("ALTER TABLE towns ADD COLUMN explosions_enabled BOOLEAN DEFAULT FALSE");
            statement.execute("ALTER TABLE towns ADD COLUMN mobs_enabled BOOLEAN DEFAULT TRUE");
            statement.execute("ALTER TABLE towns ADD COLUMN public_enabled BOOLEAN DEFAULT FALSE");

            // Update existing towns with default values
            statement.execute(
                "UPDATE towns SET " +
                "pvp_enabled = FALSE, " +
                "fire_enabled = FALSE, " +
                "explosions_enabled = FALSE, " +
                "mobs_enabled = TRUE, " +
                "public_enabled = FALSE " +
                "WHERE pvp_enabled IS NULL"
            );

        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }

    /**
     * Check if this migration has already been applied
     */
    public boolean isApplied(Connection connection) throws SQLException {
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM schema_migrations WHERE version = " + VERSION
            );

            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }

            return false;

        } catch (SQLException e) {
            // If schema_migrations table doesn't exist, assume migration not applied
            if (e.getMessage().contains("Table") && e.getMessage().contains("doesn't exist")) {
                return false;
            }
            throw e;

        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        }
    }

    /**
     * Mark this migration as applied in the schema_migrations table
     */
    public void markAsApplied(Connection connection) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, VERSION);
            statement.setString(2, DESCRIPTION);
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));

            statement.executeUpdate();
        }
    }

    /**
     * Rollback this migration (remove town toggle columns)
     * Note: This is a destructive operation and should be used with caution
     */
    public void rollback(Connection connection) throws SQLException {
        Statement statement = null;

        try {
            statement = connection.createStatement();

            // Remove town toggle columns if they exist
            // Use IF EXISTS for databases that support it, or ignore errors
            try {
                statement.execute("ALTER TABLE towns DROP COLUMN pvp_enabled");
            } catch (SQLException e) {
                // Column might not exist, ignore
            }

            try {
                statement.execute("ALTER TABLE towns DROP COLUMN fire_enabled");
            } catch (SQLException e) {
                // Column might not exist, ignore
            }

            try {
                statement.execute("ALTER TABLE towns DROP COLUMN explosions_enabled");
            } catch (SQLException e) {
                // Column might not exist, ignore
            }

            try {
                statement.execute("ALTER TABLE towns DROP COLUMN mobs_enabled");
            } catch (SQLException e) {
                // Column might not exist, ignore
            }

            try {
                statement.execute("ALTER TABLE towns DROP COLUMN public_enabled");
            } catch (SQLException e) {
                // Column might not exist, ignore
            }

        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }

    /**
     * Validate the migration was applied correctly
     */
    public boolean validate(Connection connection) throws SQLException {
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE table_name = 'towns' AND " +
                "column_name IN ('pvp_enabled', 'fire_enabled', 'explosions_enabled', 'mobs_enabled', 'public_enabled')"
            );

            if (resultSet.next()) {
                return resultSet.getInt(1) == 5; // All 5 columns should exist
            }

            return false;

        } catch (SQLException e) {
            // INFORMATION_SCHEMA might not be available in all databases
            // Try alternative approach
            return validateBySelect(connection);

        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
        }
    }

    /**
     * Alternative validation method using SELECT statements
     */
    private boolean validateBySelect(Connection connection) throws SQLException {
        Statement statement = null;

        try {
            statement = connection.createStatement();

            // Try to select each column
            String[] columns = {"pvp_enabled", "fire_enabled", "explosions_enabled", "mobs_enabled", "public_enabled"};
            int existingColumns = 0;

            for (String column : columns) {
                try {
                    statement.executeQuery("SELECT " + column + " FROM towns LIMIT 1");
                    existingColumns++;
                } catch (SQLException e) {
                    // Column doesn't exist
                }
            }

            return existingColumns == 5;

        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }
}
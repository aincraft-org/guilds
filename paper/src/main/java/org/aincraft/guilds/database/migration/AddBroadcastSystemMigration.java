package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Database migration to add town broadcasting system
 * Version 8: Adds broadcast_messages table for town communication
 */
public class AddBroadcastSystemMigration implements DatabaseMigration {

    private static final int VERSION = 8;
    private static final String DESCRIPTION = "Add town broadcasting system with message types, priorities, and audience targeting";

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
     * Execute the migration to create broadcast tables
     */
    public void migrate(Connection connection) throws SQLException {
        Statement statement = null;

        try {
            statement = connection.createStatement();

            // Create broadcast_messages table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS broadcast_messages (
                    id TEXT PRIMARY KEY,
                    town_id TEXT NOT NULL,
                    message_type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    sender_uuid TEXT NOT NULL,
                    sender_name TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    expires_at TEXT,
                    is_active BOOLEAN DEFAULT TRUE,
                    priority INTEGER DEFAULT 1,
                    target_audience TEXT DEFAULT 'all',
                    FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE
                )
            """);

            // Create broadcast_read_status table to track which residents have read which messages
            statement.execute("""
                CREATE TABLE IF NOT EXISTS broadcast_read_status (
                    id TEXT PRIMARY KEY,
                    broadcast_id TEXT NOT NULL,
                    resident_uuid TEXT NOT NULL,
                    read_at TEXT NOT NULL,
                    FOREIGN KEY (broadcast_id) REFERENCES broadcast_messages(id) ON DELETE CASCADE,
                    FOREIGN KEY (resident_uuid) REFERENCES residents(uuid) ON DELETE CASCADE,
                    UNIQUE(broadcast_id, resident_uuid)
                )
            """);

            // Create indexes for performance
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_messages_town ON broadcast_messages(town_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_messages_type ON broadcast_messages(message_type)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_messages_active ON broadcast_messages(is_active)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_messages_priority ON broadcast_messages(priority)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_messages_expires ON broadcast_messages(expires_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_messages_audience ON broadcast_messages(target_audience)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_read_status_broadcast ON broadcast_read_status(broadcast_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_broadcast_read_status_resident ON broadcast_read_status(resident_uuid)");

            // Insert some default broadcast templates for existing towns
            insertDefaultBroadcastTemplates(statement);

        } finally {
            if (statement != null) {
                statement.close();
            }
        }
    }

    /**
     * Insert default broadcast templates for existing towns
     */
    private void insertDefaultBroadcastTemplates(Statement statement) throws SQLException {
        // This could be used to create welcome messages for existing towns
        // or provide templates for different types of broadcasts
        String currentTime = LocalDateTime.now().toString();

        // Note: We don't insert actual messages here, just ensure the structure exists
        // Actual messages will be created when needed by the service layer
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
     * Rollback this migration (remove broadcast tables)
     * Note: This is a destructive operation and should be used with caution
     */
    public void rollback(Connection connection) throws SQLException {
        Statement statement = null;

        try {
            statement = connection.createStatement();

            // Drop indexes first
            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_read_status_resident");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_read_status_broadcast");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_messages_audience");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_messages_expires");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_messages_priority");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_messages_active");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_messages_type");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            try {
                statement.execute("DROP INDEX IF EXISTS idx_broadcast_messages_town");
            } catch (SQLException e) {
                // Index might not exist, ignore
            }

            // Drop tables
            try {
                statement.execute("DROP TABLE broadcast_read_status");
            } catch (SQLException e) {
                // Table might not exist, ignore
            }

            try {
                statement.execute("DROP TABLE broadcast_messages");
            } catch (SQLException e) {
                // Table might not exist, ignore
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

            // Check if broadcast_messages table exists
            resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE table_name = 'broadcast_messages'"
            );

            if (resultSet.next() && resultSet.getInt(1) > 0) {
                resultSet.close();

                // Check if broadcast_read_status table exists
                resultSet = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE table_name = 'broadcast_read_status'"
                );

                return resultSet.next() && resultSet.getInt(1) > 0;
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
     * Alternative validation method for databases without INFORMATION_SCHEMA support
     */
    private boolean validateBySelect(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            // Try to query the tables - if they exist and have the expected structure, this should work
            statement.executeQuery("SELECT COUNT(*) FROM broadcast_messages LIMIT 1");
            statement.executeQuery("SELECT COUNT(*) FROM broadcast_read_status LIMIT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
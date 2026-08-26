package dev.mintychochip.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add guild quests system.
 * Version 13 — after AddAllianceSystemMigration (v12).
 */
public class AddQuestMigration implements DatabaseMigration {

    /** The version constant. */
    private static final int VERSION = 13;

    /**
     * Returns the version.
     * @return the result
     */
    @Override
    public int getVersion() {
        return VERSION;
    }

    /**
     * Returns the description.
     * @return the result
     */
    @Override
    public String getDescription() {
        return "Add guild quests system with quest tracking and rewards";
    }

    /**
     * Performs the migrate operation.
     * @param connection the connection
     * @throws SQLException if an error occurs
     */
    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Guild quests table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_quests (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    quest_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    target_amount INTEGER NOT NULL DEFAULT 1,
                    current_progress INTEGER NOT NULL DEFAULT 0,
                    tech_point_reward INTEGER NOT NULL DEFAULT 0,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    is_completed INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    completed_at TEXT,
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )
                """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_guild_quests_guild_id ON guild_quests(guild_id)");
        }
    }

    /**
     * Returns whether applied.
     * @param connection the connection
     * @return the result
     * @throws SQLException if an error occurs
     */
    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = " + VERSION)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Performs the mark as applied operation.
     * @param connection the connection
     * @throws SQLException if an error occurs
     */
    @Override
    public void markAsApplied(Connection connection) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, VERSION);
            ps.setString(2, getDescription());
            ps.setString(3, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }
}
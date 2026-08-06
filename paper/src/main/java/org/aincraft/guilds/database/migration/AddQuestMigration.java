package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add town quests system.
 * Version 13 — after AddAllianceSystemMigration (v12).
 */
public class AddQuestMigration implements DatabaseMigration {

    private static final int VERSION = 13;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add town quests system with quest tracking and rewards";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Town quests table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS town_quests (
                    id TEXT PRIMARY KEY,
                    town_id TEXT NOT NULL,
                    quest_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    target_amount INTEGER NOT NULL DEFAULT 1,
                    current_progress INTEGER NOT NULL DEFAULT 0,
                    tech_point_reward INTEGER NOT NULL DEFAULT 0,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    is_completed INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    completed_at TEXT,
                    FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE
                )
                """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_town_quests_town_id ON town_quests(town_id)");
        }
    }

    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = " + VERSION)) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

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
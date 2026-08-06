package org.aincraft.towny.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add tech tree system tables.
 * Version 9 — after AddBroadcastSystemMigration (v8).
 */
public class AddTechTreeSystemMigration implements DatabaseMigration {

    private static final int VERSION = 9;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add tech tree system with node definitions and town unlock tracking";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Node definitions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tech_tree_nodes (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    branch TEXT NOT NULL,
                    cost INTEGER NOT NULL DEFAULT 1,
                    prerequisites TEXT DEFAULT '[]',
                    effects TEXT DEFAULT '{}',
                    position_x INTEGER DEFAULT 0,
                    position_y INTEGER DEFAULT 0
                )
            """);

            // Town unlock tracking table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS town_unlocked_nodes (
                    town_id TEXT NOT NULL,
                    node_id TEXT NOT NULL,
                    unlocked_at TEXT NOT NULL,
                    PRIMARY KEY (town_id, node_id),
                    FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE,
                    FOREIGN KEY (node_id) REFERENCES tech_tree_nodes(id) ON DELETE CASCADE
                )
            """);

            // Indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tech_nodes_branch ON tech_tree_nodes(branch)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_town_unlocked_town ON town_unlocked_nodes(town_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_town_unlocked_node ON town_unlocked_nodes(node_id)");
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

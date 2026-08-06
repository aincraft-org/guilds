package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add blueprints table for building templates.
 * Version 14 — after AddQuestMigration (v13).
 */
public class AddBlueprintMigration implements DatabaseMigration {

    private static final int VERSION = 14;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add blueprints table for building templates and schematics";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS blueprints (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    author_uuid TEXT NOT NULL,
                    guild_id TEXT NOT NULL,
                    schematic_data BLOB,
                    created_at TEXT NOT NULL
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_blueprints_guild ON blueprints(guild_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_blueprints_name ON blueprints(name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_blueprints_author ON blueprints(author_uuid)");
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

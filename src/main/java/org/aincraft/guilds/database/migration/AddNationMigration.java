package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add nation system tables.
 * Version 11 — after AddEconomyMigration (v10).
 */
public class AddNationMigration implements DatabaseMigration {

    private static final int VERSION = 11;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add nation system tables for nation mechanics";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nations (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    king_uuid TEXT NOT NULL,
                    capital_town_id TEXT NOT NULL,
                    tax_rate REAL DEFAULT 0.0,
                    is_open INTEGER DEFAULT 0,
                    created_at TEXT NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nation_members (
                    nation_id TEXT NOT NULL,
                    town_id TEXT NOT NULL,
                    PRIMARY KEY (nation_id, town_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nation_ministers (
                    nation_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (nation_id, player_uuid)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nation_relations (
                    nation_id TEXT NOT NULL,
                    other_nation TEXT NOT NULL,
                    relation_type TEXT NOT NULL,
                    PRIMARY KEY (nation_id, other_nation)
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nations_capital ON nations(capital_town_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nations_king ON nations(king_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nation_members_nation ON nation_members(nation_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nation_members_town ON nation_members(town_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nation_ministers_nation ON nation_ministers(nation_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nation_relations_nation ON nation_relations(nation_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_nation_relations_type ON nation_relations(relation_type)");
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

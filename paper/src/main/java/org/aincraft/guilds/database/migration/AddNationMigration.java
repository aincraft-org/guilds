package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.territory.persist.SqlSupport;

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
            stmt.execute(SqlSupport.withIdType(connection, """
                CREATE TABLE IF NOT EXISTS nations (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    king_uuid TEXT NOT NULL,
                    capital_guild_id TEXT NOT NULL,
                    tax_rate REAL DEFAULT 0.0,
                    is_open INTEGER DEFAULT 0,
                    created_at TEXT NOT NULL
                )
            """));

            stmt.execute(SqlSupport.withIdType(connection, """
                CREATE TABLE IF NOT EXISTS nation_members (
                    nation_id TEXT NOT NULL,
                    guild_id TEXT NOT NULL,
                    PRIMARY KEY (nation_id, guild_id)
                )
            """));

            stmt.execute(SqlSupport.withIdType(connection, """
                CREATE TABLE IF NOT EXISTS nation_ministers (
                    nation_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    PRIMARY KEY (nation_id, player_uuid)
                )
            """));

            stmt.execute(SqlSupport.withIdType(connection, """
                CREATE TABLE IF NOT EXISTS nation_relations (
                    nation_id TEXT NOT NULL,
                    other_nation TEXT NOT NULL,
                    relation_type TEXT NOT NULL,
                    PRIMARY KEY (nation_id, other_nation)
                )
            """));

            SqlSupport.createIndexIfAbsent(connection, "idx_nations_capital", "nations", "capital_guild_id");
            SqlSupport.createIndexIfAbsent(connection, "idx_nations_king", "nations", "king_uuid");
            SqlSupport.createIndexIfAbsent(connection, "idx_nation_members_nation", "nation_members", "nation_id");
            SqlSupport.createIndexIfAbsent(connection, "idx_nation_members_guild", "nation_members", "guild_id");
            SqlSupport.createIndexIfAbsent(connection, "idx_nation_ministers_nation", "nation_ministers", "nation_id");
            SqlSupport.createIndexIfAbsent(connection, "idx_nation_relations_nation", "nation_relations", "nation_id");
            SqlSupport.createIndexIfAbsent(connection, "idx_nation_relations_type", "nation_relations", "relation_type");
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

package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.territory.persist.SqlSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add the guild contracts table.
 * Version 19 — after AddAllianceRenameMigration (v18).
 */
public class AddGuildContractMigration implements DatabaseMigration {

    private static final int VERSION = 19;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add guild contracts table for cross-guild level-up material contracts";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(SqlSupport.withIdType(connection, """
                CREATE TABLE IF NOT EXISTS guild_contracts (
                    id TEXT PRIMARY KEY,
                    contracting_guild_id TEXT NOT NULL,
                    resource_type TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    payment REAL NOT NULL,
                    filled INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    fulfilled_by_guild_id TEXT,
                    created_at TEXT NOT NULL,
                    fulfilled_at TEXT,
                    FOREIGN KEY (contracting_guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )
            """));

            SqlSupport.createIndexIfAbsent(connection, "idx_guild_contracts_status", "guild_contracts", "status");
            SqlSupport.createIndexIfAbsent(connection, "idx_guild_contracts_contracting", "guild_contracts", "contracting_guild_id");
            SqlSupport.createIndexIfAbsent(connection, "idx_guild_contracts_fulfilled_by", "guild_contracts", "fulfilled_by_guild_id");
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
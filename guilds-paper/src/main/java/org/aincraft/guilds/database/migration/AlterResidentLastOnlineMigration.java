package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.territory.persist.SqlSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Migration to widen the residents.last_online column from INTEGER to BIGINT.
 *
 * <p>The {@code Resident} model stores {@code last_online} as a {@code long} epoch-millis
 * timestamp (written via {@code System.currentTimeMillis()}), which exceeds the 32-bit
 * INTEGER range (~2.1 billion). Every resident INSERT/UPDATE that writes a live timestamp
 * fails with "integer out of range". Aligning the column with the model's {@code long}
 * type fixes resident persistence.
 *
 * <p>Version 20 — after AddGuildContractMigration (v19).
 */
public class AlterResidentLastOnlineMigration implements DatabaseMigration {

    private static final int VERSION = 20;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Widen residents.last_online from INTEGER to BIGINT to match the long-typed model";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        // ALTER TYPE INTEGER -> BIGINT works in-place on PostgreSQL; existing rows are
        // promoted losslessly. Idempotent for fresh installs (column is already BIGINT).
        SqlSupport.widenIntegerToBigint(connection, "residents", "last_online");
    }

    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        // Guard for fresh installs where the initial schema may already be BIGINT: treat
        // the migration as applied when the column is BIGINT, so it isn't re-run.
        return SqlSupport.isBigint(connection, "residents", "last_online");
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
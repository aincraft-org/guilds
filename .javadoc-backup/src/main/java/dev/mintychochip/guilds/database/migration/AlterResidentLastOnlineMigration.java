package dev.mintychochip.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

    /** The version constant. */
    private static final int VERSION = 20;

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
        return "Widen residents.last_online from INTEGER to BIGINT to match the long-typed model";
    }

    /**
     * Performs the migrate operation.
     * @param connection the connection
     * @throws SQLException if an error occurs
     */
    @Override
    public void migrate(Connection connection) throws SQLException {
        // ALTER TYPE INTEGER -> BIGINT works in-place on PostgreSQL; existing rows are
        // promoted losslessly. Idempotent for fresh installs (column is already BIGINT).
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE residents ALTER COLUMN last_online TYPE BIGINT");
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
        // Guard for fresh installs where the initial schema may already be BIGINT: treat
        // the migration as applied when the column is BIGINT, so it isn't re-run.
        String sql = """
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'residents'
              AND column_name = 'last_online'
              AND data_type = 'bigint'
            """;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
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
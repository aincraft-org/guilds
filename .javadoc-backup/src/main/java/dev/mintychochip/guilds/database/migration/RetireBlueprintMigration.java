package dev.mintychochip.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Retires the blueprint feature in the schema version history.
 * <p>
 * Version 17 — after AddGuildRenameMigration (v16).
 * <p>
 * The blueprint subsystem (WorldEdit save/apply) was removed from the plugin;
 * no code reads or writes the {@code blueprints} table anymore. This migration
 * is deliberately <strong>non-destructive</strong>: the table and its indexes
 * (created by v14, possibly renamed by v16) are left in place so any persisted
 * blueprint records are preserved. Operators who want them gone can drop the
 * table manually. The migration only pins the schema version forward so the
 * migration chain stays contiguous and replayable.
 */
public class RetireBlueprintMigration implements DatabaseMigration {

    /** The version constant. */
    private static final int VERSION = 17;

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
        return "Retire blueprint feature (table preserved for operator cleanup)";
    }

    /**
     * Performs the migrate operation.
     * @param connection the connection
     * @throws SQLException if an error occurs
     */
    @Override
    public void migrate(Connection connection) throws SQLException {
        // No schema change: the blueprints table (and any records in it) is
        // intentionally preserved. See the class javadoc.
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

package dev.mintychochip.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Add the chosen governance form to guilds and nations.
 * <p>
 * The governance form (MONARCHY / OLIGARCHY / DEMOCRACY / ANARCHY) is the
 * governance-layer decision for a guild or alliance entity: it determines the
 * seat structure derived from the entity's role holders. Defaults to MONARCHY
 * so existing guilds/nations are governed by their mayor/king until changed.
 * Version 15 — after AddBlueprintMigration (v14).
 */
public class AddGovernanceFormMigration implements DatabaseMigration {

    /** The version constant. */
    private static final int VERSION = 15;

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
        return "Add governance form columns to guilds and nations";
    }

    /**
     * Performs the migrate operation.
     * @param connection the connection
     * @throws SQLException if an error occurs
     */
    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE guilds ADD COLUMN governance_form TEXT NOT NULL DEFAULT 'MONARCHY'");
            stmt.execute("ALTER TABLE nations ADD COLUMN governance_form TEXT NOT NULL DEFAULT 'MONARCHY'");
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

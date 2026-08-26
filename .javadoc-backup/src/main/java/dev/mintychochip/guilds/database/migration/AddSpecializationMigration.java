package dev.mintychochip.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add guild specialization system.
 * Version 12 — after AddTravelAbilitiesMigration (v11).
 */
public class AddSpecializationMigration implements DatabaseMigration {

    /** The version constant. */
    private static final int VERSION = 12;

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
        return "Add guild specialization system with specialization tracking";
    }

    /**
     * Performs the migrate operation.
     * @param connection the connection
     * @throws SQLException if an error occurs
     */
    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Guild specializations table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS guild_specializations (
                    guild_id TEXT PRIMARY KEY,
                    specialization TEXT NOT NULL,
                    set_at TEXT NOT NULL,
                    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
                )
                """);

            // Index
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_guild_specialization ON guild_specializations(specialization)");
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
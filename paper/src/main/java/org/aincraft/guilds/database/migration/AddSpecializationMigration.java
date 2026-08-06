package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add town specialization system.
 * Version 12 — after AddTravelAbilitiesMigration (v11).
 */
public class AddSpecializationMigration implements DatabaseMigration {

    private static final int VERSION = 12;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add town specialization system with specialization tracking";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Town specializations table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS town_specializations (
                    town_id TEXT PRIMARY KEY,
                    specialization TEXT NOT NULL,
                    set_at TEXT NOT NULL,
                    FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE
                )
                """);

            // Index
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_town_specialization ON town_specializations(specialization)");
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
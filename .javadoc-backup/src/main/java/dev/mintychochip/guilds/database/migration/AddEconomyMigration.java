package dev.mintychochip.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Migration to add economy transaction logging table.
 * Version 10 — after AddTechTreeSystemMigration (v9).
 */
public class AddEconomyMigration implements DatabaseMigration {

    /** The version constant. */
    private static final int VERSION = 10;

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
        return "Add economy transactions table for economy audit log";
    }

    /**
     * Performs the migrate operation.
     * @param connection the connection
     * @throws SQLException if an error occurs
     */
    @Override
    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS economy_transactions (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT,
                    player_uuid TEXT,
                    type TEXT NOT NULL,
                    amount REAL NOT NULL,
                    description TEXT,
                    timestamp TEXT NOT NULL
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_economy_tx_guild ON economy_transactions(guild_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_economy_tx_player ON economy_transactions(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_economy_tx_type ON economy_transactions(type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_economy_tx_timestamp ON economy_transactions(timestamp)");
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

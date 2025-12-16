package org.aincraft.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.sql.*;
import java.util.*;

/**
 * SQLite-based implementation of PlayerGuildMapping.
 * Persists player-to-guild associations to SQLite database.
 */
public class SQLitePlayerGuildMapping implements PlayerGuildMapping {
    private final String connectionString;

    /**
     * Creates a new SQLitePlayerGuildMapping.
     *
     * @param dbPath the path to the SQLite database file
     */
    @Inject
    public SQLitePlayerGuildMapping(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    /**
     * Initializes the database schema if it doesn't exist.
     */
    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS player_guilds (
                player_id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_guild_id ON player_guilds(guild_id);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    @Override
    public void addPlayerToGuild(UUID playerId, String guildId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String insertSQL = """
            INSERT OR REPLACE INTO player_guilds (player_id, guild_id)
            VALUES (?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add player to guild", e);
        }
    }

    @Override
    public void removePlayerFromGuild(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String deleteSQL = "DELETE FROM player_guilds WHERE player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            pstmt.setString(1, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove player from guild", e);
        }
    }

    @Override
    public Optional<String> getPlayerGuildId(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String selectSQL = "SELECT guild_id FROM player_guilds WHERE player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(rs.getString("guild_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get player guild ID", e);
        }

        return Optional.empty();
    }

    @Override
    public boolean isPlayerInGuild(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String selectSQL = "SELECT 1 FROM player_guilds WHERE player_id = ? LIMIT 1";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, playerId.toString());
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check if player is in guild", e);
        }
    }
}

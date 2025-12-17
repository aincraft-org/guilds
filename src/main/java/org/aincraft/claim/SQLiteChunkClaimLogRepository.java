package org.aincraft.claim;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.ChunkKey;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite-based implementation of ChunkClaimLogRepository.
 */
public class SQLiteChunkClaimLogRepository implements ChunkClaimLogRepository {
    private final String connectionString;

    @Inject
    public SQLiteChunkClaimLogRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS chunk_claim_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id TEXT NOT NULL,
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                player_id TEXT NOT NULL,
                action TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_claim_log_guild ON chunk_claim_logs(guild_id);
            CREATE INDEX IF NOT EXISTS idx_claim_log_player ON chunk_claim_logs(player_id);
            CREATE INDEX IF NOT EXISTS idx_claim_log_chunk ON chunk_claim_logs(world, chunk_x, chunk_z);
            CREATE INDEX IF NOT EXISTS idx_claim_log_time ON chunk_claim_logs(timestamp);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize chunk_claim_logs table", e);
        }
    }

    @Override
    public void log(ChunkClaimLog entry) {
        Objects.requireNonNull(entry, "Log entry cannot be null");

        String insertSQL = """
            INSERT INTO chunk_claim_logs
            (guild_id, world, chunk_x, chunk_z, player_id, action, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, entry.guildId());
            pstmt.setString(2, entry.chunk().world());
            pstmt.setInt(3, entry.chunk().x());
            pstmt.setInt(4, entry.chunk().z());
            pstmt.setString(5, entry.playerId().toString());
            pstmt.setString(6, entry.action().name());
            pstmt.setLong(7, entry.timestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log chunk claim action", e);
        }
    }

    @Override
    public List<ChunkClaimLog> findByGuildId(String guildId, int limit) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String selectSQL = """
            SELECT * FROM chunk_claim_logs
            WHERE guild_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ChunkClaimLog> logs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                logs.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find logs by guild ID", e);
        }

        return logs;
    }

    @Override
    public List<ChunkClaimLog> findByPlayer(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String selectSQL = """
            SELECT * FROM chunk_claim_logs
            WHERE player_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ChunkClaimLog> logs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                logs.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find logs by player", e);
        }

        return logs;
    }

    @Override
    public List<ChunkClaimLog> findByChunk(ChunkKey chunk, int limit) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        String selectSQL = """
            SELECT * FROM chunk_claim_logs
            WHERE world = ? AND chunk_x = ? AND chunk_z = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ChunkClaimLog> logs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, chunk.world());
            pstmt.setInt(2, chunk.x());
            pstmt.setInt(3, chunk.z());
            pstmt.setInt(4, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                logs.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find logs by chunk", e);
        }

        return logs;
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String deleteSQL = "DELETE FROM chunk_claim_logs WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete chunk claim logs", e);
        }
    }

    private ChunkClaimLog mapResultSet(ResultSet rs) throws SQLException {
        ChunkKey chunk = new ChunkKey(
                rs.getString("world"),
                rs.getInt("chunk_x"),
                rs.getInt("chunk_z")
        );

        return new ChunkClaimLog(
                rs.getLong("id"),
                rs.getString("guild_id"),
                chunk,
                UUID.fromString(rs.getString("player_id")),
                ChunkClaimLog.ActionType.valueOf(rs.getString("action")),
                rs.getLong("timestamp")
        );
    }
}

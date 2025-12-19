package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.claim.ChunkClaimLog;
import org.aincraft.claim.ChunkClaimLogRepository;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;

/**
 * JDBC-based implementation of ChunkClaimLogRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcChunkClaimLogRepository implements ChunkClaimLogRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcChunkClaimLogRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void log(ChunkClaimLog entry) {
        Objects.requireNonNull(entry, "Log entry cannot be null");

        String sql = """
            INSERT INTO chunk_claim_logs
            (guild_id, world, chunk_x, chunk_z, player_id, action, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.guildId().toString());
            ps.setString(2, entry.chunk().world());
            ps.setInt(3, entry.chunk().x());
            ps.setInt(4, entry.chunk().z());
            ps.setString(5, entry.playerId().toString());
            ps.setString(6, entry.action().name());
            ps.setLong(7, entry.timestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log chunk claim action", e);
        }
    }

    @Override
    public List<ChunkClaimLog> findByGuildId(UUID guildId, int limit) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = """
            SELECT * FROM chunk_claim_logs
            WHERE guild_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ChunkClaimLog> logs = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();

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

        String sql = """
            SELECT * FROM chunk_claim_logs
            WHERE player_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ChunkClaimLog> logs = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();

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

        String sql = """
            SELECT * FROM chunk_claim_logs
            WHERE world = ? AND chunk_x = ? AND chunk_z = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ChunkClaimLog> logs = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunk.world());
            ps.setInt(2, chunk.x());
            ps.setInt(3, chunk.z());
            ps.setInt(4, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                logs.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find logs by chunk", e);
        }

        return logs;
    }

    @Override
    public void deleteByGuildId(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM chunk_claim_logs WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
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
            UUID.fromString(rs.getString("guild_id")),
            chunk,
            UUID.fromString(rs.getString("player_id")),
            ChunkClaimLog.ActionType.valueOf(rs.getString("action")),
            rs.getLong("timestamp")
        );
    }
}

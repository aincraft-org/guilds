package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.map.ChunkClaimData;
import org.aincraft.storage.ChunkClaimRepository;

/**
 * JDBC-based implementation of ChunkClaimRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcChunkClaimRepository implements ChunkClaimRepository {
    private final ConnectionProvider connectionProvider;

    @Inject
    public JdbcChunkClaimRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public boolean claim(ChunkKey chunk, UUID guildId, UUID claimedBy) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(claimedBy, "Claimed by cannot be null");

        if (getOwner(chunk).isPresent()) {
            return false;
        }

        String sql = """
            INSERT INTO guild_chunks (world, chunk_x, chunk_z, guild_id, claimed_at, claimed_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunk.world());
            ps.setInt(2, chunk.x());
            ps.setInt(3, chunk.z());
            ps.setString(4, guildId.toString());
            ps.setLong(5, System.currentTimeMillis());
            ps.setString(6, claimedBy.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to claim chunk", e);
        }
    }

    @Override
    public boolean unclaim(ChunkKey chunk, UUID guildId) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Optional<UUID> owner = getOwner(chunk);
        if (owner.isEmpty() || !owner.get().equals(guildId)) {
            return false;
        }

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM guild_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            ps.setString(1, chunk.world());
            ps.setInt(2, chunk.x());
            ps.setInt(3, chunk.z());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unclaim chunk", e);
        }
    }

    @Override
    public void unclaimAll(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_chunks WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unclaim all chunks", e);
        }
    }

    @Override
    public Optional<UUID> getOwner(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT guild_id FROM guild_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            ps.setString(1, chunk.world());
            ps.setInt(2, chunk.x());
            ps.setInt(3, chunk.z());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(UUID.fromString(rs.getString("guild_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get chunk owner", e);
        }

        return Optional.empty();
    }

    @Override
    public List<ChunkKey> getGuildChunks(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        List<ChunkKey> chunks = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT world, chunk_x, chunk_z FROM guild_chunks WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                chunks.add(new ChunkKey(
                    rs.getString("world"),
                    rs.getInt("chunk_x"),
                    rs.getInt("chunk_z")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get guild chunks", e);
        }

        return chunks;
    }

    @Override
    public int getChunkCount(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM guild_chunks WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get chunk count", e);
        }

        return 0;
    }

    @Override
    public Map<ChunkKey, ChunkClaimData> getOwnersForChunks(List<ChunkKey> chunks) {
        Objects.requireNonNull(chunks, "Chunks cannot be null");

        if (chunks.isEmpty()) {
            return new HashMap<>();
        }

        Map<ChunkKey, ChunkClaimData> result = new HashMap<>();

        // Build SQL with IN clause for all chunks
        StringBuilder sql = new StringBuilder(
            "SELECT world, chunk_x, chunk_z, guild_id, claimed_by, claimed_at FROM guild_chunks WHERE "
        );

        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            conditions.add("(world = ? AND chunk_x = ? AND chunk_z = ?)");
        }
        sql.append(String.join(" OR ", conditions));

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            for (ChunkKey chunk : chunks) {
                ps.setString(paramIndex++, chunk.world());
                ps.setInt(paramIndex++, chunk.x());
                ps.setInt(paramIndex++, chunk.z());
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ChunkKey key = new ChunkKey(
                    rs.getString("world"),
                    rs.getInt("chunk_x"),
                    rs.getInt("chunk_z")
                );
                ChunkClaimData data = new ChunkClaimData(
                    UUID.fromString(rs.getString("guild_id")),
                    UUID.fromString(rs.getString("claimed_by")),
                    rs.getLong("claimed_at")
                );
                result.put(key, data);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get chunk owners", e);
        }

        return result;
    }
}

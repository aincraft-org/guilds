package org.aincraft.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.ChunkKey;
import org.aincraft.map.ChunkClaimData;

import java.sql.*;
import java.util.*;

/**
 * SQLite-based implementation of ChunkClaimRepository.
 */
public class SQLiteChunkClaimRepository implements ChunkClaimRepository {
    private final String connectionString;

    @Inject
    public SQLiteChunkClaimRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS guild_chunks (
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                guild_id TEXT NOT NULL,
                claimed_at INTEGER NOT NULL,
                claimed_by TEXT NOT NULL,
                PRIMARY KEY (world, chunk_x, chunk_z)
            );
            CREATE INDEX IF NOT EXISTS idx_guild_chunks_guild ON guild_chunks(guild_id);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize guild_chunks table", e);
        }
    }

    @Override
    public boolean claim(ChunkKey chunk, String guildId, UUID claimedBy) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(claimedBy, "Claimed by cannot be null");

        if (getOwner(chunk).isPresent()) {
            return false;
        }

        String insertSQL = """
            INSERT INTO guild_chunks (world, chunk_x, chunk_z, guild_id, claimed_at, claimed_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, chunk.world());
            pstmt.setInt(2, chunk.x());
            pstmt.setInt(3, chunk.z());
            pstmt.setString(4, guildId);
            pstmt.setLong(5, System.currentTimeMillis());
            pstmt.setString(6, claimedBy.toString());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to claim chunk", e);
        }
    }

    @Override
    public boolean unclaim(ChunkKey chunk, String guildId) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Optional<String> owner = getOwner(chunk);
        if (owner.isEmpty() || !owner.get().equals(guildId)) {
            return false;
        }

        String deleteSQL = "DELETE FROM guild_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, chunk.world());
            pstmt.setInt(2, chunk.x());
            pstmt.setInt(3, chunk.z());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unclaim chunk", e);
        }
    }

    @Override
    public void unclaimAll(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String deleteSQL = "DELETE FROM guild_chunks WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unclaim all chunks", e);
        }
    }

    @Override
    public Optional<String> getOwner(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        String selectSQL = "SELECT guild_id FROM guild_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, chunk.world());
            pstmt.setInt(2, chunk.x());
            pstmt.setInt(3, chunk.z());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(rs.getString("guild_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get chunk owner", e);
        }

        return Optional.empty();
    }

    @Override
    public List<ChunkKey> getGuildChunks(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String selectSQL = "SELECT world, chunk_x, chunk_z FROM guild_chunks WHERE guild_id = ?";
        List<ChunkKey> chunks = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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
    public int getChunkCount(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String selectSQL = "SELECT COUNT(*) FROM guild_chunks WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            for (ChunkKey chunk : chunks) {
                pstmt.setString(paramIndex++, chunk.world());
                pstmt.setInt(paramIndex++, chunk.x());
                pstmt.setInt(paramIndex++, chunk.z());
            }

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                ChunkKey key = new ChunkKey(
                    rs.getString("world"),
                    rs.getInt("chunk_x"),
                    rs.getInt("chunk_z")
                );
                ChunkClaimData data = new ChunkClaimData(
                    rs.getString("guild_id"),
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

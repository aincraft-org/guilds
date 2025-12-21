package org.aincraft.database.repository;

import org.aincraft.outpost.Outpost;
import org.aincraft.outpost.OutpostRepository;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.database.ConnectionProvider;

/**
 * JDBC implementation of OutpostRepository.
 * Single Responsibility: Database persistence for outposts.
 * Open/Closed: Uses connection provider abstraction.
 */
public class JdbcOutpostRepository implements OutpostRepository {
    private final ConnectionProvider connectionProvider;

    @Inject
    public JdbcOutpostRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "Connection provider cannot be null");
    }

    @Override
    public boolean save(Outpost outpost) {
        Objects.requireNonNull(outpost, "Outpost cannot be null");

        String sql = """
            INSERT INTO guild_outposts
            (id, guild_id, name, world, chunk_x, chunk_z, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, created_at, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                spawn_x = excluded.spawn_x,
                spawn_y = excluded.spawn_y,
                spawn_z = excluded.spawn_z,
                spawn_yaw = excluded.spawn_yaw,
                spawn_pitch = excluded.spawn_pitch
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, outpost.getId().toString());
            ps.setString(2, outpost.getGuildId().toString());
            ps.setString(3, outpost.getName());
            ps.setString(4, outpost.getLocation().world());
            ps.setInt(5, outpost.getLocation().x());
            ps.setInt(6, outpost.getLocation().z());
            ps.setDouble(7, outpost.getSpawnX());
            ps.setDouble(8, outpost.getSpawnY());
            ps.setDouble(9, outpost.getSpawnZ());
            ps.setFloat(10, outpost.getSpawnYaw());
            ps.setFloat(11, outpost.getSpawnPitch());
            ps.setLong(12, outpost.getCreatedAt());
            ps.setString(13, outpost.getCreatedBy().toString());

            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Log error but don't throw - allow graceful degradation
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delete(UUID outpostId) {
        Objects.requireNonNull(outpostId, "Outpost ID cannot be null");

        String sql = "DELETE FROM guild_outposts WHERE id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, outpostId.toString());
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public Optional<Outpost> findById(UUID outpostId) {
        Objects.requireNonNull(outpostId, "Outpost ID cannot be null");

        String sql = "SELECT * FROM guild_outposts WHERE id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, outpostId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapRowToOutpost(rs);
            }
            return Optional.empty();
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public Optional<Outpost> findByGuildAndName(UUID guildId, String name) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        String sql = "SELECT * FROM guild_outposts WHERE guild_id = ? AND LOWER(name) = LOWER(?)";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, guildId.toString());
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return mapRowToOutpost(rs);
            }
            return Optional.empty();
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public List<Outpost> findByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT * FROM guild_outposts WHERE guild_id = ? ORDER BY name ASC";
        List<Outpost> outposts = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                mapRowToOutpost(rs).ifPresent(outposts::add);
            }
            return outposts;
        } catch (SQLException e) {
            e.printStackTrace();
            return outposts;
        }
    }

    @Override
    public List<Outpost> findByChunk(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        String sql = "SELECT * FROM guild_outposts WHERE world = ? AND chunk_x = ? AND chunk_z = ?";
        List<Outpost> outposts = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, chunk.world());
            ps.setInt(2, chunk.x());
            ps.setInt(3, chunk.z());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                mapRowToOutpost(rs).ifPresent(outposts::add);
            }
            return outposts;
        } catch (SQLException e) {
            e.printStackTrace();
            return outposts;
        }
    }

    @Override
    public int getCountByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT COUNT(*) as count FROM guild_outposts WHERE guild_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("count");
            }
            return 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public int deleteByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "DELETE FROM guild_outposts WHERE guild_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, guildId.toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public int deleteByChunk(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        String sql = "DELETE FROM guild_outposts WHERE world = ? AND chunk_x = ? AND chunk_z = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, chunk.world());
            ps.setInt(2, chunk.x());
            ps.setInt(3, chunk.z());
            return ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Maps a database row to an Outpost domain object.
     */
    private Optional<Outpost> mapRowToOutpost(ResultSet rs) throws SQLException {
        try {
            UUID id = UUID.fromString(rs.getString("id"));
            UUID guildId = UUID.fromString(rs.getString("guild_id"));
            String name = rs.getString("name");
            String world = rs.getString("world");
            int chunkX = rs.getInt("chunk_x");
            int chunkZ = rs.getInt("chunk_z");
            double spawnX = rs.getDouble("spawn_x");
            double spawnY = rs.getDouble("spawn_y");
            double spawnZ = rs.getDouble("spawn_z");
            float spawnYaw = rs.getFloat("spawn_yaw");
            float spawnPitch = rs.getFloat("spawn_pitch");
            long createdAt = rs.getLong("created_at");
            UUID createdBy = UUID.fromString(rs.getString("created_by"));

            ChunkKey location = new ChunkKey(world, chunkX, chunkZ);

            return Outpost.restore(id, guildId, name, location, world,
                spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, createdAt, createdBy);
        } catch (IllegalArgumentException e) {
            // Invalid UUID format in database
            e.printStackTrace();
            return Optional.empty();
        }
    }
}

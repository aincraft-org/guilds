package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.aincraft.ChunkKey;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionRepository;
import org.bukkit.Location;

/**
 * JDBC-based implementation of SubregionRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcSubregionRepository implements SubregionRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcSubregionRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(Subregion region) {
        Objects.requireNonNull(region, "Subregion cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, region.getId().toString());
            ps.setObject(2, region.getGuildId());
            ps.setString(3, region.getName());
            ps.setString(4, region.getWorld());
            ps.setInt(5, region.getMinX());
            ps.setInt(6, region.getMinY());
            ps.setInt(7, region.getMinZ());
            ps.setInt(8, region.getMaxX());
            ps.setInt(9, region.getMaxY());
            ps.setInt(10, region.getMaxZ());
            ps.setString(11, region.getCreatedBy().toString());
            ps.setLong(12, region.getCreatedAt());
            ps.setString(13, serializeOwners(region.getOwners()));
            ps.setInt(14, region.getPermissions());
            ps.setString(15, region.getType());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save subregion", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z,
                 created_by, created_at, owners, permissions, type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z,
                 created_by, created_at, owners, permissions, type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                name = VALUES(name), min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                owners = VALUES(owners), permissions = VALUES(permissions), type = VALUES(type)
                """;
            case POSTGRESQL -> """
                INSERT INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z,
                 created_by, created_at, owners, permissions, type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name, min_x = EXCLUDED.min_x, min_y = EXCLUDED.min_y, min_z = EXCLUDED.min_z,
                max_x = EXCLUDED.max_x, max_y = EXCLUDED.max_y, max_z = EXCLUDED.max_z,
                owners = EXCLUDED.owners, permissions = EXCLUDED.permissions, type = EXCLUDED.type
                """;
            case H2 -> """
                MERGE INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z,
                 created_by, created_at, owners, permissions, type)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public void delete(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM subregions WHERE id = ?")) {
            ps.setString(1, regionId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete subregion", e);
        }
    }

    @Override
    public void deleteAllByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM subregions WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild subregions", e);
        }
    }

    @Override
    public Optional<Subregion> findById(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM subregions WHERE id = ?")) {
            ps.setString(1, regionId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find subregion by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Subregion> findByGuildAndName(UUID guildId, String name) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM subregions WHERE guild_id = ? AND LOWER(name) = LOWER(?)")) {
            ps.setString(1, guildId.toString());
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find subregion by name", e);
        }

        return Optional.empty();
    }

    @Override
    public List<Subregion> findByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        List<Subregion> regions = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM subregions WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                regions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild subregions", e);
        }

        return regions;
    }

    @Override
    public List<Subregion> findByLocation(Location loc) {
        Objects.requireNonNull(loc, "Location cannot be null");

        String sql = """
            SELECT * FROM subregions
            WHERE world = ?
            AND min_x <= ? AND max_x >= ?
            AND min_y <= ? AND max_y >= ?
            AND min_z <= ? AND max_z >= ?
            """;

        List<Subregion> regions = new ArrayList<>();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, x);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, y);
            ps.setInt(6, z);
            ps.setInt(7, z);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                regions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find subregions at location", e);
        }

        return regions;
    }

    @Override
    public List<Subregion> findOverlappingChunks(Set<ChunkKey> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Subregion> result = new ArrayList<>();
        for (ChunkKey chunk : chunks) {
            result.addAll(findOverlappingChunk(chunk));
        }
        return result.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public List<Subregion> findOverlappingChunk(ChunkKey chunk) {
        Objects.requireNonNull(chunk, "Chunk cannot be null");

        int chunkMinX = chunk.x() * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.z() * 16;
        int chunkMaxZ = chunkMinZ + 15;

        String sql = """
            SELECT * FROM subregions
            WHERE world = ?
            AND min_x <= ? AND max_x >= ?
            AND min_z <= ? AND max_z >= ?
            """;

        List<Subregion> regions = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunk.world());
            ps.setInt(2, chunkMaxX);
            ps.setInt(3, chunkMinX);
            ps.setInt(4, chunkMaxZ);
            ps.setInt(5, chunkMinZ);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                regions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find subregions in chunk", e);
        }

        return regions;
    }

    @Override
    public int getCountByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM subregions WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count guild subregions", e);
        }

        return 0;
    }

    @Override
    public long getTotalVolumeByGuildAndType(UUID guildId, String typeId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = """
            SELECT SUM((max_x - min_x + 1) * (max_y - min_y + 1) * (max_z - min_z + 1)) as total_volume
            FROM subregions
            WHERE guild_id = ? AND type = ?
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.setString(2, typeId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getLong("total_volume");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate total volume by type", e);
        }

        return 0;
    }

    private Subregion mapResultSet(ResultSet rs) throws SQLException {
        return new Subregion(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("guild_id")),
            rs.getString("name"),
            rs.getString("world"),
            rs.getInt("min_x"),
            rs.getInt("min_y"),
            rs.getInt("min_z"),
            rs.getInt("max_x"),
            rs.getInt("max_y"),
            rs.getInt("max_z"),
            UUID.fromString(rs.getString("created_by")),
            rs.getLong("created_at"),
            deserializeOwners(rs.getString("owners")),
            rs.getInt("permissions"),
            rs.getString("type")
        );
    }

    private String serializeOwners(Set<UUID> owners) {
        return owners.stream()
            .map(UUID::toString)
            .collect(Collectors.joining(","));
    }

    private Set<UUID> deserializeOwners(String ownersStr) {
        if (ownersStr == null || ownersStr.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(ownersStr.split(","))
            .map(UUID::fromString)
            .collect(Collectors.toSet());
    }
}

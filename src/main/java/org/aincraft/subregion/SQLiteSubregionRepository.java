package org.aincraft.subregion;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import org.bukkit.Location;

/**
 * SQLite-based implementation of SubregionRepository.
 */
public class SQLiteSubregionRepository implements SubregionRepository {
    private final String connectionString;

    @Inject
    public SQLiteSubregionRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS subregions (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                name TEXT NOT NULL,
                world TEXT NOT NULL,
                min_x INTEGER NOT NULL,
                min_y INTEGER NOT NULL,
                min_z INTEGER NOT NULL,
                max_x INTEGER NOT NULL,
                max_y INTEGER NOT NULL,
                max_z INTEGER NOT NULL,
                created_by TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                owners TEXT NOT NULL,
                permissions INTEGER NOT NULL DEFAULT 0,
                type TEXT,
                UNIQUE(guild_id, name)
            );
            CREATE INDEX IF NOT EXISTS idx_subregions_guild ON subregions(guild_id);
            CREATE INDEX IF NOT EXISTS idx_subregions_world ON subregions(world);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
            // Add type column if it doesn't exist (for existing databases)
            addTypeColumnIfMissing(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize subregions table", e);
        }
    }

    private void addTypeColumnIfMissing(Connection conn) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, "subregions", "type")) {
            if (!rs.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE subregions ADD COLUMN type TEXT");
                }
            }
        }
    }

    @Override
    public void save(Subregion region) {
        Objects.requireNonNull(region, "Subregion cannot be null");

        String upsertSQL = """
            INSERT OR REPLACE INTO subregions
            (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z,
             created_by, created_at, owners, permissions, type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {
            pstmt.setString(1, region.getId().toString());
            pstmt.setObject(2, region.getGuildId());
            pstmt.setString(3, region.getName());
            pstmt.setString(4, region.getWorld());
            pstmt.setInt(5, region.getMinX());
            pstmt.setInt(6, region.getMinY());
            pstmt.setInt(7, region.getMinZ());
            pstmt.setInt(8, region.getMaxX());
            pstmt.setInt(9, region.getMaxY());
            pstmt.setInt(10, region.getMaxZ());
            pstmt.setString(11, region.getCreatedBy().toString());
            pstmt.setLong(12, region.getCreatedAt());
            pstmt.setString(13, serializeOwners(region.getOwners()));
            pstmt.setInt(14, region.getPermissions());
            pstmt.setString(15, region.getType());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save subregion", e);
        }
    }

    @Override
    public void delete(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String deleteSQL = "DELETE FROM subregions WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, regionId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete subregion", e);
        }
    }

    @Override
    public void deleteAllByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String deleteSQL = "DELETE FROM subregions WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild subregions", e);
        }
    }

    @Override
    public Optional<Subregion> findById(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String selectSQL = "SELECT * FROM subregions WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, regionId.toString());
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM subregions WHERE guild_id = ? AND LOWER(name) = LOWER(?)";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId.toString());
            pstmt.setString(2, name);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM subregions WHERE guild_id = ?";
        List<Subregion> regions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId.toString());
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = """
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

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, loc.getWorld().getName());
            pstmt.setInt(2, x);
            pstmt.setInt(3, x);
            pstmt.setInt(4, y);
            pstmt.setInt(5, y);
            pstmt.setInt(6, z);
            pstmt.setInt(7, z);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = """
            SELECT * FROM subregions
            WHERE world = ?
            AND min_x <= ? AND max_x >= ?
            AND min_z <= ? AND max_z >= ?
            """;

        List<Subregion> regions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, chunk.world());
            pstmt.setInt(2, chunkMaxX);
            pstmt.setInt(3, chunkMinX);
            pstmt.setInt(4, chunkMaxZ);
            pstmt.setInt(5, chunkMinZ);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT COUNT(*) FROM subregions WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId.toString());
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = """
            SELECT SUM((max_x - min_x + 1) * (max_y - min_y + 1) * (max_z - min_z + 1)) as total_volume
            FROM subregions
            WHERE guild_id = ? AND type = ?
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId.toString());
            pstmt.setString(2, typeId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getLong("total_volume");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate total volume by type", e);
        }

        return 0;
    }

    @Override
    public List<Subregion> findOverlapping(UUID guildId, String world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(world, "World cannot be null");

        String selectSQL = """
            SELECT * FROM subregions
            WHERE guild_id = ? AND world = ?
            AND min_x <= ? AND max_x >= ?
            AND min_y <= ? AND max_y >= ?
            AND min_z <= ? AND max_z >= ?
            """;

        List<Subregion> regions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId.toString());
            pstmt.setString(2, world);
            pstmt.setInt(3, maxX);
            pstmt.setInt(4, minX);
            pstmt.setInt(5, maxY);
            pstmt.setInt(6, minY);
            pstmt.setInt(7, maxZ);
            pstmt.setInt(8, minZ);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                regions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find overlapping regions", e);
        }

        return regions;
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

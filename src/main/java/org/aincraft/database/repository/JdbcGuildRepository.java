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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.Sql;
import org.aincraft.storage.GuildRepository;

/**
 * JDBC-based implementation of GuildRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildRepository implements GuildRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(Guild guild) {
        Objects.requireNonNull(guild, "Guild cannot be null");

        String membersJson = serializeMembers(guild.getMembers());
        String sql = Sql.upsertGuild(dbType);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, guild.getId());
            ps.setString(2, guild.getName());
            ps.setString(3, guild.getDescription());
            ps.setString(4, guild.getOwnerId().toString());
            ps.setLong(5, guild.getCreatedAt());
            ps.setInt(6, guild.getMaxMembers());
            ps.setString(7, membersJson);
            ps.setString(8, guild.getSpawnWorld());
            ps.setObject(9, guild.getSpawnX());
            ps.setObject(10, guild.getSpawnY());
            ps.setObject(11, guild.getSpawnZ());
            ps.setObject(12, guild.getSpawnYaw());
            ps.setObject(13, guild.getSpawnPitch());
            ps.setString(14, guild.getColor());

            ChunkKey homeblock = guild.getHomeblock();
            ps.setString(15, homeblock != null ? homeblock.world() : null);
            ps.setObject(16, homeblock != null ? homeblock.x() : null);
            ps.setObject(17, homeblock != null ? homeblock.z() : null);

            setBoolean(ps, 18, guild.isExplosionsAllowed());
            setBoolean(ps, 19, guild.isFireAllowed());
            setBoolean(ps, 20, guild.isPublic());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild", e);
        }
    }

    @Override
    public void delete(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guilds WHERE id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild", e);
        }
    }

    @Override
    public Optional<Guild> findById(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guilds WHERE id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToGuild(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Guild> findByName(String name) {
        Objects.requireNonNull(name, "Guild name cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guilds WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToGuild(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild by name", e);
        }

        return Optional.empty();
    }

    @Override
    public List<Guild> findAll() {
        List<Guild> guilds = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guilds");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                guilds.add(mapRowToGuild(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all guilds", e);
        }

        return Collections.unmodifiableList(guilds);
    }

    private Guild mapRowToGuild(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        String ownerId = rs.getString("owner_id");
        long createdAt = rs.getLong("created_at");
        int maxMembers = rs.getInt("max_members");
        String membersJson = rs.getString("members");
        String color = rs.getString("color");

        Guild guild = new Guild(id, name, description, UUID.fromString(ownerId), createdAt, maxMembers, color);

        restoreMembers(guild, membersJson);
        restoreSpawn(guild, rs);
        restoreHomeblock(guild, rs);

        guild.setExplosionsAllowed(getBoolean(rs, "allow_explosions", true));
        guild.setFireAllowed(getBoolean(rs, "allow_fire", true));
        guild.setPublic(getBoolean(rs, "is_public", false));

        return guild;
    }

    private void restoreMembers(Guild guild, String membersJson) throws SQLException {
        try {
            List<UUID> members = deserializeMembers(membersJson);
            var membersField = Guild.class.getDeclaredField("members");
            membersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<UUID> guildMembers = (List<UUID>) membersField.get(guild);
            guildMembers.clear();
            guildMembers.addAll(members);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new SQLException("Failed to restore guild members", e);
        }
    }

    private void restoreSpawn(Guild guild, ResultSet rs) throws SQLException {
        String spawnWorld = rs.getString("spawn_world");
        if (spawnWorld != null) {
            guild.setSpawnWorld(spawnWorld);
            guild.setSpawnX(rs.getDouble("spawn_x"));
            guild.setSpawnY(rs.getDouble("spawn_y"));
            guild.setSpawnZ(rs.getDouble("spawn_z"));
            guild.setSpawnYaw(rs.getFloat("spawn_yaw"));
            guild.setSpawnPitch(rs.getFloat("spawn_pitch"));
        }
    }

    private void restoreHomeblock(Guild guild, ResultSet rs) throws SQLException {
        String homeblockWorld = rs.getString("homeblock_world");
        if (homeblockWorld != null) {
            guild.setHomeblock(new ChunkKey(
                homeblockWorld,
                rs.getInt("homeblock_chunk_x"),
                rs.getInt("homeblock_chunk_z")
            ));
        }
    }

    private String serializeMembers(List<UUID> members) {
        return members.stream()
            .map(UUID::toString)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    private List<UUID> deserializeMembers(String membersJson) {
        if (membersJson == null || membersJson.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(membersJson.split(","))
            .map(UUID::fromString)
            .toList());
    }

    private void setBoolean(PreparedStatement ps, int index, boolean value) throws SQLException {
        if (dbType == DatabaseType.SQLITE) {
            ps.setInt(index, value ? 1 : 0);
        } else if (dbType == DatabaseType.MYSQL || dbType == DatabaseType.MARIADB) {
            ps.setInt(index, value ? 1 : 0);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private boolean getBoolean(ResultSet rs, String column, boolean defaultValue) throws SQLException {
        try {
            if (dbType == DatabaseType.SQLITE || dbType == DatabaseType.MYSQL || dbType == DatabaseType.MARIADB) {
                return rs.getInt(column) != 0;
            } else {
                return rs.getBoolean(column);
            }
        } catch (SQLException e) {
            return defaultValue;
        }
    }
}

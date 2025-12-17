package org.aincraft.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import java.sql.*;
import java.util.*;

/**
 * SQLite-based implementation of GuildRepository.
 * Persists guilds to SQLite database.
 */
public class SQLiteGuildRepository implements GuildRepository {
    private final String connectionString;

    /**
     * Creates a new SQLiteGuildRepository.
     *
     * @param dbPath the path to the SQLite database file
     */
    @Inject
    public SQLiteGuildRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    /**
     * Initializes the database schema if it doesn't exist.
     */
    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS guilds (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL UNIQUE,
                description TEXT,
                owner_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                max_members INTEGER NOT NULL,
                members TEXT NOT NULL,
                spawn_world TEXT,
                spawn_x REAL,
                spawn_y REAL,
                spawn_z REAL,
                spawn_yaw REAL,
                spawn_pitch REAL,
                color TEXT,
                homeblock_world TEXT,
                homeblock_chunk_x INTEGER,
                homeblock_chunk_z INTEGER
            );
            CREATE INDEX IF NOT EXISTS idx_guild_name ON guilds(name);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }

            // Migration: Add spawn columns, color, and homeblock to existing tables
            String[] migrations = {
                "ALTER TABLE guilds ADD COLUMN spawn_world TEXT",
                "ALTER TABLE guilds ADD COLUMN spawn_x REAL",
                "ALTER TABLE guilds ADD COLUMN spawn_y REAL",
                "ALTER TABLE guilds ADD COLUMN spawn_z REAL",
                "ALTER TABLE guilds ADD COLUMN spawn_yaw REAL",
                "ALTER TABLE guilds ADD COLUMN spawn_pitch REAL",
                "ALTER TABLE guilds ADD COLUMN color TEXT",
                "ALTER TABLE guilds ADD COLUMN homeblock_world TEXT",
                "ALTER TABLE guilds ADD COLUMN homeblock_chunk_x INTEGER",
                "ALTER TABLE guilds ADD COLUMN homeblock_chunk_z INTEGER"
            };

            for (String migration : migrations) {
                try {
                    stmt.execute(migration);
                } catch (SQLException e) {
                    // Column likely already exists, ignore
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    @Override
    public void save(Guild guild) {
        Objects.requireNonNull(guild, "Guild cannot be null");

        String membersJson = serializeMembers(guild.getMembers());

        try (Connection conn = DriverManager.getConnection(connectionString)) {
            // Check if guild already exists
            String checkSQL = "SELECT id FROM guilds WHERE id = ?";
            boolean exists;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSQL)) {
                checkStmt.setString(1, guild.getId());
                exists = checkStmt.executeQuery().next();
            }

            String sql;
            if (exists) {
                // Guild exists - UPDATE without modifying created_at (immutable field)
                sql = """
                    UPDATE guilds
                    SET name = ?, description = ?, owner_id = ?, max_members = ?, members = ?,
                        spawn_world = ?, spawn_x = ?, spawn_y = ?, spawn_z = ?, spawn_yaw = ?, spawn_pitch = ?, color = ?,
                        homeblock_world = ?, homeblock_chunk_x = ?, homeblock_chunk_z = ?
                    WHERE id = ?
                    """;
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guild.getName());
                    pstmt.setString(2, guild.getDescription());
                    pstmt.setString(3, guild.getOwnerId().toString());
                    pstmt.setInt(4, guild.getMaxMembers());
                    pstmt.setString(5, membersJson);
                    pstmt.setString(6, guild.getSpawnWorld());
                    pstmt.setObject(7, guild.getSpawnX());
                    pstmt.setObject(8, guild.getSpawnY());
                    pstmt.setObject(9, guild.getSpawnZ());
                    pstmt.setObject(10, guild.getSpawnYaw());
                    pstmt.setObject(11, guild.getSpawnPitch());
                    pstmt.setString(12, guild.getColor());
                    ChunkKey homeblock = guild.getHomeblock();
                    pstmt.setString(13, homeblock != null ? homeblock.world() : null);
                    pstmt.setObject(14, homeblock != null ? homeblock.x() : null);
                    pstmt.setObject(15, homeblock != null ? homeblock.z() : null);
                    pstmt.setString(16, guild.getId());
                    pstmt.executeUpdate();
                }
            } else {
                // New guild - INSERT with created_at
                sql = """
                    INSERT INTO guilds
                    (id, name, description, owner_id, created_at, max_members, members,
                     spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, color,
                     homeblock_world, homeblock_chunk_x, homeblock_chunk_z)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, guild.getId());
                    pstmt.setString(2, guild.getName());
                    pstmt.setString(3, guild.getDescription());
                    pstmt.setString(4, guild.getOwnerId().toString());
                    pstmt.setLong(5, guild.getCreatedAt());
                    pstmt.setInt(6, guild.getMaxMembers());
                    pstmt.setString(7, membersJson);
                    pstmt.setString(8, guild.getSpawnWorld());
                    pstmt.setObject(9, guild.getSpawnX());
                    pstmt.setObject(10, guild.getSpawnY());
                    pstmt.setObject(11, guild.getSpawnZ());
                    pstmt.setObject(12, guild.getSpawnYaw());
                    pstmt.setObject(13, guild.getSpawnPitch());
                    pstmt.setString(14, guild.getColor());
                    ChunkKey homeblock = guild.getHomeblock();
                    pstmt.setString(15, homeblock != null ? homeblock.world() : null);
                    pstmt.setObject(16, homeblock != null ? homeblock.x() : null);
                    pstmt.setObject(17, homeblock != null ? homeblock.z() : null);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild", e);
        }
    }

    @Override
    public void delete(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String deleteSQL = "DELETE FROM guilds WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild", e);
        }
    }

    @Override
    public Optional<Guild> findById(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String selectSQL = "SELECT * FROM guilds WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM guilds WHERE LOWER(name) = LOWER(?)";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();

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
        String selectSQL = "SELECT * FROM guilds";

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                guilds.add(mapRowToGuild(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all guilds", e);
        }

        return Collections.unmodifiableList(guilds);
    }

    /**
     * Maps a database row to a Guild object.
     */
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

        // Restore members via reflection (Guild doesn't expose member modification)
        restoreMembers(guild, membersJson);

        // Restore spawn location if present
        restoreSpawn(guild, rs);

        // Restore homeblock if present
        restoreHomeblock(guild, rs);

        return guild;
    }

    /**
     * Restores members list from JSON string using reflection.
     */
    private void restoreMembers(Guild guild, String membersJson) throws SQLException {
        try {
            List<UUID> members = deserializeMembers(membersJson);
            var membersField = Guild.class.getDeclaredField("members");
            membersField.setAccessible(true);
            List<UUID> guildMembers = (List<UUID>) membersField.get(guild);
            guildMembers.clear();
            guildMembers.addAll(members);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new SQLException("Failed to restore guild members", e);
        }
    }

    /**
     * Serializes UUID list to comma-separated string.
     */
    private String serializeMembers(List<UUID> members) {
        return members.stream()
            .map(UUID::toString)
            .reduce((a, b) -> a + "," + b)
            .orElse("");
    }

    /**
     * Deserializes comma-separated string to UUID list.
     */
    private List<UUID> deserializeMembers(String membersJson) {
        if (membersJson == null || membersJson.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(membersJson.split(","))
            .map(UUID::fromString)
            .toList();
    }

    /**
     * Restores spawn location from database row.
     */
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

    /**
     * Restores homeblock from database row.
     */
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
}

package org.aincraft.guilds.services.impl;



import com.azoth.territory.model.GovernmentForm;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.Location;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of GuildService with database operations
 */

public class GuildServiceImpl implements org.aincraft.guilds.services.GuildService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final org.aincraft.guilds.services.ResidentService residentService;
    private org.aincraft.guilds.services.PermissionService permissionService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String GUILD_PROGRESS_COLUMNS = "guild_level, tech_points, active_project_id";

    public GuildServiceImpl(DatabaseManager databaseManager, Logger logger,
                         org.aincraft.guilds.services.ResidentService residentService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.residentService = residentService;
    }

    /**
     * Late-bound dependency: PermissionService depends on this service, so the
     * wiring root hands it over after both exist (breaks the service cycle).
     */
    public void setPermissionService(org.aincraft.guilds.services.PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public Guild createGuild(String name, UUID mayorUuid) {
        // Check if guild already exists
        if (guildExists(name)) {
            throw new IllegalArgumentException("Guild already exists: " + name);
        }

        // Use transaction for guild creation
        return databaseManager.executeTransactionWithResult(connection -> {
            try {
                // Insert guild
                String guildSql = "INSERT INTO guilds (id, name, mayor_uuid, balance, is_open, created_at, permissions_flags, tax_rates, " +
                                "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                String guildId = UUID.randomUUID().toString();
                String createdAt = LocalDateTime.now().format(DATE_FORMATTER);

                // Default tax rates as JSON
                String taxRates = "{\"resident\":0.0,\"plot\":0.0,\"shop\":0.0}";

                try (PreparedStatement statement = connection.prepareStatement(guildSql)) {
                    statement.setString(1, guildId);
                    statement.setString(2, name);
                    statement.setString(3, mayorUuid.toString());
                    statement.setDouble(4, 0.0); // Starting balance
                    statement.setBoolean(5, true); // Open for new residents
                    statement.setString(6, createdAt);
                    statement.setInt(7, 0); // Default permission flags
                    statement.setString(8, taxRates);
                    // Set toggle defaults
                    statement.setBoolean(9, true); // PvP enabled by default
                    statement.setBoolean(10, true); // Fire enabled by default
                    statement.setBoolean(11, true); // Explosions enabled by default
                    statement.setBoolean(12, true); // Mobs enabled by default
                    statement.setBoolean(13, false); // Public disabled by default (only residents can build)

                    statement.executeUpdate();
                }

                // Add mayor as resident
                String residentSql = "INSERT INTO guild_residents (guild_id, resident_uuid, role, joined_at) VALUES (?, ?, ?, ?)";

                try (PreparedStatement statement = connection.prepareStatement(residentSql)) {
                    statement.setString(1, guildId);
                    statement.setString(2, mayorUuid.toString());
                    statement.setString(3, "mayor");
                    statement.setString(4, createdAt);

                    statement.executeUpdate();
                }

                // Update resident's guild
                String updateResidentSql = "UPDATE residents SET guild_name = ? WHERE uuid = ?";

                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, name);
                    statement.setString(2, mayorUuid.toString());

                    statement.executeUpdate();
                }

                Guild guild = new Guild(name, mayorUuid);
                guild.setId(guildId);
                guild.setCreatedAt(LocalDateTime.parse(createdAt, DATE_FORMATTER));

                logger.info("Created new guild: " + name + " with mayor: " + mayorUuid);

                return guild;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to create guild: " + name, e);
                throw new RuntimeException("Failed to create guild", e);
            }
        }).orElseThrow(() -> new RuntimeException("Failed to create guild: transaction returned no result"));
    }

    @Override
    public Guild createGuild(String name, UUID mayorUuid, Location homeBlockLocation) {
        // Check if guild already exists
        if (guildExists(name)) {
            throw new IllegalArgumentException("Guild already exists: " + name);
        }

        // Get block coordinates from location
        // GuildBlock stores BLOCK coordinates (not chunk coordinates)
        int[] blockCoords = homeBlockLocation.getBlockCoordinates();
        int blockX = blockCoords[0];
        int blockZ = blockCoords[2]; // blockCoords returns [x, y, z], we need z

        // Calculate chunk for logging
        int[] chunkCoords = homeBlockLocation.getChunkCoordinates();
        int chunkX = chunkCoords[0];
        int chunkZ = chunkCoords[1];

        // Use transaction for guild creation
        return databaseManager.executeTransactionWithResult(connection -> {
            try {
                // Insert guild with home block and spawn (storing BLOCK coordinates)
                String guildSql = "INSERT INTO guilds (id, name, mayor_uuid, balance, is_open, created_at, permissions_flags, tax_rates, " +
                                "home_block_x, home_block_z, home_block_world, " +
                                "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                                "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                String guildId = UUID.randomUUID().toString();
                String createdAt = LocalDateTime.now().format(DATE_FORMATTER);

                // Default tax rates as JSON
                String taxRates = "{\"resident\":0.0,\"plot\":0.0,\"shop\":0.0}";

                try (PreparedStatement statement = connection.prepareStatement(guildSql)) {
                    statement.setString(1, guildId);
                    statement.setString(2, name);
                    statement.setString(3, mayorUuid.toString());
                    statement.setDouble(4, 0.0); // Starting balance
                    statement.setBoolean(5, true); // Open for new residents
                    statement.setString(6, createdAt);
                    statement.setInt(7, 0); // Default permission flags
                    statement.setString(8, taxRates);
                    statement.setInt(9, blockX); // Store block coordinate
                    statement.setInt(10, blockZ); // Store block coordinate
                    statement.setString(11, homeBlockLocation.getWorld());
                    // Set spawn to same location as home block
                    statement.setDouble(12, homeBlockLocation.getX());
                    statement.setDouble(13, homeBlockLocation.getY());
                    statement.setDouble(14, homeBlockLocation.getZ());
                    statement.setDouble(15, homeBlockLocation.getYaw());
                    statement.setDouble(16, homeBlockLocation.getPitch());
                    statement.setString(17, homeBlockLocation.getWorld());
                    // Set toggle defaults
                    statement.setBoolean(18, true); // PvP enabled by default
                    statement.setBoolean(19, true); // Fire enabled by default
                    statement.setBoolean(20, true); // Explosions enabled by default
                    statement.setBoolean(21, true); // Mobs enabled by default
                    statement.setBoolean(22, false); // Public disabled by default (only residents can build)

                    statement.executeUpdate();
                }

                // Add mayor as resident
                String residentSql = "INSERT INTO guild_residents (guild_id, resident_uuid, role, joined_at) VALUES (?, ?, ?, ?)";

                try (PreparedStatement statement = connection.prepareStatement(residentSql)) {
                    statement.setString(1, guildId);
                    statement.setString(2, mayorUuid.toString());
                    statement.setString(3, "mayor");
                    statement.setString(4, createdAt);

                    statement.executeUpdate();
                }

                // Update resident's guild
                String updateResidentSql = "UPDATE residents SET guild_name = ? WHERE uuid = ?";

                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, name);
                    statement.setString(2, mayorUuid.toString());

                    statement.executeUpdate();
                }

                Guild guild = new Guild(name, mayorUuid);
                guild.setId(guildId);
                guild.setCreatedAt(LocalDateTime.parse(createdAt, DATE_FORMATTER));

                // Set home block with BLOCK coordinates
                org.aincraft.guilds.models.GuildBlock homeBlock = new org.aincraft.guilds.models.GuildBlock(
                    blockX, blockZ, homeBlockLocation.getWorld(), guildId
                );
                guild.setHomeBlock(homeBlock);

                // Set spawn location
                guild.setSpawnLocation(homeBlockLocation);

                logger.info("Created new guild: " + name + " with mayor: " + mayorUuid + " at chunk [" + chunkX + ", " + chunkZ + "] with spawn at " + homeBlockLocation.toDisplayString());

                return guild;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to create guild: " + name, e);
                throw new RuntimeException("Failed to create guild", e);
            }
        }).orElseThrow(() -> new RuntimeException("Failed to create guild: transaction returned no result"));
    }

    @Override
    public Optional<Guild> getGuild(String name) {
        // Try query with spawn columns first
        try {
            String sqlWithSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                               "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                               "is_open, created_at, permissions_flags, tax_rates, " +
                               "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled, " +
                               GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE name = ?";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlWithSpawn)) {

                statement.setString(1, name);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Guild guild = mapResultSetToGuild(resultSet);
                        loadGuildResidents(connection, guild);
                        return Optional.of(guild);
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found in getGuild(), using query without spawn columns for guild: " + name);
                try {
                    String sqlWithoutSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                                         "is_open, created_at, permissions_flags, tax_rates, " +
                                         "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled, " +
                                         GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE name = ?";

                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = connection.prepareStatement(sqlWithoutSpawn)) {

                        statement.setString(1, name);

                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Guild guild = mapResultSetToGuildWithoutSpawn(resultSet);
                                loadGuildResidents(connection, guild);
                                return Optional.of(guild);
                            }
                        }
                    }
                } catch (SQLException e2) {
                    logger.log(Level.SEVERE, "Failed to get guild with fallback query: " + name, e2);
                }
            } else {
                logger.log(Level.SEVERE, "Failed to get guild: " + name, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<Guild> getGuild(UUID uuid) {
        logger.info("Looking for guild with UUID: " + uuid.toString());

        // Try query with spawn columns first
        try {
            String sqlWithSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                               "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                               "is_open, created_at, permissions_flags, tax_rates, " +
                               "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled, " +
                               GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE id = ?";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlWithSpawn)) {

                statement.setString(1, uuid.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Guild guild = mapResultSetToGuild(resultSet);
                        loadGuildResidents(connection, guild);
                        logger.info("Found guild: " + guild.getName() + " with ID: " + guild.getId());
                        return Optional.of(guild);
                    }
                }
            }

            logger.info("No guild found with UUID: " + uuid.toString());
        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found in getGuild(UUID), using query without spawn columns");
                try {
                    String sqlWithoutSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                                         "is_open, created_at, permissions_flags, tax_rates, " +
                                         "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled, " +
                               GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE id = ?";

                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = connection.prepareStatement(sqlWithoutSpawn)) {

                        statement.setString(1, uuid.toString());

                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Guild guild = mapResultSetToGuildWithoutSpawn(resultSet);
                                loadGuildResidents(connection, guild);
                                return Optional.of(guild);
                            }
                        }
                    }
                } catch (SQLException e2) {
                    logger.log(Level.SEVERE, "Failed to get guild with fallback query (UUID): " + uuid, e2);
                }
            } else {
                logger.log(Level.SEVERE, "Failed to get guild: " + uuid, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<Guild> getGuildById(String guildId) {
        // Try query with spawn columns first
        try {
            String sqlWithSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                               "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                               "is_open, created_at, permissions_flags, tax_rates, " +
                               "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled, " +
                               GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE id = ?";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlWithSpawn)) {

                statement.setString(1, guildId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Guild guild = mapResultSetToGuild(resultSet);
                        loadGuildResidents(connection, guild);
                        return Optional.of(guild);
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found in getGuildById(), using query without spawn columns");
                try {
                    String sqlWithoutSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                                         "is_open, created_at, permissions_flags, tax_rates, " +
                                         "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled, " +
                               GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE id = ?";

                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = connection.prepareStatement(sqlWithoutSpawn)) {

                        statement.setString(1, guildId);

                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Guild guild = mapResultSetToGuildWithoutSpawn(resultSet);
                                loadGuildResidents(connection, guild);
                                return Optional.of(guild);
                            }
                        }
                    }
                } catch (SQLException e2) {
                    logger.log(Level.SEVERE, "Failed to get guild by ID with fallback query: " + guildId, e2);
                }
            } else {
                logger.log(Level.SEVERE, "Failed to get guild by ID: " + guildId, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public Guild updateGuild(Guild guild) {
        String sql = "UPDATE guilds SET name = ?, mayor_uuid = ?, balance = ?, home_block_x = ?, " +
                    "home_block_z = ?, home_block_world = ?, spawn_x = ?, spawn_y = ?, spawn_z = ?, " +
                    "spawn_yaw = ?, spawn_pitch = ?, spawn_world = ?, is_open = ?, permissions_flags = ?, tax_rates = ?, " +
                    "pvp_enabled = ?, fire_enabled = ?, explosions_enabled = ?, mobs_enabled = ?, public_enabled = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guild.getName());
            statement.setString(2, guild.getMayorUuid().toString());
            statement.setDouble(3, guild.getBalance());

            if (guild.getHomeBlock() != null) {
                statement.setInt(4, guild.getHomeBlock().getX());
                statement.setInt(5, guild.getHomeBlock().getZ());
                statement.setString(6, guild.getHomeBlock().getWorld());
            } else {
                statement.setNull(4, Types.INTEGER);
                statement.setNull(5, Types.INTEGER);
                statement.setNull(6, Types.VARCHAR);
            }

            // Handle spawn location
            if (guild.getSpawnLocation() != null) {
                Location spawn = guild.getSpawnLocation();
                statement.setDouble(7, spawn.getX());
                statement.setDouble(8, spawn.getY());
                statement.setDouble(9, spawn.getZ());
                statement.setDouble(10, spawn.getYaw());
                statement.setDouble(11, spawn.getPitch());
                statement.setString(12, spawn.getWorld());
            } else {
                statement.setNull(7, Types.DOUBLE);
                statement.setNull(8, Types.DOUBLE);
                statement.setNull(9, Types.DOUBLE);
                statement.setNull(10, Types.DOUBLE);
                statement.setNull(11, Types.DOUBLE);
                statement.setNull(12, Types.VARCHAR);
            }

            statement.setBoolean(13, guild.isOpen());
            statement.setInt(14, 0); // Permission flags will be implemented later
            statement.setString(15, "{}"); // Tax rates as JSON

            // Toggle fields
            statement.setBoolean(16, guild.isPvpEnabled());
            statement.setBoolean(17, guild.isFireEnabled());
            statement.setBoolean(18, guild.isExplosionsEnabled());
            statement.setBoolean(19, guild.isMobsEnabled());
            statement.setBoolean(20, guild.isPublicEnabled());

            statement.setString(21, guild.getId());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Updated guild: " + guild.getName());
            }

            return guild;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update guild: " + guild.getName(), e);
            throw new RuntimeException("Failed to update guild", e);
        }
    }

    @Override
    public boolean deleteGuild(String name) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Get guild ID
                String getIdSql = "SELECT id FROM guilds WHERE name = ?";
                String guildId = null;

                try (PreparedStatement statement = connection.prepareStatement(getIdSql)) {
                    statement.setString(1, name);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Guild doesn't exist
                            return;
                        }
                    }
                }

                // Delete all guild blocks (claims) for this guild
                String deleteGuildBlocksSql = "DELETE FROM guild_blocks WHERE guild_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteGuildBlocksSql)) {
                    statement.setString(1, guildId);
                    int blocksDeleted = statement.executeUpdate();
                    logger.info("Deleted " + blocksDeleted + " guild blocks for guild: " + name);
                }

                // Delete guild residents associations
                String deleteGuildResidentsSql = "DELETE FROM guild_residents WHERE guild_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteGuildResidentsSql)) {
                    statement.setString(1, guildId);
                    statement.executeUpdate();
                }

                // Remove residents from guild
                String updateResidentsSql = "UPDATE residents SET guild_name = NULL WHERE guild_name = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateResidentsSql)) {
                    statement.setString(1, name);
                    statement.executeUpdate();
                }

                // Delete guild
                String deleteGuildSql = "DELETE FROM guilds WHERE name = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteGuildSql)) {
                    statement.setString(1, name);
                    int rowsDeleted = statement.executeUpdate();

                    if (rowsDeleted > 0) {
                        logger.info("Deleted guild: " + name);
                        result[0] = true;
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete guild: " + name, e);
                throw new RuntimeException("Failed to delete guild", e);
            }
        });

        return result[0];
    }

    @Override
    public List<Guild> getAllGuilds() {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates, " +
                    GUILD_PROGRESS_COLUMNS + " FROM guilds ORDER BY name";
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Guild guild = mapResultSetToGuild(resultSet);
                guilds.add(guild);
            }

            // Load residents for all guilds
            for (Guild guild : guilds) {
                loadGuildResidents(connection, guild);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all guilds", e);
        }

        return guilds;
    }

    @Override
    public List<Guild> getGuildsByPopulation() {
        // Get guilds with resident counts
        String sql = """
            SELECT t.id, t.name, t.mayor_uuid, t.balance, t.home_block_x, t.home_block_z, t.home_block_world,
                   t.spawn_x, t.spawn_y, t.spawn_z, t.spawn_yaw, t.spawn_pitch, t.spawn_world,
                   t.is_open, t.created_at, t.permissions_flags, t.tax_rates,
                   t.guild_level, t.tech_points, t.active_project_id,
                   COUNT(tr.resident_uuid) as resident_count
            FROM guilds t
            LEFT JOIN guild_residents tr ON t.id = tr.guild_id
            GROUP BY t.id
            ORDER BY resident_count DESC
            """;

        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Guild guild = mapResultSetToGuild(resultSet);
                guilds.add(guild);
            }

            // Load residents for all guilds
            for (Guild guild : guilds) {
                loadGuildResidents(connection, guild);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guilds by population", e);
        }

        return guilds;
    }

    @Override
    public List<Guild> getGuildsByBalance() {
        String sql = """
            SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world,
                   spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world,
                   is_open, created_at, permissions_flags, tax_rates,
                   guild_level, tech_points, active_project_id
            FROM guilds
            ORDER BY balance DESC
            """;

        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Guild guild = mapResultSetToGuild(resultSet);
                guilds.add(guild);
            }

            // Load residents for all guilds
            for (Guild guild : guilds) {
                loadGuildResidents(connection, guild);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guilds by balance", e);
        }

        return guilds;
    }

    @Override
    public boolean guildExists(String name) {
        String sql = "SELECT COUNT(*) FROM guilds WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if guild exists: " + name, e);
        }

        return false;
    }

    @Override
    public GovernmentForm getGovernanceForm(String guildId) {
        String sql = "SELECT governance_form FROM guilds WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return GovernmentForm.fromString(resultSet.getString("governance_form"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read governance form for guild " + guildId, e);
        }
        return GovernmentForm.MONARCHY;
    }

    @Override
    public boolean addResidentToGuild(String guildName, UUID residentUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Get guild ID
                String getGuildIdSql = "SELECT id FROM guilds WHERE name = ?";
                String guildId = null;

                try (PreparedStatement statement = connection.prepareStatement(getGuildIdSql)) {
                    statement.setString(1, guildName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Guild doesn't exist
                            return;
                        }
                    }
                }

                // Check if resident already in guild
                String checkResidentSql = "SELECT COUNT(*) FROM guild_residents WHERE guild_id = ? AND resident_uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(checkResidentSql)) {
                    statement.setString(1, guildId);
                    statement.setString(2, residentUuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next() && resultSet.getInt(1) > 0) {
                            result[0] = false; // Already in guild
                            return;
                        }
                    }
                }

                // Add resident to guild
                String insertSql = "INSERT INTO guild_residents (guild_id, resident_uuid, role, joined_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                    statement.setString(1, guildId);
                    statement.setString(2, residentUuid.toString());
                    statement.setString(3, "resident");
                    statement.setString(4, LocalDateTime.now().format(DATE_FORMATTER));

                    statement.executeUpdate();
                }

                // Update resident's guild name
                String updateResidentSql = "UPDATE residents SET guild_name = ? WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, guildName);
                    statement.setString(2, residentUuid.toString());

                    statement.executeUpdate();
                }

                logger.info("Added resident " + residentUuid + " to guild " + guildName);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to add resident to guild: " + guildName, e);
                throw new RuntimeException("Failed to add resident to guild", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean removeResidentFromGuild(String guildName, UUID residentUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Get guild ID
                String getGuildIdSql = "SELECT id FROM guilds WHERE name = ?";
                String guildId = null;

                try (PreparedStatement statement = connection.prepareStatement(getGuildIdSql)) {
                    statement.setString(1, guildName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Guild doesn't exist
                            return;
                        }
                    }
                }

                // Remove resident from guild
                String deleteSql = "DELETE FROM guild_residents WHERE guild_id = ? AND resident_uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                    statement.setString(1, guildId);
                    statement.setString(2, residentUuid.toString());

                    statement.executeUpdate();
                }

                // Update resident's guild name
                String updateResidentSql = "UPDATE residents SET guild_name = NULL WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, residentUuid.toString());

                    statement.executeUpdate();
                }

                logger.info("Removed resident " + residentUuid + " from guild " + guildName);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to remove resident from guild: " + guildName, e);
                throw new RuntimeException("Failed to remove resident from guild", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean setGuildMayor(String guildName, UUID mayorUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Update guild's mayor
                String updateGuildSql = "UPDATE guilds SET mayor_uuid = ? WHERE name = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateGuildSql)) {
                    statement.setString(1, mayorUuid.toString());
                    statement.setString(2, guildName);

                    int rowsUpdated = statement.executeUpdate();

                    if (rowsUpdated == 0) {
                        result[0] = false; // Guild doesn't exist
                        return;
                    }
                }

                // Update resident's role in guild
                String updateRoleSql = "UPDATE guild_residents SET role = 'mayor' WHERE guild_id = (SELECT id FROM guilds WHERE name = ?) AND resident_uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateRoleSql)) {
                    statement.setString(1, guildName);
                    statement.setString(2, mayorUuid.toString());

                    statement.executeUpdate();
                }

                logger.info("Set mayor for guild " + guildName + ": " + mayorUuid);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to set mayor for guild: " + guildName, e);
                throw new RuntimeException("Failed to set mayor for guild", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean addGuildAssistant(String guildName, UUID assistantUuid) {
        String sql = "UPDATE guild_residents SET role = 'assistant' WHERE guild_id = (SELECT id FROM guilds WHERE name = ?) AND resident_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);
            statement.setString(2, assistantUuid.toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Added assistant to guild " + guildName + ": " + assistantUuid);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to add assistant to guild: " + guildName, e);
        }

        return false;
    }

    @Override
    public boolean removeGuildAssistant(String guildName, UUID assistantUuid) {
        String sql = "UPDATE guild_residents SET role = 'resident' WHERE guild_id = (SELECT id FROM guilds WHERE name = ?) AND resident_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);
            statement.setString(2, assistantUuid.toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Removed assistant from guild " + guildName + ": " + assistantUuid);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to remove assistant from guild: " + guildName, e);
        }

        return false;
    }

    @Override
    public int getGuildResidentCount(String guildName) {
        String sql = "SELECT COUNT(*) FROM guild_residents WHERE guild_id = (SELECT id FROM guilds WHERE name = ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident count for guild: " + guildName, e);
        }

        return 0;
    }

    @Override
    public double updateGuildBalance(String guildName, double amount) {
        String sql = "UPDATE guilds SET balance = balance + ? WHERE name = ? RETURNING balance";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDouble(1, amount);
            statement.setString(2, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    double newBalance = resultSet.getDouble(1);
                    logger.info("Updated balance for guild " + guildName + ": " + amount + " (new total: " + newBalance + ")");
                    return newBalance;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update balance for guild: " + guildName, e);
        }

        return 0;
    }

    @Override
    public List<Guild> getOpenGuilds() {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates, " +
                    GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE is_open = TRUE ORDER BY name";
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Guild guild = mapResultSetToGuild(resultSet);
                guilds.add(guild);
            }

            // Load residents for all guilds
            for (Guild guild : guilds) {
                loadGuildResidents(connection, guild);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get open guilds", e);
        }

        return guilds;
    }

    @Override
    public List<Guild> searchGuilds(String query) {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates, " +
                    GUILD_PROGRESS_COLUMNS + " FROM guilds WHERE name LIKE ? ORDER BY name";
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, "%" + query + "%");

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Guild guild = mapResultSetToGuild(resultSet);
                    guilds.add(guild);
                }
            }

            // Load residents for all guilds
            for (Guild guild : guilds) {
                loadGuildResidents(connection, guild);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to search guilds: " + query, e);
        }

        return guilds;
    }

    /**
     * Map a ResultSet to a Guild object
     */
    private Guild mapResultSetToGuild(ResultSet resultSet) throws SQLException {
        String id = resultSet.getString("id");
        String name = resultSet.getString("name");
        UUID mayorUuid = UUID.fromString(resultSet.getString("mayor_uuid"));
        double balance = resultSet.getDouble("balance");
        boolean isOpen = resultSet.getBoolean("is_open");
        String createdAtStr = resultSet.getString("created_at");

        Guild guild = new Guild(name, mayorUuid);
        guild.setId(id);
        guild.setBalance(balance);
        guild.setOpen(isOpen);

        // Handle home block
        try {
            int homeBlockX = resultSet.getInt("home_block_x");
            boolean xNull = resultSet.wasNull();
            int homeBlockZ = resultSet.getInt("home_block_z");
            boolean zNull = resultSet.wasNull();
            String homeBlockWorld = resultSet.getString("home_block_world");

            if (!xNull && !zNull && homeBlockWorld != null) {
                org.aincraft.guilds.models.GuildBlock homeBlock = new org.aincraft.guilds.models.GuildBlock(
                    homeBlockX, homeBlockZ, homeBlockWorld, id
                );
                guild.setHomeBlock(homeBlock);
            }
        } catch (SQLException e) {
            // Home block columns might not exist, just skip
            logger.fine("Could not load home block for guild: " + name + " - " + e.getMessage());
        }

        // Handle spawn location - use wasNull() to check for NULL values
        try {
            double spawnX = resultSet.getDouble("spawn_x");
            boolean xNull = resultSet.wasNull();
            double spawnY = resultSet.getDouble("spawn_y");
            boolean yNull = resultSet.wasNull();
            double spawnZ = resultSet.getDouble("spawn_z");
            boolean zNull = resultSet.wasNull();
            float spawnYaw = resultSet.getFloat("spawn_yaw");
            boolean yawNull = resultSet.wasNull();
            float spawnPitch = resultSet.getFloat("spawn_pitch");
            boolean pitchNull = resultSet.wasNull();
            String spawnWorld = resultSet.getString("spawn_world");

            if (!xNull && !yNull && !zNull && spawnWorld != null) {
                Location spawnLocation = new Location(
                    spawnX,
                    spawnY,
                    spawnZ,
                    yawNull ? 0.0f : spawnYaw,
                    pitchNull ? 0.0f : spawnPitch,
                    spawnWorld
                );
                guild.setSpawnLocation(spawnLocation);
            }
        } catch (SQLException e) {
            // Spawn columns might not exist or have issues, just skip
            logger.fine("Could not load spawn location for guild: " + name + " - " + e.getMessage());
        }

        if (createdAtStr != null) {
            guild.setCreatedAt(LocalDateTime.parse(createdAtStr, DATE_FORMATTER));
        }

        // Handle toggle fields (new in migration v6)
        try {
            guild.setPvpEnabled(resultSet.getBoolean("pvp_enabled"));
            guild.setFireEnabled(resultSet.getBoolean("fire_enabled"));
            guild.setExplosionsEnabled(resultSet.getBoolean("explosions_enabled"));
            guild.setMobsEnabled(resultSet.getBoolean("mobs_enabled"));
            guild.setPublicEnabled(resultSet.getBoolean("public_enabled"));
        } catch (SQLException e) {
            // Toggle columns might not exist yet, use defaults
            logger.fine("Could not load toggle fields for guild: " + name + " - " + e.getMessage());
            // Guild constructor already sets default values
        }

        loadLevelAndProjectColumns(resultSet, guild, name);

        return guild;
    }

    /**
     * Map a ResultSet to a Guild object (without spawn columns for fallback)
     */
    private Guild mapResultSetToGuildWithoutSpawn(ResultSet resultSet) throws SQLException {
        String id = resultSet.getString("id");
        String name = resultSet.getString("name");
        UUID mayorUuid = UUID.fromString(resultSet.getString("mayor_uuid"));
        double balance = resultSet.getDouble("balance");
        boolean isOpen = resultSet.getBoolean("is_open");
        String createdAtStr = resultSet.getString("created_at");

        Guild guild = new Guild(name, mayorUuid);
        guild.setId(id);
        guild.setBalance(balance);
        guild.setOpen(isOpen);

        // Handle home block
        try {
            int homeBlockX = resultSet.getInt("home_block_x");
            boolean xNull = resultSet.wasNull();
            int homeBlockZ = resultSet.getInt("home_block_z");
            boolean zNull = resultSet.wasNull();
            String homeBlockWorld = resultSet.getString("home_block_world");

            if (!xNull && !zNull && homeBlockWorld != null) {
                org.aincraft.guilds.models.GuildBlock homeBlock = new org.aincraft.guilds.models.GuildBlock(
                    homeBlockX, homeBlockZ, homeBlockWorld, id
                );
                guild.setHomeBlock(homeBlock);
            }
        } catch (SQLException e) {
            // Home block columns might not exist, just skip
            logger.fine("Could not load home block for guild: " + name + " - " + e.getMessage());
        }

        // Note: spawn location will be null, getGuildSpawn() will use fallback logic

        if (createdAtStr != null) {
            guild.setCreatedAt(LocalDateTime.parse(createdAtStr, DATE_FORMATTER));
        }

        // Handle toggle fields (new in migration v6)
        try {
            guild.setPvpEnabled(resultSet.getBoolean("pvp_enabled"));
            guild.setFireEnabled(resultSet.getBoolean("fire_enabled"));
            guild.setExplosionsEnabled(resultSet.getBoolean("explosions_enabled"));
            guild.setMobsEnabled(resultSet.getBoolean("mobs_enabled"));
            guild.setPublicEnabled(resultSet.getBoolean("public_enabled"));
        } catch (SQLException e) {
            // Toggle columns might not exist yet, use defaults
            logger.fine("Could not load toggle fields for guild: " + name + " - " + e.getMessage());
            // Guild constructor already sets default values
        }

        loadLevelAndProjectColumns(resultSet, guild, name);

        return guild;
    }

    private void loadLevelAndProjectColumns(ResultSet resultSet, Guild guild, String name) {
        try {
            int level = resultSet.getInt("guild_level");
            if (!resultSet.wasNull()) {
                guild.setGuildLevel(level);
            }
            int points = resultSet.getInt("tech_points");
            if (!resultSet.wasNull()) {
                guild.setTechPoints(points);
            }
            guild.setActiveProjectId(resultSet.getString("active_project_id"));
        } catch (SQLException e) {
            logger.fine("Could not load level/project fields for guild: " + name + " - " + e.getMessage());
        }
    }

    /**
     * Load residents for a guild
     */
    private void loadGuildResidents(Connection connection, Guild guild) throws SQLException {
        String sql = "SELECT resident_uuid, role FROM guild_residents WHERE guild_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guild.getId());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID residentUuid = UUID.fromString(resultSet.getString("resident_uuid"));
                    String role = resultSet.getString("role");

                    guild.getResidents().add(residentUuid);

                    if ("assistant".equals(role)) {
                        guild.getAssistants().add(residentUuid);
                    }
                }
            }
        }
    }

    @Override
    public boolean setGuildSpawn(String guildName, Location location) {
        // First, validate that the spawn location is within the guild's home block chunk
        Optional<Guild> guildOpt = getGuild(guildName);
        if (guildOpt.isEmpty()) {
            logger.warning("Cannot set spawn - guild does not exist: " + guildName);
            return false;
        }

        Guild guild = guildOpt.get();
        if (guild.getHomeBlock() == null) {
            logger.warning("Cannot set spawn - guild does not have a home block set: " + guildName);
            return false;
        }

        // Get the chunk coordinates for both the spawn location and home block
        int[] spawnChunk = location.getChunkCoordinates();
        int[] homeBlockChunk = guild.getHomeBlock().getChunkCoordinates();

        // Validate spawn is in home block chunk
        if (spawnChunk[0] != homeBlockChunk[0] || spawnChunk[1] != homeBlockChunk[1]) {
            logger.warning("Cannot set spawn - spawn must be in guild's home block chunk. " +
                    "Spawn chunk: [" + spawnChunk[0] + ", " + spawnChunk[1] + "], " +
                    "Home block chunk: [" + homeBlockChunk[0] + ", " + homeBlockChunk[1] + "]");
            return false;
        }

        // Validate world matches
        if (!location.getWorld().equals(guild.getHomeBlock().getWorld())) {
            logger.warning("Cannot set spawn - spawn must be in the same world as home block");
            return false;
        }

        String sql = "UPDATE guilds SET spawn_x = ?, spawn_y = ?, spawn_z = ?, spawn_yaw = ?, spawn_pitch = ?, spawn_world = ? WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDouble(1, location.getX());
            statement.setDouble(2, location.getY());
            statement.setDouble(3, location.getZ());
            statement.setDouble(4, location.getYaw());
            statement.setDouble(5, location.getPitch());
            statement.setString(6, location.getWorld());
            statement.setString(7, guildName);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Set spawn for guild " + guildName + ": " + location.toDisplayString());
                return true;
            }

        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.warning("Cannot set spawn - spawn columns don't exist. Database migration may not have run: " + guildName);
                // Fallback: update home_block instead
                return setHomeBlockAsSpawnFallback(guildName, location);
            } else {
                logger.log(Level.SEVERE, "Failed to set spawn for guild: " + guildName, e);
            }
        }

        return false;
    }

    /**
     * Fallback method to set home_block as spawn if spawn columns don't exist
     */
    private boolean setHomeBlockAsSpawnFallback(String guildName, Location location) {
        logger.info("Setting home_block fallback spawn for guild " + guildName + " at location: " + location.toDisplayString());

        // Try with home_block_y first
        String sqlWithY = "UPDATE guilds SET home_block_x = ?, home_block_z = ?, home_block_y = ?, home_block_world = ? WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlWithY)) {

            // Convert to block coordinates
            int blockX = (int) Math.floor(location.getX());
            int blockY = (int) Math.floor(location.getY());
            int blockZ = (int) Math.floor(location.getZ());

            logger.info("Converted location to blocks: x=" + blockX + ", y=" + blockY + ", z=" + blockZ);

            statement.setInt(1, blockX);
            statement.setInt(2, blockZ);
            statement.setInt(3, blockY);
            statement.setString(4, location.getWorld());
            statement.setString(5, guildName);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("SUCCESS: Set home_block as fallback spawn for guild " + guildName + ": " + blockX + ", " + blockY + ", " + blockZ);
                return true;
            } else {
                logger.warning("FAILED: No rows updated for guild " + guildName);
            }

        } catch (SQLException e) {
            // If home_block_y column doesn't exist, try without it
            if (e.getMessage().contains("no such column")) {
                logger.info("home_block_y column not found, using fallback without Y coordinate: " + e.getMessage());
                return setHomeBlockAsSpawnFallbackWithoutY(guildName, location);
            } else {
                logger.log(Level.SEVERE, "SQL Error setting home_block fallback for guild: " + guildName, e);
            }
        }

        return false;
    }

    /**
     * Fallback method to set home_block without Y coordinate
     */
    private boolean setHomeBlockAsSpawnFallbackWithoutY(String guildName, Location location) {
        logger.info("Setting home_block fallback WITHOUT Y for guild " + guildName + " at location: " + location.toDisplayString());

        String sql = "UPDATE guilds SET home_block_x = ?, home_block_z = ?, home_block_world = ? WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // Convert to block coordinates
            int blockX = (int) Math.floor(location.getX());
            int blockZ = (int) Math.floor(location.getZ());

            logger.info("Converted location to blocks (no Y): x=" + blockX + ", z=" + blockZ);

            statement.setInt(1, blockX);
            statement.setInt(2, blockZ);
            statement.setString(3, location.getWorld());
            statement.setString(4, guildName);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("SUCCESS: Set home_block as fallback spawn for guild " + guildName + ": " + blockX + ", " + blockZ + " (no Y saved)");
                return true;
            } else {
                logger.warning("FAILED: No rows updated for guild " + guildName + " (no Y)");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error setting home_block fallback for guild (without Y): " + guildName, e);
        }

        return false;
    }

    @Override
    public Optional<Location> getGuildSpawn(String guildName) {
        logger.info("Getting spawn for guild: " + guildName);

        String sql = "SELECT spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world FROM guilds WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    logger.info("Found spawn columns data for guild: " + guildName);

                    // Use wasNull() to properly check for NULL values
                    double spawnX = resultSet.getDouble("spawn_x");
                    boolean xNull = resultSet.wasNull();
                    double spawnY = resultSet.getDouble("spawn_y");
                    boolean yNull = resultSet.wasNull();
                    double spawnZ = resultSet.getDouble("spawn_z");
                    boolean zNull = resultSet.wasNull();
                    float spawnYaw = resultSet.getFloat("spawn_yaw");
                    boolean yawNull = resultSet.wasNull();
                    float spawnPitch = resultSet.getFloat("spawn_pitch");
                    boolean pitchNull = resultSet.wasNull();
                    String spawnWorld = resultSet.getString("spawn_world");

                    logger.info("Spawn data - X:" + (xNull ? "NULL" : spawnX) + " Y:" + (yNull ? "NULL" : spawnY) + " Z:" + (zNull ? "NULL" : spawnZ) + " World:" + spawnWorld);

                    if (!xNull && !yNull && !zNull && spawnWorld != null) {
                        Location spawnLocation = new Location(
                            spawnX,
                            spawnY,
                            spawnZ,
                            yawNull ? 0.0f : spawnYaw,
                            pitchNull ? 0.0f : spawnPitch,
                            spawnWorld
                        );
                        logger.info("Returning spawn location: " + spawnLocation.toDisplayString());
                        return Optional.of(spawnLocation);
                    } else {
                        logger.warning("Spawn columns exist but some are null for guild: " + guildName);
                    }
                } else {
                    logger.info("No spawn data found for guild: " + guildName);
                }
            }

        } catch (SQLException e) {
            // If spawn columns don't exist, try to use home_block as fallback
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found, using home_block as fallback for guild: " + guildName + " - " + e.getMessage());
                return getHomeBlockAsSpawn(guildName);
            } else {
                logger.log(Level.SEVERE, "Failed to get spawn for guild: " + guildName, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean canTeleportToSpawn(UUID playerUuid, String guildName) {
        // Check if guild exists
        if (!guildExists(guildName)) {
            return false;
        }

        // Check if guild has a spawn set
        Optional<Location> spawnLocation = getGuildSpawn(guildName);
        if (spawnLocation.isEmpty()) {
            return false;
        }

        // Check if player is a resident of the guild
        Optional<Guild> guild = getGuild(guildName);
        if (guild.isEmpty()) {
            return false;
        }

        // Allow teleportation if:
        // 1. Player is a resident of the guild
        // 2. Guild is open to public
        // 3. Player has permission (could be extended with permission system)

        return guild.get().isResident(playerUuid) || guild.get().isOpen();
    }

    /**
     * Fallback method to use home_block as spawn if spawn columns don't exist
     */
    private Optional<Location> getHomeBlockAsSpawn(String guildName) {
        // Try with home_block_y first
        String sqlWithY = "SELECT home_block_x, home_block_y, home_block_z, home_block_world FROM guilds WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlWithY)) {

            statement.setString(1, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Integer homeBlockX = resultSet.getObject("home_block_x", Integer.class);
                    Integer homeBlockY = resultSet.getObject("home_block_y", Integer.class);
                    Integer homeBlockZ = resultSet.getObject("home_block_z", Integer.class);
                    String homeBlockWorld = resultSet.getString("home_block_world");

                    if (homeBlockX != null && homeBlockZ != null && homeBlockWorld != null) {
                        // Use saved coordinates, with Y coordinate if available
                        double spawnY = (homeBlockY != null) ? homeBlockY + 0.5 : 0.0;

                        Location spawnLocation = new Location(
                            homeBlockX + 0.5,  // Center of block
                            spawnY,            // Use saved Y (player's exact position)
                            homeBlockZ + 0.5,  // Center of block
                            0.0f,              // Default yaw
                            0.0f,              // Default pitch
                            homeBlockWorld
                        );
                        return Optional.of(spawnLocation);
                    }
                }
            }

        } catch (SQLException e) {
            // If home_block_y column doesn't exist, try without it
            if (e.getMessage().contains("no such column")) {
                logger.info("home_block_y column not found, using fallback without Y coordinate");
                return getHomeBlockAsSpawnWithoutY(guildName);
            } else {
                logger.log(Level.SEVERE, "Failed to get home block for guild: " + guildName, e);
            }
        }

        return Optional.empty();
    }

    /**
     * Fallback method to use home_block without Y coordinate
     */
    private Optional<Location> getHomeBlockAsSpawnWithoutY(String guildName) {
        String sql = "SELECT home_block_x, home_block_z, home_block_world FROM guilds WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Integer homeBlockX = resultSet.getObject("home_block_x", Integer.class);
                    Integer homeBlockZ = resultSet.getObject("home_block_z", Integer.class);
                    String homeBlockWorld = resultSet.getString("home_block_world");

                    if (homeBlockX != null && homeBlockZ != null && homeBlockWorld != null) {
                        // Use center of block at ground level if no Y saved
                        Location spawnLocation = new Location(
                            homeBlockX + 0.5,  // Center of block
                            0.0,               // Ground level, player can fall safely
                            homeBlockZ + 0.5,  // Center of block
                            0.0f,              // Default yaw
                            0.0f,              // Default pitch
                            homeBlockWorld
                        );
                        return Optional.of(spawnLocation);
                    }
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get home block without Y for guild: " + guildName, e);
        }

        return Optional.empty();
    }

    // Guild level system methods - basic implementations for compilation

    @Override
    public List<org.aincraft.guilds.models.Guild> getGuildsByLevel() {
        List<org.aincraft.guilds.models.Guild> guilds = getAllGuilds();
        guilds.sort((t1, t2) -> Integer.compare(t2.getGuildLevel(), t1.getGuildLevel()));
        return guilds;
    }

    @Override
    public List<org.aincraft.guilds.models.Guild> getGuildsByLevelRange(int minLevel, int maxLevel) {
        return getAllGuilds().stream()
                .filter(guild -> guild.getGuildLevel() >= minLevel && guild.getGuildLevel() <= maxLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<org.aincraft.guilds.models.Guild> getGuildsByMinimumLevel(int minimumLevel) {
        return getAllGuilds().stream()
                .filter(guild -> guild.getGuildLevel() >= minimumLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<org.aincraft.guilds.models.Guild> getGuildsByTechPoints() {
        List<org.aincraft.guilds.models.Guild> guilds = getAllGuilds();
        guilds.sort((t1, t2) -> Integer.compare(t2.getTechPoints(), t1.getTechPoints()));
        return guilds;
    }

    @Override
    public int getTotalTechPoints() {
        return getAllGuilds().stream()
                .mapToInt(org.aincraft.guilds.models.Guild::getTechPoints)
                .sum();
    }

    @Override
    public GuildStatistics getGuildStatistics() {
        List<org.aincraft.guilds.models.Guild> guilds = getAllGuilds();

        int totalGuilds = guilds.size();
        double averageLevel = guilds.stream()
                .mapToInt(org.aincraft.guilds.models.Guild::getGuildLevel)
                .average()
                .orElse(0.0);
        int maxLevel = guilds.stream()
                .mapToInt(org.aincraft.guilds.models.Guild::getGuildLevel)
                .max()
                .orElse(0);
        int totalTechPoints = getTotalTechPoints();
        int totalResidents = guilds.stream()
                .mapToInt(org.aincraft.guilds.models.Guild::getResidentCount)
                .sum();
        double totalBalance = guilds.stream()
                .mapToDouble(org.aincraft.guilds.models.Guild::getBalance)
                .sum();

        // Simple level distribution (1-10, 11-20, etc.)
        java.util.Map<String, Integer> levelDistribution = new java.util.HashMap<>();
        for (org.aincraft.guilds.models.Guild guild : guilds) {
            int level = guild.getGuildLevel();
            String range = ((level - 1) / 10 * 10 + 1) + "-" + ((level - 1) / 10 * 10 + 10);
            levelDistribution.put(range, levelDistribution.getOrDefault(range, 0) + 1);
        }

        return new GuildStatistics(totalGuilds, (int) averageLevel, maxLevel,
                                 totalTechPoints, totalResidents, totalBalance, levelDistribution);
    }

    @Override
    public boolean updateGuildLevel(String guildName, int newLevel, int techPoints) {
        Optional<org.aincraft.guilds.models.Guild> guildOpt = getGuild(guildName);
        if (guildOpt.isEmpty()) {
            return false;
        }

        org.aincraft.guilds.models.Guild guild = guildOpt.get();
        guild.setGuildLevel(newLevel);
        guild.addTechPoints(techPoints);

        return updateGuild(guild) != null;
    }

    @Override
    public boolean updateGuildUpgradeProgress(String guildName, java.util.Map<String, Integer> upgradeProgress) {
        Optional<org.aincraft.guilds.models.Guild> guildOpt = getGuild(guildName);
        if (guildOpt.isEmpty()) {
            return false;
        }

        org.aincraft.guilds.models.Guild guild = guildOpt.get();
        guild.setUpgradeProgress(upgradeProgress);

        return updateGuild(guild) != null;
    }

    @Override
    public List<org.aincraft.guilds.models.Guild> getRankedGuilds(String criteria, int limit) {
        List<org.aincraft.guilds.models.Guild> guilds = getAllGuilds();

        switch (criteria.toLowerCase()) {
            case "level":
                guilds.sort((t1, t2) -> Integer.compare(t2.getGuildLevel(), t1.getGuildLevel()));
                break;
            case "residents":
                guilds.sort((t1, t2) -> Integer.compare(t2.getResidentCount(), t1.getResidentCount()));
                break;
            case "balance":
                guilds.sort((t1, t2) -> Double.compare(t2.getBalance(), t1.getBalance()));
                break;
            case "techpoints":
                guilds.sort((t1, t2) -> Integer.compare(t2.getTechPoints(), t1.getTechPoints()));
                break;
            default:
                // Default to level
                guilds.sort((t1, t2) -> Integer.compare(t2.getGuildLevel(), t1.getGuildLevel()));
                break;
        }

        return guilds.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<org.aincraft.guilds.models.Guild> getTopLevelGuilds(int limit) {
        return getRankedGuilds("level", limit);
    }

    @Override
    public List<org.aincraft.guilds.models.Guild> getGuildsReadyForUpgrade() {
        // Basic implementation - would need GuildLevelService integration for real functionality
        return java.util.Collections.emptyList();
    }

    @Override
    public double getAverageGuildLevel() {
        return getAllGuilds().stream()
                .mapToInt(org.aincraft.guilds.models.Guild::getGuildLevel)
                .average()
                .orElse(0.0);
    }

    // Guild toggle system implementation

    @Override
    public boolean toggleGuildPermission(String guildName, String permissionType, UUID playerUuid) {
        try {
            Optional<org.aincraft.guilds.models.Guild> guildOpt = getGuild(guildName);
            if (guildOpt.isEmpty()) {
                logger.warning("Cannot toggle permission - guild does not exist: " + guildName);
                return false;
            }

            org.aincraft.guilds.models.Guild guild = guildOpt.get();

            // Check if player has permission to toggle guild settings
            if (!permissionService.hasGuildAdmin(playerUuid, guildName)) {
                logger.warning("Player " + playerUuid + " attempted to toggle guild permission without admin rights: " + guildName);
                return false;
            }

            // Toggle the permission using the guild's method
            boolean currentState = guild.getToggle(permissionType);
            boolean newValue = !currentState;
            guild.setToggle(permissionType, newValue);

            // Save the updated guild to database
            return updateGuild(guild) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to toggle guild permission: " + permissionType + " for guild: " + guildName, e);
            return false;
        }
    }

    @Override
    public java.util.Map<String, Boolean> getGuildToggles(String guildName) {
        try {
            Optional<org.aincraft.guilds.models.Guild> guildOpt = getGuild(guildName);
            if (guildOpt.isEmpty()) {
                return new HashMap<>();
            }

            return guildOpt.get().getAllToggles();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get guild toggles for guild: " + guildName, e);
            return new HashMap<>();
        }
    }

    @Override
    public boolean setGuildToggle(String guildName, String permissionType, boolean value, UUID playerUuid) {
        try {
            Optional<org.aincraft.guilds.models.Guild> guildOpt = getGuild(guildName);
            if (guildOpt.isEmpty()) {
                logger.warning("Cannot set toggle - guild does not exist: " + guildName);
                return false;
            }

            org.aincraft.guilds.models.Guild guild = guildOpt.get();

            // Check if player has permission to set guild settings
            if (!permissionService.hasGuildAdmin(playerUuid, guildName)) {
                logger.warning("Player " + playerUuid + " attempted to set guild toggle without admin rights: " + guildName);
                return false;
            }

            // Set the toggle using the guild's method
            boolean success = guild.setToggle(permissionType, value);
            if (!success) {
                logger.warning("Invalid toggle type: " + permissionType + " for guild: " + guildName);
                return false;
            }

            // Save the updated guild to database
            return updateGuild(guild) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set guild toggle: " + permissionType + " for guild: " + guildName, e);
            return false;
        }
    }

    @Override
    public boolean getGuildToggle(String guildName, String permissionType) {
        try {
            Optional<org.aincraft.guilds.models.Guild> guildOpt = getGuild(guildName);
            if (guildOpt.isEmpty()) {
                return false;
            }

            return guildOpt.get().getToggle(permissionType);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get guild toggle: " + permissionType + " for guild: " + guildName, e);
            return false;
        }
    }
}
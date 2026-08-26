package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.territory.model.GovernmentForm;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.Location;
import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.sql.SqlParams;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of GuildService with database operations
 */

public class GuildServiceImpl implements dev.mintychochip.guilds.services.GuildService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The data source. */
    private final DataSource dataSource;
    /** The logger. */
    private final Logger logger;
    /** The resident service. */
    private final dev.mintychochip.guilds.services.ResidentService residentService;
    /** The permission service. */
    private dev.mintychochip.guilds.services.PermissionService permissionService;

    /** The date formatter constant. */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new guild service impl instance.
     * @param databaseManager the database manager
     * @param logger the logger
     * @param residentService the resident service
     */
    public GuildServiceImpl(DatabaseManager databaseManager, Logger logger,
                         dev.mintychochip.guilds.services.ResidentService residentService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.residentService = residentService;
    }

    /**
     * Late-bound dependency: PermissionService depends on this service, so the
     * wiring root hands it over after both exist (breaks the service cycle).
     */
    public void setPermissionService(dev.mintychochip.guilds.services.PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Creates a new guild.
     * @param name the name
     * @param mayorUuid the mayor uuid
     * @return the result
     */
    @Override
    public Guild createGuild(String name, UUID mayorUuid) {
        // Check if guild already exists
        if (guildExists(name)) {
            throw new IllegalArgumentException("Guild already exists: " + name);
        }

        // Use transaction for guild creation
        return databaseManager.executeTransactionWithResult(connection -> {
            try {
                String guildId = UUID.randomUUID().toString();
                String createdAt = LocalDateTime.now().format(DATE_FORMATTER);

                // Default tax rates as JSON
                String taxRates = "{\"resident\":0.0,\"plot\":0.0,\"shop\":0.0}";

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/insert.sql", SqlParams.of(
                        "id", guildId,
                        "name", name,
                        "mayor_uuid", mayorUuid.toString(),
                        "balance", 0.0,
                        "is_open", true,
                        "created_at", createdAt,
                        "permissions_flags", 0,
                        "tax_rates", taxRates,
                        "pvp_enabled", true,
                        "fire_enabled", true,
                        "explosions_enabled", true,
                        "mobs_enabled", true,
                        "public_enabled", false))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/insert-resident.sql", Map.of(
                        "guild_id", guildId,
                        "resident_uuid", mayorUuid.toString(),
                        "role", "mayor",
                        "joined_at", createdAt))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/update-resident-guild.sql", Map.of(
                        "guild_name", name,
                        "uuid", mayorUuid.toString()))) {
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

    /**
     * Creates a new guild.
     * @param name the name
     * @param mayorUuid the mayor uuid
     * @param homeBlockLocation the home block location
     * @return the result
     */
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
                String guildId = UUID.randomUUID().toString();
                String createdAt = LocalDateTime.now().format(DATE_FORMATTER);

                // Default tax rates as JSON
                String taxRates = "{\"resident\":0.0,\"plot\":0.0,\"shop\":0.0}";

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/insert-with-home.sql", SqlParams.of(
                        "id", guildId,
                        "name", name,
                        "mayor_uuid", mayorUuid.toString(),
                        "balance", 0.0,
                        "is_open", true,
                        "created_at", createdAt,
                        "permissions_flags", 0,
                        "tax_rates", taxRates,
                        "home_block_x", blockX,
                        "home_block_z", blockZ,
                        "home_block_world", homeBlockLocation.getWorld(),
                        "spawn_x", homeBlockLocation.getX(),
                        "spawn_y", homeBlockLocation.getY(),
                        "spawn_z", homeBlockLocation.getZ(),
                        "spawn_yaw", homeBlockLocation.getYaw(),
                        "spawn_pitch", homeBlockLocation.getPitch(),
                        "spawn_world", homeBlockLocation.getWorld(),
                        "pvp_enabled", true,
                        "fire_enabled", true,
                        "explosions_enabled", true,
                        "mobs_enabled", true,
                        "public_enabled", false))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/insert-resident.sql", Map.of(
                        "guild_id", guildId,
                        "resident_uuid", mayorUuid.toString(),
                        "role", "mayor",
                        "joined_at", createdAt))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/update-resident-guild.sql", Map.of(
                        "guild_name", name,
                        "uuid", mayorUuid.toString()))) {
                    statement.executeUpdate();
                }

                Guild guild = new Guild(name, mayorUuid);
                guild.setId(guildId);
                guild.setCreatedAt(LocalDateTime.parse(createdAt, DATE_FORMATTER));

                // Set home block with BLOCK coordinates
                dev.mintychochip.guilds.models.GuildBlock homeBlock = new dev.mintychochip.guilds.models.GuildBlock(
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

    /**
     * Returns the guild.
     * @param name the name
     * @return the result
     */
    @Override
    public Optional<Guild> getGuild(String name) {
        // Try query with spawn columns first
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "guilds/select-by-name.sql", Map.of(
                         "name", name))) {

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
                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = SQL.prepare(connection, "guilds/select-by-name-without-spawn.sql", Map.of(
                                 "name", name))) {

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

    /**
     * Returns the guild.
     * @param uuid the uuid
     * @return the result
     */
    @Override
    public Optional<Guild> getGuild(UUID uuid) {
        logger.info("Looking for guild with UUID: " + uuid.toString());

        // Try query with spawn columns first
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "guilds/select-by-id.sql", Map.of(
                         "id", uuid.toString()))) {

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
                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = SQL.prepare(connection, "guilds/select-by-id-without-spawn.sql", Map.of(
                                 "id", uuid.toString()))) {

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

    /**
     * Returns the guild by id.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public Optional<Guild> getGuildById(String guildId) {
        // Try query with spawn columns first
        try {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = SQL.prepare(connection, "guilds/select-by-id.sql", Map.of(
                         "id", guildId))) {

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
                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = SQL.prepare(connection, "guilds/select-by-id-without-spawn.sql", Map.of(
                                 "id", guildId))) {

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

    /**
     * Updates the guild.
     * @param guild the guild
     * @return the result
     */
    @Override
    public Guild updateGuild(Guild guild) {
        Integer homeBlockX = null;
        Integer homeBlockZ = null;
        String homeBlockWorld = null;
        if (guild.getHomeBlock() != null) {
            homeBlockX = guild.getHomeBlock().getX();
            homeBlockZ = guild.getHomeBlock().getZ();
            homeBlockWorld = guild.getHomeBlock().getWorld();
        }

        Double spawnX = null;
        Double spawnY = null;
        Double spawnZ = null;
        Double spawnYaw = null;
        Double spawnPitch = null;
        String spawnWorld = null;
        if (guild.getSpawnLocation() != null) {
            Location spawn = guild.getSpawnLocation();
            spawnX = spawn.getX();
            spawnY = spawn.getY();
            spawnZ = spawn.getZ();
            spawnYaw = (double) spawn.getYaw();
            spawnPitch = (double) spawn.getPitch();
            spawnWorld = spawn.getWorld();
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/update.sql", SqlParams.of(
                     "name", guild.getName(),
                     "mayor_uuid", guild.getMayorUuid().toString(),
                     "balance", guild.getBalance(),
                     "home_block_x", homeBlockX,
                     "home_block_z", homeBlockZ,
                     "home_block_world", homeBlockWorld,
                     "spawn_x", spawnX,
                     "spawn_y", spawnY,
                     "spawn_z", spawnZ,
                     "spawn_yaw", spawnYaw,
                     "spawn_pitch", spawnPitch,
                     "spawn_world", spawnWorld,
                     "is_open", guild.isOpen(),
                     "permissions_flags", 0,
                     "tax_rates", "{}",
                     "pvp_enabled", guild.isPvpEnabled(),
                     "fire_enabled", guild.isFireEnabled(),
                     "explosions_enabled", guild.isExplosionsEnabled(),
                     "mobs_enabled", guild.isMobsEnabled(),
                     "public_enabled", guild.isPublicEnabled(),
                     "id", guild.getId()))) {

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

    /**
     * Deletes the guild.
     * @param name the name
     * @return the result
     */
    @Override
    public boolean deleteGuild(String name) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                String guildId = null;

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/select-id-by-name.sql", Map.of(
                        "name", name))) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Guild doesn't exist
                            return;
                        }
                    }
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/delete-blocks.sql", Map.of(
                        "guild_id", guildId))) {
                    int blocksDeleted = statement.executeUpdate();
                    logger.info("Deleted " + blocksDeleted + " guild blocks for guild: " + name);
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/delete-residents.sql", Map.of(
                        "guild_id", guildId))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/clear-resident-guild-by-name.sql", Map.of(
                        "guild_name", name))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/delete-by-name.sql", Map.of(
                        "name", name))) {
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

    /**
     * Returns the all guilds.
     * @return the result
     */
    @Override
    public List<Guild> getAllGuilds() {
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("guilds/select-all.sql"))) {

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

    /**
     * Returns the guilds by population.
     * @return the result
     */
    @Override
    public List<Guild> getGuildsByPopulation() {
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("guilds/select-by-population.sql"))) {

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

    /**
     * Returns the guilds by balance.
     * @return the result
     */
    @Override
    public List<Guild> getGuildsByBalance() {
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("guilds/select-by-balance.sql"))) {

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

    /**
     * Performs the guild exists operation.
     * @param name the name
     * @return the result
     */
    @Override
    public boolean guildExists(String name) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/count-by-name.sql", Map.of(
                     "name", name))) {

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

    /**
     * Returns the governance form.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public GovernmentForm getGovernanceForm(String guildId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/select-governance-form.sql", Map.of(
                     "id", guildId))) {
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

    /**
     * Adds the resident to guild.
     * @param guildName the guild name
     * @param residentUuid the resident uuid
     * @return the result
     */
    @Override
    public boolean addResidentToGuild(String guildName, UUID residentUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                String guildId = null;

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/select-id-by-name.sql", Map.of(
                        "name", guildName))) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Guild doesn't exist
                            return;
                        }
                    }
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/count-resident.sql", Map.of(
                        "guild_id", guildId,
                        "resident_uuid", residentUuid.toString()))) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next() && resultSet.getInt(1) > 0) {
                            result[0] = false; // Already in guild
                            return;
                        }
                    }
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/insert-resident.sql", Map.of(
                        "guild_id", guildId,
                        "resident_uuid", residentUuid.toString(),
                        "role", "resident",
                        "joined_at", LocalDateTime.now().format(DATE_FORMATTER)))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/update-resident-guild.sql", Map.of(
                        "guild_name", guildName,
                        "uuid", residentUuid.toString()))) {
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

    /**
     * Removes the resident from guild.
     * @param guildName the guild name
     * @param residentUuid the resident uuid
     * @return the result
     */
    @Override
    public boolean removeResidentFromGuild(String guildName, UUID residentUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                String guildId = null;

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/select-id-by-name.sql", Map.of(
                        "name", guildName))) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            guildId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Guild doesn't exist
                            return;
                        }
                    }
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/delete-resident.sql", Map.of(
                        "guild_id", guildId,
                        "resident_uuid", residentUuid.toString()))) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/clear-resident-guild.sql", Map.of(
                        "uuid", residentUuid.toString()))) {
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

    /**
     * Sets the guild mayor.
     * @param guildName the guild name
     * @param mayorUuid the mayor uuid
     * @return the result
     */
    @Override
    public boolean setGuildMayor(String guildName, UUID mayorUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                try (PreparedStatement statement = SQL.prepare(connection, "guilds/update-mayor.sql", Map.of(
                        "mayor_uuid", mayorUuid.toString(),
                        "name", guildName))) {
                    int rowsUpdated = statement.executeUpdate();

                    if (rowsUpdated == 0) {
                        result[0] = false; // Guild doesn't exist
                        return;
                    }
                }

                try (PreparedStatement statement = SQL.prepare(connection, "guilds/update-mayor-role.sql", Map.of(
                        "name", guildName,
                        "resident_uuid", mayorUuid.toString()))) {
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

    /**
     * Adds the guild assistant.
     * @param guildName the guild name
     * @param assistantUuid the assistant uuid
     * @return the result
     */
    @Override
    public boolean addGuildAssistant(String guildName, UUID assistantUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/update-assistant-role.sql", Map.of(
                     "name", guildName,
                     "resident_uuid", assistantUuid.toString()))) {

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

    /**
     * Removes the guild assistant.
     * @param guildName the guild name
     * @param assistantUuid the assistant uuid
     * @return the result
     */
    @Override
    public boolean removeGuildAssistant(String guildName, UUID assistantUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/update-resident-role.sql", Map.of(
                     "name", guildName,
                     "resident_uuid", assistantUuid.toString()))) {

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

    /**
     * Returns the guild resident count.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public int getGuildResidentCount(String guildName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/count-residents-by-name.sql", Map.of(
                     "name", guildName))) {

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

    /**
     * Updates the guild balance.
     * @param guildName the guild name
     * @param amount the amount
     * @return the result
     */
    @Override
    public double updateGuildBalance(String guildName, double amount) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/update-balance.sql", Map.of(
                     "amount", amount,
                     "name", guildName))) {

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

    /**
     * Returns the open guilds.
     * @return the result
     */
    @Override
    public List<Guild> getOpenGuilds() {
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("guilds/select-open.sql"))) {

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

    /**
     * Finds the guilds.
     * @param query the query
     * @return the result
     */
    @Override
    public List<Guild> searchGuilds(String query) {
        List<Guild> guilds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/search.sql", Map.of(
                     "name_pattern", "%" + query + "%"))) {

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
                dev.mintychochip.guilds.models.GuildBlock homeBlock = new dev.mintychochip.guilds.models.GuildBlock(
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
                dev.mintychochip.guilds.models.GuildBlock homeBlock = new dev.mintychochip.guilds.models.GuildBlock(
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

    /**
     * Loads the level and project columns.
     * @param resultSet the result set
     * @param guild the guild
     * @param name the name
     */
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
        try (PreparedStatement statement = SQL.prepare(connection, "guilds/select-residents.sql", Map.of(
                "guild_id", guild.getId()))) {

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

    /**
     * Sets the guild spawn.
     * @param guildName the guild name
     * @param location the location
     * @return the result
     */
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

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/update-spawn.sql", Map.of(
                     "spawn_x", location.getX(),
                     "spawn_y", location.getY(),
                     "spawn_z", location.getZ(),
                     "spawn_yaw", location.getYaw(),
                     "spawn_pitch", location.getPitch(),
                     "spawn_world", location.getWorld(),
                     "name", guildName))) {

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

        int blockX = (int) Math.floor(location.getX());
        int blockY = (int) Math.floor(location.getY());
        int blockZ = (int) Math.floor(location.getZ());

        logger.info("Converted location to blocks: x=" + blockX + ", y=" + blockY + ", z=" + blockZ);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/update-home-block.sql", Map.of(
                     "home_block_x", blockX,
                     "home_block_z", blockZ,
                     "home_block_y", blockY,
                     "home_block_world", location.getWorld(),
                     "name", guildName))) {

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

        int blockX = (int) Math.floor(location.getX());
        int blockZ = (int) Math.floor(location.getZ());

        logger.info("Converted location to blocks (no Y): x=" + blockX + ", z=" + blockZ);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/update-home-block-without-y.sql", Map.of(
                     "home_block_x", blockX,
                     "home_block_z", blockZ,
                     "home_block_world", location.getWorld(),
                     "name", guildName))) {

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

    /**
     * Returns the guild spawn.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public Optional<Location> getGuildSpawn(String guildName) {
        logger.info("Getting spawn for guild: " + guildName);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/select-spawn.sql", Map.of(
                     "name", guildName))) {

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

    /**
     * Returns whether teleport to spawn.
     * @param playerUuid the player uuid
     * @param guildName the guild name
     * @return the result
     */
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/select-home-block.sql", Map.of(
                     "name", guildName))) {

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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "guilds/select-home-block-without-y.sql", Map.of(
                     "name", guildName))) {

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

    /**
     * Returns the guilds by level.
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.models.Guild> getGuildsByLevel() {
        List<dev.mintychochip.guilds.models.Guild> guilds = getAllGuilds();
        guilds.sort((t1, t2) -> Integer.compare(t2.getGuildLevel(), t1.getGuildLevel()));
        return guilds;
    }

    /**
     * Returns the guilds by level range.
     * @param minLevel the min level
     * @param maxLevel the max level
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.models.Guild> getGuildsByLevelRange(int minLevel, int maxLevel) {
        return getAllGuilds().stream()
                .filter(guild -> guild.getGuildLevel() >= minLevel && guild.getGuildLevel() <= maxLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns the guilds by minimum level.
     * @param minimumLevel the minimum level
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.models.Guild> getGuildsByMinimumLevel(int minimumLevel) {
        return getAllGuilds().stream()
                .filter(guild -> guild.getGuildLevel() >= minimumLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns the guilds by tech points.
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.models.Guild> getGuildsByTechPoints() {
        List<dev.mintychochip.guilds.models.Guild> guilds = getAllGuilds();
        guilds.sort((t1, t2) -> Integer.compare(t2.getTechPoints(), t1.getTechPoints()));
        return guilds;
    }

    /**
     * Returns the total tech points.
     * @return the result
     */
    @Override
    public int getTotalTechPoints() {
        return getAllGuilds().stream()
                .mapToInt(dev.mintychochip.guilds.models.Guild::getTechPoints)
                .sum();
    }

    /**
     * Returns the guild statistics.
     * @return the result
     */
    @Override
    public GuildStatistics getGuildStatistics() {
        List<dev.mintychochip.guilds.models.Guild> guilds = getAllGuilds();

        int totalGuilds = guilds.size();
        double averageLevel = guilds.stream()
                .mapToInt(dev.mintychochip.guilds.models.Guild::getGuildLevel)
                .average()
                .orElse(0.0);
        int maxLevel = guilds.stream()
                .mapToInt(dev.mintychochip.guilds.models.Guild::getGuildLevel)
                .max()
                .orElse(0);
        int totalTechPoints = getTotalTechPoints();
        int totalResidents = guilds.stream()
                .mapToInt(dev.mintychochip.guilds.models.Guild::getResidentCount)
                .sum();
        double totalBalance = guilds.stream()
                .mapToDouble(dev.mintychochip.guilds.models.Guild::getBalance)
                .sum();

        // Simple level distribution (1-10, 11-20, etc.)
        java.util.Map<String, Integer> levelDistribution = new java.util.HashMap<>();
        for (dev.mintychochip.guilds.models.Guild guild : guilds) {
            int level = guild.getGuildLevel();
            String range = ((level - 1) / 10 * 10 + 1) + "-" + ((level - 1) / 10 * 10 + 10);
            levelDistribution.put(range, levelDistribution.getOrDefault(range, 0) + 1);
        }

        return new GuildStatistics(totalGuilds, (int) averageLevel, maxLevel,
                                 totalTechPoints, totalResidents, totalBalance, levelDistribution);
    }

    /**
     * Updates the guild level.
     * @param guildName the guild name
     * @param newLevel the new level
     * @param techPoints the tech points
     * @return the result
     */
    @Override
    public boolean updateGuildLevel(String guildName, int newLevel, int techPoints) {
        Optional<dev.mintychochip.guilds.models.Guild> guildOpt = getGuild(guildName);
        if (guildOpt.isEmpty()) {
            return false;
        }

        dev.mintychochip.guilds.models.Guild guild = guildOpt.get();
        guild.setGuildLevel(newLevel);
        guild.addTechPoints(techPoints);

        return updateGuild(guild) != null;
    }

    /**
     * Updates the guild upgrade progress.
     * @param guildName the guild name
     * @param upgradeProgress the upgrade progress
     * @return the result
     */
    @Override
    public boolean updateGuildUpgradeProgress(String guildName, java.util.Map<String, Integer> upgradeProgress) {
        Optional<dev.mintychochip.guilds.models.Guild> guildOpt = getGuild(guildName);
        if (guildOpt.isEmpty()) {
            return false;
        }

        dev.mintychochip.guilds.models.Guild guild = guildOpt.get();
        guild.setUpgradeProgress(upgradeProgress);

        return updateGuild(guild) != null;
    }

    /**
     * Returns the ranked guilds.
     * @param criteria the criteria
     * @param limit the limit
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.models.Guild> getRankedGuilds(String criteria, int limit) {
        List<dev.mintychochip.guilds.models.Guild> guilds = getAllGuilds();

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

    /**
     * Returns the top level guilds.
     * @param limit the limit
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.models.Guild> getTopLevelGuilds(int limit) {
        return getRankedGuilds("level", limit);
    }

    /**
     * Returns the guilds ready for upgrade.
     * @return the result
     */
    @Override
    public List<dev.mintychochip.guilds.models.Guild> getGuildsReadyForUpgrade() {
        // Basic implementation - would need GuildLevelService integration for real functionality
        return java.util.Collections.emptyList();
    }

    /**
     * Returns the average guild level.
     * @return the result
     */
    @Override
    public double getAverageGuildLevel() {
        return getAllGuilds().stream()
                .mapToInt(dev.mintychochip.guilds.models.Guild::getGuildLevel)
                .average()
                .orElse(0.0);
    }

    // Guild toggle system implementation

    /**
     * Performs the toggle guild permission operation.
     * @param guildName the guild name
     * @param permissionType the permission type
     * @param playerUuid the player uuid
     * @return the result
     */
    @Override
    public boolean toggleGuildPermission(String guildName, String permissionType, UUID playerUuid) {
        try {
            Optional<dev.mintychochip.guilds.models.Guild> guildOpt = getGuild(guildName);
            if (guildOpt.isEmpty()) {
                logger.warning("Cannot toggle permission - guild does not exist: " + guildName);
                return false;
            }

            dev.mintychochip.guilds.models.Guild guild = guildOpt.get();

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

    /**
     * Returns the guild toggles.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public java.util.Map<String, Boolean> getGuildToggles(String guildName) {
        try {
            Optional<dev.mintychochip.guilds.models.Guild> guildOpt = getGuild(guildName);
            if (guildOpt.isEmpty()) {
                return new HashMap<>();
            }

            return guildOpt.get().getAllToggles();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get guild toggles for guild: " + guildName, e);
            return new HashMap<>();
        }
    }

    /**
     * Sets the guild toggle.
     * @param guildName the guild name
     * @param permissionType the permission type
     * @param value the value
     * @param playerUuid the player uuid
     * @return the result
     */
    @Override
    public boolean setGuildToggle(String guildName, String permissionType, boolean value, UUID playerUuid) {
        try {
            Optional<dev.mintychochip.guilds.models.Guild> guildOpt = getGuild(guildName);
            if (guildOpt.isEmpty()) {
                logger.warning("Cannot set toggle - guild does not exist: " + guildName);
                return false;
            }

            dev.mintychochip.guilds.models.Guild guild = guildOpt.get();

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

    /**
     * Returns the guild toggle.
     * @param guildName the guild name
     * @param permissionType the permission type
     * @return the result
     */
    @Override
    public boolean getGuildToggle(String guildName, String permissionType) {
        try {
            Optional<dev.mintychochip.guilds.models.Guild> guildOpt = getGuild(guildName);
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

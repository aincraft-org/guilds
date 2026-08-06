package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.Location;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of TownService with database operations
 */
@Singleton
public class TownServiceImpl implements org.aincraft.towny.services.TownService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final org.aincraft.towny.services.ResidentService residentService;
    private final org.aincraft.towny.services.PermissionService permissionService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    public TownServiceImpl(DatabaseManager databaseManager, Logger logger,
                         org.aincraft.towny.services.ResidentService residentService,
                         org.aincraft.towny.services.PermissionService permissionService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.residentService = residentService;
        this.permissionService = permissionService;
    }

    @Override
    public Town createTown(String name, UUID mayorUuid) {
        // Check if town already exists
        if (townExists(name)) {
            throw new IllegalArgumentException("Town already exists: " + name);
        }

        // Use transaction for town creation
        return databaseManager.executeTransactionWithResult(connection -> {
            try {
                // Insert town
                String townSql = "INSERT INTO towns (id, name, mayor_uuid, balance, is_open, created_at, permissions_flags, tax_rates, " +
                                "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                String townId = UUID.randomUUID().toString();
                String createdAt = LocalDateTime.now().format(DATE_FORMATTER);

                // Default tax rates as JSON
                String taxRates = "{\"resident\":0.0,\"plot\":0.0,\"shop\":0.0}";

                try (PreparedStatement statement = connection.prepareStatement(townSql)) {
                    statement.setString(1, townId);
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
                String residentSql = "INSERT INTO town_residents (town_id, resident_uuid, role, joined_at) VALUES (?, ?, ?, ?)";

                try (PreparedStatement statement = connection.prepareStatement(residentSql)) {
                    statement.setString(1, townId);
                    statement.setString(2, mayorUuid.toString());
                    statement.setString(3, "mayor");
                    statement.setString(4, createdAt);

                    statement.executeUpdate();
                }

                // Update resident's town
                String updateResidentSql = "UPDATE residents SET town_name = ? WHERE uuid = ?";

                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, name);
                    statement.setString(2, mayorUuid.toString());

                    statement.executeUpdate();
                }

                Town town = new Town(name, mayorUuid);
                town.setId(townId);
                town.setCreatedAt(LocalDateTime.parse(createdAt, DATE_FORMATTER));

                logger.info("Created new town: " + name + " with mayor: " + mayorUuid);

                return town;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to create town: " + name, e);
                throw new RuntimeException("Failed to create town", e);
            }
        }).orElseThrow(() -> new RuntimeException("Failed to create town: transaction returned no result"));
    }

    @Override
    public Town createTown(String name, UUID mayorUuid, Location homeBlockLocation) {
        // Check if town already exists
        if (townExists(name)) {
            throw new IllegalArgumentException("Town already exists: " + name);
        }

        // Get block coordinates from location
        // TownBlock stores BLOCK coordinates (not chunk coordinates)
        int[] blockCoords = homeBlockLocation.getBlockCoordinates();
        int blockX = blockCoords[0];
        int blockZ = blockCoords[2]; // blockCoords returns [x, y, z], we need z

        // Calculate chunk for logging
        int[] chunkCoords = homeBlockLocation.getChunkCoordinates();
        int chunkX = chunkCoords[0];
        int chunkZ = chunkCoords[1];

        // Use transaction for town creation
        return databaseManager.executeTransactionWithResult(connection -> {
            try {
                // Insert town with home block and spawn (storing BLOCK coordinates)
                String townSql = "INSERT INTO towns (id, name, mayor_uuid, balance, is_open, created_at, permissions_flags, tax_rates, " +
                                "home_block_x, home_block_z, home_block_world, " +
                                "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                                "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                String townId = UUID.randomUUID().toString();
                String createdAt = LocalDateTime.now().format(DATE_FORMATTER);

                // Default tax rates as JSON
                String taxRates = "{\"resident\":0.0,\"plot\":0.0,\"shop\":0.0}";

                try (PreparedStatement statement = connection.prepareStatement(townSql)) {
                    statement.setString(1, townId);
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
                String residentSql = "INSERT INTO town_residents (town_id, resident_uuid, role, joined_at) VALUES (?, ?, ?, ?)";

                try (PreparedStatement statement = connection.prepareStatement(residentSql)) {
                    statement.setString(1, townId);
                    statement.setString(2, mayorUuid.toString());
                    statement.setString(3, "mayor");
                    statement.setString(4, createdAt);

                    statement.executeUpdate();
                }

                // Update resident's town
                String updateResidentSql = "UPDATE residents SET town_name = ? WHERE uuid = ?";

                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, name);
                    statement.setString(2, mayorUuid.toString());

                    statement.executeUpdate();
                }

                Town town = new Town(name, mayorUuid);
                town.setId(townId);
                town.setCreatedAt(LocalDateTime.parse(createdAt, DATE_FORMATTER));

                // Set home block with BLOCK coordinates
                org.aincraft.towny.models.TownBlock homeBlock = new org.aincraft.towny.models.TownBlock(
                    blockX, blockZ, homeBlockLocation.getWorld(), townId
                );
                town.setHomeBlock(homeBlock);

                // Set spawn location
                town.setSpawnLocation(homeBlockLocation);

                logger.info("Created new town: " + name + " with mayor: " + mayorUuid + " at chunk [" + chunkX + ", " + chunkZ + "] with spawn at " + homeBlockLocation.toDisplayString());

                return town;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to create town: " + name, e);
                throw new RuntimeException("Failed to create town", e);
            }
        }).orElseThrow(() -> new RuntimeException("Failed to create town: transaction returned no result"));
    }

    @Override
    public Optional<Town> getTown(String name) {
        // Try query with spawn columns first
        try {
            String sqlWithSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                               "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                               "is_open, created_at, permissions_flags, tax_rates, " +
                               "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled FROM towns WHERE name = ?";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlWithSpawn)) {

                statement.setString(1, name);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Town town = mapResultSetToTown(resultSet);
                        loadTownResidents(connection, town);
                        return Optional.of(town);
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found in getTown(), using query without spawn columns for town: " + name);
                try {
                    String sqlWithoutSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                                         "is_open, created_at, permissions_flags, tax_rates, " +
                                         "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled FROM towns WHERE name = ?";

                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = connection.prepareStatement(sqlWithoutSpawn)) {

                        statement.setString(1, name);

                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Town town = mapResultSetToTownWithoutSpawn(resultSet);
                                loadTownResidents(connection, town);
                                return Optional.of(town);
                            }
                        }
                    }
                } catch (SQLException e2) {
                    logger.log(Level.SEVERE, "Failed to get town with fallback query: " + name, e2);
                }
            } else {
                logger.log(Level.SEVERE, "Failed to get town: " + name, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<Town> getTown(UUID uuid) {
        logger.info("Looking for town with UUID: " + uuid.toString());

        // Try query with spawn columns first
        try {
            String sqlWithSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                               "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                               "is_open, created_at, permissions_flags, tax_rates, " +
                               "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled FROM towns WHERE id = ?";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlWithSpawn)) {

                statement.setString(1, uuid.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Town town = mapResultSetToTown(resultSet);
                        loadTownResidents(connection, town);
                        logger.info("Found town: " + town.getName() + " with ID: " + town.getId());
                        return Optional.of(town);
                    }
                }
            }

            logger.info("No town found with UUID: " + uuid.toString());
        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found in getTown(UUID), using query without spawn columns");
                try {
                    String sqlWithoutSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                                         "is_open, created_at, permissions_flags, tax_rates, " +
                                         "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled FROM towns WHERE id = ?";

                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = connection.prepareStatement(sqlWithoutSpawn)) {

                        statement.setString(1, uuid.toString());

                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Town town = mapResultSetToTownWithoutSpawn(resultSet);
                                loadTownResidents(connection, town);
                                return Optional.of(town);
                            }
                        }
                    }
                } catch (SQLException e2) {
                    logger.log(Level.SEVERE, "Failed to get town with fallback query (UUID): " + uuid, e2);
                }
            } else {
                logger.log(Level.SEVERE, "Failed to get town: " + uuid, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public Optional<Town> getTownById(String townId) {
        // Try query with spawn columns first
        try {
            String sqlWithSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                               "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                               "is_open, created_at, permissions_flags, tax_rates, " +
                               "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled FROM towns WHERE id = ?";

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sqlWithSpawn)) {

                statement.setString(1, townId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        Town town = mapResultSetToTown(resultSet);
                        loadTownResidents(connection, town);
                        return Optional.of(town);
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found in getTownById(), using query without spawn columns");
                try {
                    String sqlWithoutSpawn = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                                         "is_open, created_at, permissions_flags, tax_rates, " +
                                         "pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled FROM towns WHERE id = ?";

                    try (Connection connection = dataSource.getConnection();
                         PreparedStatement statement = connection.prepareStatement(sqlWithoutSpawn)) {

                        statement.setString(1, townId);

                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                Town town = mapResultSetToTownWithoutSpawn(resultSet);
                                loadTownResidents(connection, town);
                                return Optional.of(town);
                            }
                        }
                    }
                } catch (SQLException e2) {
                    logger.log(Level.SEVERE, "Failed to get town by ID with fallback query: " + townId, e2);
                }
            } else {
                logger.log(Level.SEVERE, "Failed to get town by ID: " + townId, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public Town updateTown(Town town) {
        String sql = "UPDATE towns SET name = ?, mayor_uuid = ?, balance = ?, home_block_x = ?, " +
                    "home_block_z = ?, home_block_world = ?, spawn_x = ?, spawn_y = ?, spawn_z = ?, " +
                    "spawn_yaw = ?, spawn_pitch = ?, spawn_world = ?, is_open = ?, permissions_flags = ?, tax_rates = ?, " +
                    "pvp_enabled = ?, fire_enabled = ?, explosions_enabled = ?, mobs_enabled = ?, public_enabled = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, town.getName());
            statement.setString(2, town.getMayorUuid().toString());
            statement.setDouble(3, town.getBalance());

            if (town.getHomeBlock() != null) {
                statement.setInt(4, town.getHomeBlock().getX());
                statement.setInt(5, town.getHomeBlock().getZ());
                statement.setString(6, town.getHomeBlock().getWorld());
            } else {
                statement.setNull(4, Types.INTEGER);
                statement.setNull(5, Types.INTEGER);
                statement.setNull(6, Types.VARCHAR);
            }

            // Handle spawn location
            if (town.getSpawnLocation() != null) {
                Location spawn = town.getSpawnLocation();
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

            statement.setBoolean(13, town.isOpen());
            statement.setInt(14, 0); // Permission flags will be implemented later
            statement.setString(15, "{}"); // Tax rates as JSON

            // Toggle fields
            statement.setBoolean(16, town.isPvpEnabled());
            statement.setBoolean(17, town.isFireEnabled());
            statement.setBoolean(18, town.isExplosionsEnabled());
            statement.setBoolean(19, town.isMobsEnabled());
            statement.setBoolean(20, town.isPublicEnabled());

            statement.setString(21, town.getId());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Updated town: " + town.getName());
            }

            return town;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update town: " + town.getName(), e);
            throw new RuntimeException("Failed to update town", e);
        }
    }

    @Override
    public boolean deleteTown(String name) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Get town ID
                String getIdSql = "SELECT id FROM towns WHERE name = ?";
                String townId = null;

                try (PreparedStatement statement = connection.prepareStatement(getIdSql)) {
                    statement.setString(1, name);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            townId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Town doesn't exist
                            return;
                        }
                    }
                }

                // Delete all town blocks (claims) for this town
                String deleteTownBlocksSql = "DELETE FROM town_blocks WHERE town_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteTownBlocksSql)) {
                    statement.setString(1, townId);
                    int blocksDeleted = statement.executeUpdate();
                    logger.info("Deleted " + blocksDeleted + " town blocks for town: " + name);
                }

                // Delete town residents associations
                String deleteTownResidentsSql = "DELETE FROM town_residents WHERE town_id = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteTownResidentsSql)) {
                    statement.setString(1, townId);
                    statement.executeUpdate();
                }

                // Remove residents from town
                String updateResidentsSql = "UPDATE residents SET town_name = NULL WHERE town_name = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateResidentsSql)) {
                    statement.setString(1, name);
                    statement.executeUpdate();
                }

                // Delete town
                String deleteTownSql = "DELETE FROM towns WHERE name = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteTownSql)) {
                    statement.setString(1, name);
                    int rowsDeleted = statement.executeUpdate();

                    if (rowsDeleted > 0) {
                        logger.info("Deleted town: " + name);
                        result[0] = true;
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete town: " + name, e);
                throw new RuntimeException("Failed to delete town", e);
            }
        });

        return result[0];
    }

    @Override
    public List<Town> getAllTowns() {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates FROM towns ORDER BY name";
        List<Town> towns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Town town = mapResultSetToTown(resultSet);
                towns.add(town);
            }

            // Load residents for all towns
            for (Town town : towns) {
                loadTownResidents(connection, town);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all towns", e);
        }

        return towns;
    }

    @Override
    public List<Town> getTownsByPopulation() {
        // Get towns with resident counts
        String sql = """
            SELECT t.id, t.name, t.mayor_uuid, t.balance, t.home_block_x, t.home_block_z, t.home_block_world,
                   t.spawn_x, t.spawn_y, t.spawn_z, t.spawn_yaw, t.spawn_pitch, t.spawn_world,
                   t.is_open, t.created_at, t.permissions_flags, t.tax_rates,
                   COUNT(tr.resident_uuid) as resident_count
            FROM towns t
            LEFT JOIN town_residents tr ON t.id = tr.town_id
            GROUP BY t.id
            ORDER BY resident_count DESC
            """;

        List<Town> towns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Town town = mapResultSetToTown(resultSet);
                towns.add(town);
            }

            // Load residents for all towns
            for (Town town : towns) {
                loadTownResidents(connection, town);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get towns by population", e);
        }

        return towns;
    }

    @Override
    public List<Town> getTownsByBalance() {
        String sql = """
            SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world,
                   spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world,
                   is_open, created_at, permissions_flags, tax_rates
            FROM towns
            ORDER BY balance DESC
            """;

        List<Town> towns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Town town = mapResultSetToTown(resultSet);
                towns.add(town);
            }

            // Load residents for all towns
            for (Town town : towns) {
                loadTownResidents(connection, town);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get towns by balance", e);
        }

        return towns;
    }

    @Override
    public boolean townExists(String name) {
        String sql = "SELECT COUNT(*) FROM towns WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if town exists: " + name, e);
        }

        return false;
    }

    @Override
    public boolean addResidentToTown(String townName, UUID residentUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Get town ID
                String getTownIdSql = "SELECT id FROM towns WHERE name = ?";
                String townId = null;

                try (PreparedStatement statement = connection.prepareStatement(getTownIdSql)) {
                    statement.setString(1, townName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            townId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Town doesn't exist
                            return;
                        }
                    }
                }

                // Check if resident already in town
                String checkResidentSql = "SELECT COUNT(*) FROM town_residents WHERE town_id = ? AND resident_uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(checkResidentSql)) {
                    statement.setString(1, townId);
                    statement.setString(2, residentUuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next() && resultSet.getInt(1) > 0) {
                            result[0] = false; // Already in town
                            return;
                        }
                    }
                }

                // Add resident to town
                String insertSql = "INSERT INTO town_residents (town_id, resident_uuid, role, joined_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                    statement.setString(1, townId);
                    statement.setString(2, residentUuid.toString());
                    statement.setString(3, "resident");
                    statement.setString(4, LocalDateTime.now().format(DATE_FORMATTER));

                    statement.executeUpdate();
                }

                // Update resident's town name
                String updateResidentSql = "UPDATE residents SET town_name = ? WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, townName);
                    statement.setString(2, residentUuid.toString());

                    statement.executeUpdate();
                }

                logger.info("Added resident " + residentUuid + " to town " + townName);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to add resident to town: " + townName, e);
                throw new RuntimeException("Failed to add resident to town", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean removeResidentFromTown(String townName, UUID residentUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Get town ID
                String getTownIdSql = "SELECT id FROM towns WHERE name = ?";
                String townId = null;

                try (PreparedStatement statement = connection.prepareStatement(getTownIdSql)) {
                    statement.setString(1, townName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            townId = resultSet.getString("id");
                        } else {
                            result[0] = false; // Town doesn't exist
                            return;
                        }
                    }
                }

                // Remove resident from town
                String deleteSql = "DELETE FROM town_residents WHERE town_id = ? AND resident_uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                    statement.setString(1, townId);
                    statement.setString(2, residentUuid.toString());

                    statement.executeUpdate();
                }

                // Update resident's town name
                String updateResidentSql = "UPDATE residents SET town_name = NULL WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateResidentSql)) {
                    statement.setString(1, residentUuid.toString());

                    statement.executeUpdate();
                }

                logger.info("Removed resident " + residentUuid + " from town " + townName);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to remove resident from town: " + townName, e);
                throw new RuntimeException("Failed to remove resident from town", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean setTownMayor(String townName, UUID mayorUuid) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Update town's mayor
                String updateTownSql = "UPDATE towns SET mayor_uuid = ? WHERE name = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateTownSql)) {
                    statement.setString(1, mayorUuid.toString());
                    statement.setString(2, townName);

                    int rowsUpdated = statement.executeUpdate();

                    if (rowsUpdated == 0) {
                        result[0] = false; // Town doesn't exist
                        return;
                    }
                }

                // Update resident's role in town
                String updateRoleSql = "UPDATE town_residents SET role = 'mayor' WHERE town_id = (SELECT id FROM towns WHERE name = ?) AND resident_uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(updateRoleSql)) {
                    statement.setString(1, townName);
                    statement.setString(2, mayorUuid.toString());

                    statement.executeUpdate();
                }

                logger.info("Set mayor for town " + townName + ": " + mayorUuid);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to set mayor for town: " + townName, e);
                throw new RuntimeException("Failed to set mayor for town", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean addTownAssistant(String townName, UUID assistantUuid) {
        String sql = "UPDATE town_residents SET role = 'assistant' WHERE town_id = (SELECT id FROM towns WHERE name = ?) AND resident_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);
            statement.setString(2, assistantUuid.toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Added assistant to town " + townName + ": " + assistantUuid);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to add assistant to town: " + townName, e);
        }

        return false;
    }

    @Override
    public boolean removeTownAssistant(String townName, UUID assistantUuid) {
        String sql = "UPDATE town_residents SET role = 'resident' WHERE town_id = (SELECT id FROM towns WHERE name = ?) AND resident_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);
            statement.setString(2, assistantUuid.toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Removed assistant from town " + townName + ": " + assistantUuid);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to remove assistant from town: " + townName, e);
        }

        return false;
    }

    @Override
    public int getTownResidentCount(String townName) {
        String sql = "SELECT COUNT(*) FROM town_residents WHERE town_id = (SELECT id FROM towns WHERE name = ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident count for town: " + townName, e);
        }

        return 0;
    }

    @Override
    public double updateTownBalance(String townName, double amount) {
        String sql = "UPDATE towns SET balance = balance + ? WHERE name = ? RETURNING balance";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDouble(1, amount);
            statement.setString(2, townName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    double newBalance = resultSet.getDouble(1);
                    logger.info("Updated balance for town " + townName + ": " + amount + " (new total: " + newBalance + ")");
                    return newBalance;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update balance for town: " + townName, e);
        }

        return 0;
    }

    @Override
    public List<Town> getOpenTowns() {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates FROM towns WHERE is_open = TRUE ORDER BY name";
        List<Town> towns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                Town town = mapResultSetToTown(resultSet);
                towns.add(town);
            }

            // Load residents for all towns
            for (Town town : towns) {
                loadTownResidents(connection, town);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get open towns", e);
        }

        return towns;
    }

    @Override
    public List<Town> searchTowns(String query) {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates FROM towns WHERE name LIKE ? ORDER BY name";
        List<Town> towns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, "%" + query + "%");

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Town town = mapResultSetToTown(resultSet);
                    towns.add(town);
                }
            }

            // Load residents for all towns
            for (Town town : towns) {
                loadTownResidents(connection, town);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to search towns: " + query, e);
        }

        return towns;
    }

    /**
     * Map a ResultSet to a Town object
     */
    private Town mapResultSetToTown(ResultSet resultSet) throws SQLException {
        String id = resultSet.getString("id");
        String name = resultSet.getString("name");
        UUID mayorUuid = UUID.fromString(resultSet.getString("mayor_uuid"));
        double balance = resultSet.getDouble("balance");
        boolean isOpen = resultSet.getBoolean("is_open");
        String createdAtStr = resultSet.getString("created_at");

        Town town = new Town(name, mayorUuid);
        town.setId(id);
        town.setBalance(balance);
        town.setOpen(isOpen);

        // Handle home block
        try {
            int homeBlockX = resultSet.getInt("home_block_x");
            boolean xNull = resultSet.wasNull();
            int homeBlockZ = resultSet.getInt("home_block_z");
            boolean zNull = resultSet.wasNull();
            String homeBlockWorld = resultSet.getString("home_block_world");

            if (!xNull && !zNull && homeBlockWorld != null) {
                org.aincraft.towny.models.TownBlock homeBlock = new org.aincraft.towny.models.TownBlock(
                    homeBlockX, homeBlockZ, homeBlockWorld, id
                );
                town.setHomeBlock(homeBlock);
            }
        } catch (SQLException e) {
            // Home block columns might not exist, just skip
            logger.fine("Could not load home block for town: " + name + " - " + e.getMessage());
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
                town.setSpawnLocation(spawnLocation);
            }
        } catch (SQLException e) {
            // Spawn columns might not exist or have issues, just skip
            logger.fine("Could not load spawn location for town: " + name + " - " + e.getMessage());
        }

        if (createdAtStr != null) {
            town.setCreatedAt(LocalDateTime.parse(createdAtStr, DATE_FORMATTER));
        }

        // Handle toggle fields (new in migration v6)
        try {
            town.setPvpEnabled(resultSet.getBoolean("pvp_enabled"));
            town.setFireEnabled(resultSet.getBoolean("fire_enabled"));
            town.setExplosionsEnabled(resultSet.getBoolean("explosions_enabled"));
            town.setMobsEnabled(resultSet.getBoolean("mobs_enabled"));
            town.setPublicEnabled(resultSet.getBoolean("public_enabled"));
        } catch (SQLException e) {
            // Toggle columns might not exist yet, use defaults
            logger.fine("Could not load toggle fields for town: " + name + " - " + e.getMessage());
            // Town constructor already sets default values
        }

        return town;
    }

    /**
     * Map a ResultSet to a Town object (without spawn columns for fallback)
     */
    private Town mapResultSetToTownWithoutSpawn(ResultSet resultSet) throws SQLException {
        String id = resultSet.getString("id");
        String name = resultSet.getString("name");
        UUID mayorUuid = UUID.fromString(resultSet.getString("mayor_uuid"));
        double balance = resultSet.getDouble("balance");
        boolean isOpen = resultSet.getBoolean("is_open");
        String createdAtStr = resultSet.getString("created_at");

        Town town = new Town(name, mayorUuid);
        town.setId(id);
        town.setBalance(balance);
        town.setOpen(isOpen);

        // Handle home block
        try {
            int homeBlockX = resultSet.getInt("home_block_x");
            boolean xNull = resultSet.wasNull();
            int homeBlockZ = resultSet.getInt("home_block_z");
            boolean zNull = resultSet.wasNull();
            String homeBlockWorld = resultSet.getString("home_block_world");

            if (!xNull && !zNull && homeBlockWorld != null) {
                org.aincraft.towny.models.TownBlock homeBlock = new org.aincraft.towny.models.TownBlock(
                    homeBlockX, homeBlockZ, homeBlockWorld, id
                );
                town.setHomeBlock(homeBlock);
            }
        } catch (SQLException e) {
            // Home block columns might not exist, just skip
            logger.fine("Could not load home block for town: " + name + " - " + e.getMessage());
        }

        // Note: spawn location will be null, getTownSpawn() will use fallback logic

        if (createdAtStr != null) {
            town.setCreatedAt(LocalDateTime.parse(createdAtStr, DATE_FORMATTER));
        }

        // Handle toggle fields (new in migration v6)
        try {
            town.setPvpEnabled(resultSet.getBoolean("pvp_enabled"));
            town.setFireEnabled(resultSet.getBoolean("fire_enabled"));
            town.setExplosionsEnabled(resultSet.getBoolean("explosions_enabled"));
            town.setMobsEnabled(resultSet.getBoolean("mobs_enabled"));
            town.setPublicEnabled(resultSet.getBoolean("public_enabled"));
        } catch (SQLException e) {
            // Toggle columns might not exist yet, use defaults
            logger.fine("Could not load toggle fields for town: " + name + " - " + e.getMessage());
            // Town constructor already sets default values
        }

        return town;
    }

    /**
     * Load residents for a town
     */
    private void loadTownResidents(Connection connection, Town town) throws SQLException {
        String sql = "SELECT resident_uuid, role FROM town_residents WHERE town_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, town.getId());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID residentUuid = UUID.fromString(resultSet.getString("resident_uuid"));
                    String role = resultSet.getString("role");

                    town.getResidents().add(residentUuid);

                    if ("assistant".equals(role)) {
                        town.getAssistants().add(residentUuid);
                    }
                }
            }
        }
    }

    @Override
    public boolean setTownSpawn(String townName, Location location) {
        // First, validate that the spawn location is within the town's home block chunk
        Optional<Town> townOpt = getTown(townName);
        if (townOpt.isEmpty()) {
            logger.warning("Cannot set spawn - town does not exist: " + townName);
            return false;
        }

        Town town = townOpt.get();
        if (town.getHomeBlock() == null) {
            logger.warning("Cannot set spawn - town does not have a home block set: " + townName);
            return false;
        }

        // Get the chunk coordinates for both the spawn location and home block
        int[] spawnChunk = location.getChunkCoordinates();
        int[] homeBlockChunk = town.getHomeBlock().getChunkCoordinates();

        // Validate spawn is in home block chunk
        if (spawnChunk[0] != homeBlockChunk[0] || spawnChunk[1] != homeBlockChunk[1]) {
            logger.warning("Cannot set spawn - spawn must be in town's home block chunk. " +
                    "Spawn chunk: [" + spawnChunk[0] + ", " + spawnChunk[1] + "], " +
                    "Home block chunk: [" + homeBlockChunk[0] + ", " + homeBlockChunk[1] + "]");
            return false;
        }

        // Validate world matches
        if (!location.getWorld().equals(town.getHomeBlock().getWorld())) {
            logger.warning("Cannot set spawn - spawn must be in the same world as home block");
            return false;
        }

        String sql = "UPDATE towns SET spawn_x = ?, spawn_y = ?, spawn_z = ?, spawn_yaw = ?, spawn_pitch = ?, spawn_world = ? WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDouble(1, location.getX());
            statement.setDouble(2, location.getY());
            statement.setDouble(3, location.getZ());
            statement.setDouble(4, location.getYaw());
            statement.setDouble(5, location.getPitch());
            statement.setString(6, location.getWorld());
            statement.setString(7, townName);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Set spawn for town " + townName + ": " + location.toDisplayString());
                return true;
            }

        } catch (SQLException e) {
            if (e.getMessage().contains("no such column")) {
                logger.warning("Cannot set spawn - spawn columns don't exist. Database migration may not have run: " + townName);
                // Fallback: update home_block instead
                return setHomeBlockAsSpawnFallback(townName, location);
            } else {
                logger.log(Level.SEVERE, "Failed to set spawn for town: " + townName, e);
            }
        }

        return false;
    }

    /**
     * Fallback method to set home_block as spawn if spawn columns don't exist
     */
    private boolean setHomeBlockAsSpawnFallback(String townName, Location location) {
        logger.info("Setting home_block fallback spawn for town " + townName + " at location: " + location.toDisplayString());

        // Try with home_block_y first
        String sqlWithY = "UPDATE towns SET home_block_x = ?, home_block_z = ?, home_block_y = ?, home_block_world = ? WHERE name = ?";

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
            statement.setString(5, townName);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("SUCCESS: Set home_block as fallback spawn for town " + townName + ": " + blockX + ", " + blockY + ", " + blockZ);
                return true;
            } else {
                logger.warning("FAILED: No rows updated for town " + townName);
            }

        } catch (SQLException e) {
            // If home_block_y column doesn't exist, try without it
            if (e.getMessage().contains("no such column")) {
                logger.info("home_block_y column not found, using fallback without Y coordinate: " + e.getMessage());
                return setHomeBlockAsSpawnFallbackWithoutY(townName, location);
            } else {
                logger.log(Level.SEVERE, "SQL Error setting home_block fallback for town: " + townName, e);
            }
        }

        return false;
    }

    /**
     * Fallback method to set home_block without Y coordinate
     */
    private boolean setHomeBlockAsSpawnFallbackWithoutY(String townName, Location location) {
        logger.info("Setting home_block fallback WITHOUT Y for town " + townName + " at location: " + location.toDisplayString());

        String sql = "UPDATE towns SET home_block_x = ?, home_block_z = ?, home_block_world = ? WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // Convert to block coordinates
            int blockX = (int) Math.floor(location.getX());
            int blockZ = (int) Math.floor(location.getZ());

            logger.info("Converted location to blocks (no Y): x=" + blockX + ", z=" + blockZ);

            statement.setInt(1, blockX);
            statement.setInt(2, blockZ);
            statement.setString(3, location.getWorld());
            statement.setString(4, townName);

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("SUCCESS: Set home_block as fallback spawn for town " + townName + ": " + blockX + ", " + blockZ + " (no Y saved)");
                return true;
            } else {
                logger.warning("FAILED: No rows updated for town " + townName + " (no Y)");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error setting home_block fallback for town (without Y): " + townName, e);
        }

        return false;
    }

    @Override
    public Optional<Location> getTownSpawn(String townName) {
        logger.info("Getting spawn for town: " + townName);

        String sql = "SELECT spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world FROM towns WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    logger.info("Found spawn columns data for town: " + townName);

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
                        logger.warning("Spawn columns exist but some are null for town: " + townName);
                    }
                } else {
                    logger.info("No spawn data found for town: " + townName);
                }
            }

        } catch (SQLException e) {
            // If spawn columns don't exist, try to use home_block as fallback
            if (e.getMessage().contains("no such column")) {
                logger.info("Spawn columns not found, using home_block as fallback for town: " + townName + " - " + e.getMessage());
                return getHomeBlockAsSpawn(townName);
            } else {
                logger.log(Level.SEVERE, "Failed to get spawn for town: " + townName, e);
            }
        }

        return Optional.empty();
    }

    @Override
    public boolean canTeleportToSpawn(UUID playerUuid, String townName) {
        // Check if town exists
        if (!townExists(townName)) {
            return false;
        }

        // Check if town has a spawn set
        Optional<Location> spawnLocation = getTownSpawn(townName);
        if (spawnLocation.isEmpty()) {
            return false;
        }

        // Check if player is a resident of the town
        Optional<Town> town = getTown(townName);
        if (town.isEmpty()) {
            return false;
        }

        // Allow teleportation if:
        // 1. Player is a resident of the town
        // 2. Town is open to public
        // 3. Player has permission (could be extended with permission system)

        return town.get().isResident(playerUuid) || town.get().isOpen();
    }

    /**
     * Fallback method to use home_block as spawn if spawn columns don't exist
     */
    private Optional<Location> getHomeBlockAsSpawn(String townName) {
        // Try with home_block_y first
        String sqlWithY = "SELECT home_block_x, home_block_y, home_block_z, home_block_world FROM towns WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sqlWithY)) {

            statement.setString(1, townName);

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
                return getHomeBlockAsSpawnWithoutY(townName);
            } else {
                logger.log(Level.SEVERE, "Failed to get home block for town: " + townName, e);
            }
        }

        return Optional.empty();
    }

    /**
     * Fallback method to use home_block without Y coordinate
     */
    private Optional<Location> getHomeBlockAsSpawnWithoutY(String townName) {
        String sql = "SELECT home_block_x, home_block_z, home_block_world FROM towns WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);

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
            logger.log(Level.SEVERE, "Failed to get home block without Y for town: " + townName, e);
        }

        return Optional.empty();
    }

    // Town level system methods - basic implementations for compilation

    @Override
    public List<org.aincraft.towny.models.Town> getTownsByLevel() {
        List<org.aincraft.towny.models.Town> towns = getAllTowns();
        towns.sort((t1, t2) -> Integer.compare(t2.getTownLevel(), t1.getTownLevel()));
        return towns;
    }

    @Override
    public List<org.aincraft.towny.models.Town> getTownsByLevelRange(int minLevel, int maxLevel) {
        return getAllTowns().stream()
                .filter(town -> town.getTownLevel() >= minLevel && town.getTownLevel() <= maxLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<org.aincraft.towny.models.Town> getTownsByMinimumLevel(int minimumLevel) {
        return getAllTowns().stream()
                .filter(town -> town.getTownLevel() >= minimumLevel)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<org.aincraft.towny.models.Town> getTownsByTechPoints() {
        List<org.aincraft.towny.models.Town> towns = getAllTowns();
        towns.sort((t1, t2) -> Integer.compare(t2.getTechPoints(), t1.getTechPoints()));
        return towns;
    }

    @Override
    public int getTotalTechPoints() {
        return getAllTowns().stream()
                .mapToInt(org.aincraft.towny.models.Town::getTechPoints)
                .sum();
    }

    @Override
    public TownStatistics getTownStatistics() {
        List<org.aincraft.towny.models.Town> towns = getAllTowns();

        int totalTowns = towns.size();
        double averageLevel = towns.stream()
                .mapToInt(org.aincraft.towny.models.Town::getTownLevel)
                .average()
                .orElse(0.0);
        int maxLevel = towns.stream()
                .mapToInt(org.aincraft.towny.models.Town::getTownLevel)
                .max()
                .orElse(0);
        int totalTechPoints = getTotalTechPoints();
        int totalResidents = towns.stream()
                .mapToInt(org.aincraft.towny.models.Town::getResidentCount)
                .sum();
        double totalBalance = towns.stream()
                .mapToDouble(org.aincraft.towny.models.Town::getBalance)
                .sum();

        // Simple level distribution (1-10, 11-20, etc.)
        java.util.Map<String, Integer> levelDistribution = new java.util.HashMap<>();
        for (org.aincraft.towny.models.Town town : towns) {
            int level = town.getTownLevel();
            String range = ((level - 1) / 10 * 10 + 1) + "-" + ((level - 1) / 10 * 10 + 10);
            levelDistribution.put(range, levelDistribution.getOrDefault(range, 0) + 1);
        }

        return new TownStatistics(totalTowns, (int) averageLevel, maxLevel,
                                 totalTechPoints, totalResidents, totalBalance, levelDistribution);
    }

    @Override
    public boolean updateTownLevel(String townName, int newLevel, int techPoints) {
        Optional<org.aincraft.towny.models.Town> townOpt = getTown(townName);
        if (townOpt.isEmpty()) {
            return false;
        }

        org.aincraft.towny.models.Town town = townOpt.get();
        town.setTownLevel(newLevel);
        town.addTechPoints(techPoints);

        return updateTown(town) != null;
    }

    @Override
    public boolean updateTownUpgradeProgress(String townName, java.util.Map<String, Integer> upgradeProgress) {
        Optional<org.aincraft.towny.models.Town> townOpt = getTown(townName);
        if (townOpt.isEmpty()) {
            return false;
        }

        org.aincraft.towny.models.Town town = townOpt.get();
        town.setUpgradeProgress(upgradeProgress);

        return updateTown(town) != null;
    }

    @Override
    public List<org.aincraft.towny.models.Town> getRankedTowns(String criteria, int limit) {
        List<org.aincraft.towny.models.Town> towns = getAllTowns();

        switch (criteria.toLowerCase()) {
            case "level":
                towns.sort((t1, t2) -> Integer.compare(t2.getTownLevel(), t1.getTownLevel()));
                break;
            case "residents":
                towns.sort((t1, t2) -> Integer.compare(t2.getResidentCount(), t1.getResidentCount()));
                break;
            case "balance":
                towns.sort((t1, t2) -> Double.compare(t2.getBalance(), t1.getBalance()));
                break;
            case "techpoints":
                towns.sort((t1, t2) -> Integer.compare(t2.getTechPoints(), t1.getTechPoints()));
                break;
            default:
                // Default to level
                towns.sort((t1, t2) -> Integer.compare(t2.getTownLevel(), t1.getTownLevel()));
                break;
        }

        return towns.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<org.aincraft.towny.models.Town> getTopLevelTowns(int limit) {
        return getRankedTowns("level", limit);
    }

    @Override
    public List<org.aincraft.towny.models.Town> getTownsReadyForUpgrade() {
        // Basic implementation - would need TownLevelService integration for real functionality
        return java.util.Collections.emptyList();
    }

    @Override
    public double getAverageTownLevel() {
        return getAllTowns().stream()
                .mapToInt(org.aincraft.towny.models.Town::getTownLevel)
                .average()
                .orElse(0.0);
    }

    // Town toggle system implementation

    @Override
    public boolean toggleTownPermission(String townName, String permissionType, UUID playerUuid) {
        try {
            Optional<org.aincraft.towny.models.Town> townOpt = getTown(townName);
            if (townOpt.isEmpty()) {
                logger.warning("Cannot toggle permission - town does not exist: " + townName);
                return false;
            }

            org.aincraft.towny.models.Town town = townOpt.get();

            // Check if player has permission to toggle town settings
            if (!permissionService.hasTownAdmin(playerUuid, townName)) {
                logger.warning("Player " + playerUuid + " attempted to toggle town permission without admin rights: " + townName);
                return false;
            }

            // Toggle the permission using the town's method
            boolean currentState = town.getToggle(permissionType);
            boolean newValue = !currentState;
            town.setToggle(permissionType, newValue);

            // Save the updated town to database
            return updateTown(town) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to toggle town permission: " + permissionType + " for town: " + townName, e);
            return false;
        }
    }

    @Override
    public java.util.Map<String, Boolean> getTownToggles(String townName) {
        try {
            Optional<org.aincraft.towny.models.Town> townOpt = getTown(townName);
            if (townOpt.isEmpty()) {
                return new HashMap<>();
            }

            return townOpt.get().getAllToggles();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get town toggles for town: " + townName, e);
            return new HashMap<>();
        }
    }

    @Override
    public boolean setTownToggle(String townName, String permissionType, boolean value, UUID playerUuid) {
        try {
            Optional<org.aincraft.towny.models.Town> townOpt = getTown(townName);
            if (townOpt.isEmpty()) {
                logger.warning("Cannot set toggle - town does not exist: " + townName);
                return false;
            }

            org.aincraft.towny.models.Town town = townOpt.get();

            // Check if player has permission to set town settings
            if (!permissionService.hasTownAdmin(playerUuid, townName)) {
                logger.warning("Player " + playerUuid + " attempted to set town toggle without admin rights: " + townName);
                return false;
            }

            // Set the toggle using the town's method
            boolean success = town.setToggle(permissionType, value);
            if (!success) {
                logger.warning("Invalid toggle type: " + permissionType + " for town: " + townName);
                return false;
            }

            // Save the updated town to database
            return updateTown(town) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set town toggle: " + permissionType + " for town: " + townName, e);
            return false;
        }
    }

    @Override
    public boolean getTownToggle(String townName, String permissionType) {
        try {
            Optional<org.aincraft.towny.models.Town> townOpt = getTown(townName);
            if (townOpt.isEmpty()) {
                return false;
            }

            return townOpt.get().getToggle(permissionType);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get town toggle: " + permissionType + " for town: " + townName, e);
            return false;
        }
    }
}
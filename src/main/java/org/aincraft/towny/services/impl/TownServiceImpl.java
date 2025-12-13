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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    public TownServiceImpl(DatabaseManager databaseManager, Logger logger, org.aincraft.towny.services.ResidentService residentService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.residentService = residentService;
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
                String townSql = "INSERT INTO towns (id, name, mayor_uuid, balance, is_open, created_at, permissions_flags, tax_rates) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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
    public Optional<Town> getTown(String name) {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates FROM towns WHERE name = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Town town = mapResultSetToTown(resultSet);
                    loadTownResidents(connection, town);
                    return Optional.of(town);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town: " + name, e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Town> getTown(UUID uuid) {
        String sql = "SELECT id, name, mayor_uuid, balance, home_block_x, home_block_z, home_block_world, " +
                    "spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world, " +
                    "is_open, created_at, permissions_flags, tax_rates FROM towns WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Town town = mapResultSetToTown(resultSet);
                    loadTownResidents(connection, town);
                    return Optional.of(town);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town: " + uuid, e);
        }

        return Optional.empty();
    }

    @Override
    public Town updateTown(Town town) {
        String sql = "UPDATE towns SET name = ?, mayor_uuid = ?, balance = ?, home_block_x = ?, " +
                    "home_block_z = ?, home_block_world = ?, spawn_x = ?, spawn_y = ?, spawn_z = ?, " +
                    "spawn_yaw = ?, spawn_pitch = ?, spawn_world = ?, is_open = ?, permissions_flags = ?, tax_rates = ? WHERE id = ?";

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

            statement.setString(16, town.getId());

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

        if (createdAtStr != null) {
            town.setCreatedAt(LocalDateTime.parse(createdAtStr, DATE_FORMATTER));
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
}
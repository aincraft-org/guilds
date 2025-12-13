package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.TownBlock;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of PlotService with database operations
 */
@Singleton
public class PlotServiceImpl implements org.aincraft.towny.services.PlotService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    public PlotServiceImpl(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
    }

    @Override
    public TownBlock createTownBlock(int x, int z, String world, String townName) {
        String sql = "INSERT INTO town_blocks (id, x, z, world, town_id, plot_type, price, permissions_flags, claimed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            UUID plotId = UUID.randomUUID();
            String claimedAt = LocalDateTime.now().format(DATE_FORMATTER);

            statement.setString(1, plotId.toString());
            statement.setInt(2, x);
            statement.setInt(3, z);
            statement.setString(4, world);
            statement.setString(5, townName); // This should be town_id, but using town_name for now
            statement.setString(6, TownBlock.PlotType.DEFAULT);
            statement.setDouble(7, 0.0); // Default price
            statement.setInt(8, 0); // Default permission flags
            statement.setString(9, claimedAt);

            statement.executeUpdate();

            TownBlock townBlock = new TownBlock(x, z, world, townName);
            townBlock.setId(plotId);
            townBlock.setClaimedAt(LocalDateTime.parse(claimedAt, DATE_FORMATTER));

            logger.info("Created town block at " + x + "," + z + " in world " + world + " for town " + townName);
            return townBlock;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create town block at " + x + "," + z + " in world " + world, e);
            throw new RuntimeException("Failed to create town block", e);
        }
    }

    @Override
    public Optional<TownBlock> getTownBlock(int x, int z, String world) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE x = ? AND z = ? AND world = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, x);
            statement.setInt(2, z);
            statement.setString(3, world);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town block at " + x + "," + z + " in world " + world, e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<TownBlock> getTownBlock(UUID id) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town block: " + id, e);
        }

        return Optional.empty();
    }

    @Override
    public TownBlock updateTownBlock(TownBlock townBlock) {
        String sql = "UPDATE town_blocks SET x = ?, z = ?, world = ?, town_id = ?, owner_uuid = ?, " +
                    "plot_type = ?, price = ?, permissions_flags = ?, custom_name = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, townBlock.getX());
            statement.setInt(2, townBlock.getZ());
            statement.setString(3, townBlock.getWorld());
            statement.setString(4, townBlock.getTownId());

            if (townBlock.getOwnerId() != null) {
                statement.setString(5, townBlock.getOwnerId().toString());
            } else {
                statement.setNull(5, Types.VARCHAR);
            }

            statement.setString(6, townBlock.getPlotType());
            statement.setDouble(7, townBlock.getPrice());
            statement.setInt(8, 0); // Permission flags will be implemented later
            statement.setString(9, townBlock.getCustomName());
            statement.setString(10, townBlock.getId().toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Updated town block: " + townBlock.getId());
            }

            return townBlock;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update town block: " + townBlock.getId(), e);
            throw new RuntimeException("Failed to update town block", e);
        }
    }

    @Override
    public boolean deleteTownBlock(UUID id) {
        String sql = "DELETE FROM town_blocks WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, id.toString());

            int rowsDeleted = statement.executeUpdate();

            if (rowsDeleted > 0) {
                logger.info("Deleted town block: " + id);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete town block: " + id, e);
        }

        return false;
    }

    @Override
    public List<TownBlock> getAllTownBlocks() {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks ORDER BY world, x, z";
        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                townBlocks.add(mapResultSetToTownBlock(resultSet));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all town blocks", e);
        }

        return townBlocks;
    }

    @Override
    public List<TownBlock> getTownBlocksInTown(String townName) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE town_id = ? ORDER BY x, z";
        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    townBlocks.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks for town: " + townName, e);
        }

        return townBlocks;
    }

    @Override
    public List<TownBlock> getTownBlocksInWorld(String world) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE world = ? ORDER BY x, z";
        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, world);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    townBlocks.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks in world: " + world, e);
        }

        return townBlocks;
    }

    @Override
    public List<TownBlock> getTownBlocksOwnedBy(UUID residentUuid) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE owner_uuid = ? ORDER BY world, x, z";
        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    townBlocks.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks owned by: " + residentUuid, e);
        }

        return townBlocks;
    }

    @Override
    public boolean townBlockExists(int x, int z, String world) {
        String sql = "SELECT COUNT(*) FROM town_blocks WHERE x = ? AND z = ? AND world = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, x);
            statement.setInt(2, z);
            statement.setString(3, world);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if town block exists at " + x + "," + z + " in world " + world, e);
        }

        return false;
    }

    @Override
    public boolean claimTownBlock(int x, int z, String world, String townName) {
        // Check if town block already exists
        if (townBlockExists(x, z, world)) {
            return false;
        }

        try {
            createTownBlock(x, z, world, townName);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to claim town block at " + x + "," + z + " in world " + world, e);
            return false;
        }
    }

    @Override
    public boolean unclaimTownBlock(int x, int z, String world) {
        String sql = "DELETE FROM town_blocks WHERE x = ? AND z = ? AND world = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, x);
            statement.setInt(2, z);
            statement.setString(3, world);

            int rowsDeleted = statement.executeUpdate();

            if (rowsDeleted > 0) {
                logger.info("Unclaimed town block at " + x + "," + z + " in world " + world);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to unclaim town block at " + x + "," + z + " in world " + world, e);
        }

        return false;
    }

    @Override
    public boolean setTownBlockOwner(UUID id, UUID ownerUuid) {
        String sql = "UPDATE town_blocks SET owner_uuid = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            if (ownerUuid != null) {
                statement.setString(1, ownerUuid.toString());
            } else {
                statement.setNull(1, Types.VARCHAR);
            }

            statement.setString(2, id.toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Set owner for town block " + id + ": " + ownerUuid);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set owner for town block: " + id, e);
        }

        return false;
    }

    @Override
    public List<TownBlock> getTownBlocksInRadius(int centerX, int centerZ, int radius, String world) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE world = ? AND x >= ? AND x <= ? AND z >= ? AND z <= ? ORDER BY x, z";

        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, world);
            statement.setInt(2, centerX - radius);
            statement.setInt(3, centerX + radius);
            statement.setInt(4, centerZ - radius);
            statement.setInt(5, centerZ + radius);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    townBlocks.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks in radius around " + centerX + "," + centerZ + " in world " + world, e);
        }

        return townBlocks;
    }

    @Override
    public List<TownBlock> getTownBlocksByType(String plotType) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE plot_type = ? ORDER BY world, x, z";
        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, plotType);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    townBlocks.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks by type: " + plotType, e);
        }

        return townBlocks;
    }

    @Override
    public List<TownBlock> getTownOwnedBlocks(String townName) {
        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE town_id = ? AND owner_uuid IS NULL ORDER BY x, z";
        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    townBlocks.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town-owned blocks for town: " + townName, e);
        }

        return townBlocks;
    }

    @Override
    public int getTownBlockCount(String townName) {
        String sql = "SELECT COUNT(*) FROM town_blocks WHERE town_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town block count for town: " + townName, e);
        }

        return 0;
    }

    @Override
    public boolean setPlotType(UUID id, String plotType) {
        String sql = "UPDATE town_blocks SET plot_type = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, plotType);
            statement.setString(2, id.toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Set plot type for town block " + id + ": " + plotType);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set plot type for town block: " + id, e);
        }

        return false;
    }

    @Override
    public List<TownBlock> getTownBlocksInChunk(int chunkX, int chunkZ, String world) {
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;

        String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM town_blocks WHERE world = ? AND x >= ? AND x < ? AND z >= ? AND z < ? ORDER BY x, z";

        List<TownBlock> townBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, world);
            statement.setInt(2, blockX);
            statement.setInt(3, blockX + 16);
            statement.setInt(4, blockZ);
            statement.setInt(5, blockZ + 16);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    townBlocks.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks in chunk " + chunkX + "," + chunkZ + " in world " + world, e);
        }

        return townBlocks;
    }

    /**
     * Map a ResultSet to a TownBlock object
     */
    private TownBlock mapResultSetToTownBlock(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        int x = resultSet.getInt("x");
        int z = resultSet.getInt("z");
        String world = resultSet.getString("world");
        String townId = resultSet.getString("town_id");
        String plotType = resultSet.getString("plot_type");
        double price = resultSet.getDouble("price");
        String claimedAtStr = resultSet.getString("claimed_at");
        String customName = resultSet.getString("custom_name");

        TownBlock townBlock = new TownBlock(x, z, world, townId);
        townBlock.setId(id);
        townBlock.setPlotType(plotType);
        townBlock.setPrice(price);

        String ownerUuidStr = resultSet.getString("owner_uuid");
        if (ownerUuidStr != null && !ownerUuidStr.isEmpty()) {
            townBlock.setOwnerId(UUID.fromString(ownerUuidStr));
        }

        if (claimedAtStr != null) {
            townBlock.setClaimedAt(LocalDateTime.parse(claimedAtStr, DATE_FORMATTER));
        }

        townBlock.setCustomName(customName);

        return townBlock;
    }
}
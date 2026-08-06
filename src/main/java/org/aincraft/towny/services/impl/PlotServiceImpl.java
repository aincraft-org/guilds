package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.models.Permission;
import org.aincraft.towny.models.PlotTypes;
import org.aincraft.towny.services.TownService;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private final TownService townService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    public PlotServiceImpl(DatabaseManager databaseManager, TownService townService, Logger logger) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.townService = townService;
        this.logger = logger;
    }

    @Override
    public TownBlock createTownBlock(int x, int z, String world, String townName) {
        // First, get the town ID from the town name
        String getTownIdSql = "SELECT id FROM towns WHERE name = ?";
        String townId = null;

        try (Connection connection = dataSource.getConnection()) {
            // Get town ID
            try (PreparedStatement getTownStmt = connection.prepareStatement(getTownIdSql)) {
                getTownStmt.setString(1, townName);
                try (ResultSet rs = getTownStmt.executeQuery()) {
                    if (rs.next()) {
                        townId = rs.getString("id");
                    } else {
                        throw new RuntimeException("Town not found: " + townName);
                    }
                }
            }

            // Create town block
            String sql = "INSERT INTO town_blocks (id, x, z, world, town_id, plot_type, price, permissions_flags, claimed_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                UUID plotId = UUID.randomUUID();
                String claimedAt = LocalDateTime.now().format(DATE_FORMATTER);

                statement.setString(1, plotId.toString());
                statement.setInt(2, x);
                statement.setInt(3, z);
                statement.setString(4, world);
                statement.setString(5, townId);
                statement.setString(6, PlotTypes.DEFAULT);
                statement.setDouble(7, 0.0); // Default price
                statement.setInt(8, 0); // Default permission flags
                statement.setString(9, claimedAt);

                statement.executeUpdate();

                TownBlock townBlock = new TownBlock(x, z, world, townId);
                townBlock.setId(plotId);
                townBlock.setClaimedAt(LocalDateTime.parse(claimedAt, DATE_FORMATTER));

                logger.info("Created town block at " + x + "," + z + " in world " + world + " for town " + townName + " (ID: " + townId + ")");
                return townBlock;
            }

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
                    TownBlock townBlock = mapResultSetToTownBlock(resultSet);
                    logger.info("Found town block in database: x=" + x + ", z=" + z + ", world=" + world +
                              ", town_id=" + townBlock.getTownId() + ", owner_id=" + townBlock.getOwnerId());
                    return Optional.of(townBlock);
                }
            }

            logger.info("No town block found in database query for x=" + x + ", z=" + z + ", world=" + world);

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
            statement.setInt(8, townBlock.getPermissionsFlags()); // Use permission flags from model
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
        // First get the town ID from the town name
        String getTownIdSql = "SELECT id FROM towns WHERE name = ?";
        String townId = null;

        try (Connection connection = dataSource.getConnection()) {
            // Get town ID
            try (PreparedStatement getTownStmt = connection.prepareStatement(getTownIdSql)) {
                getTownStmt.setString(1, townName);
                try (ResultSet rs = getTownStmt.executeQuery()) {
                    if (rs.next()) {
                        townId = rs.getString("id");
                    } else {
                        logger.warning("Town not found: " + townName);
                        return new ArrayList<>();
                    }
                }
            }

            // Get town blocks for this town
            String sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                        "FROM town_blocks WHERE town_id = ? ORDER BY x, z";
            List<TownBlock> townBlocks = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, townId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        townBlocks.add(mapResultSetToTownBlock(resultSet));
                    }
                }
            }

            return townBlocks;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks for town: " + townName, e);
            return new ArrayList<>();
        }
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
        int permissionsFlags = resultSet.getInt("permissions_flags");

        TownBlock townBlock = new TownBlock(x, z, world, townId);
        townBlock.setId(id);
        townBlock.setPlotType(plotType);
        townBlock.setPrice(price);
        townBlock.setPermissionsFlags(permissionsFlags);

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

    // Plot claiming and ownership methods implementation

    @Override
    public boolean claimPlotForResident(UUID residentUuid, int x, int z, String world) {
        try {
            // Check if plot exists and is town-owned
            Optional<TownBlock> existingPlot = getTownBlock(x, z, world);
            if (existingPlot.isEmpty()) {
                return false; // Plot doesn't exist (town must claim territory first)
            }

            TownBlock plot = existingPlot.get();

            // Plot is owned by a resident, can't claim it
            if (plot.getOwnerId() != null) {
                return false;
            }

            // Plot is town-owned, resident can claim it
            String residentName = getResidentName(residentUuid);
            if (residentName == null) {
                return false; // Resident doesn't exist
            }

            // Get resident's town to verify they belong to the same town
            String townName = getResidentTown(residentUuid);
            if (townName == null) {
                return false; // Resident not in a town
            }

            // Verify plot belongs to resident's town
            Optional<Town> residentTown = townService.getTown(townName);
            if (residentTown.isEmpty() || !plot.getTownId().equals(residentTown.get().getId())) {
                return false; // Plot belongs to different town
            }

            // Transfer ownership to resident
            plot.setOwnerId(residentUuid);
            plot.resetToDefaultPermissions(); // Set full permissions for owner

            return updateTownBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to claim plot for resident " + residentUuid + " at " + x + "," + z + " in " + world, e);
            return false;
        }
    }

    @Override
    public boolean buyPlot(UUID residentUuid, UUID plotId, double price) {
        try {
            Optional<TownBlock> plotOpt = getTownBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            TownBlock plot = plotOpt.get();
            if (!plot.isForSale() || plot.getPrice() != price) {
                return false; // Plot not for sale at this price
            }

            if (plot.hasOwner()) {
                return false; // Plot already has owner
            }

            // TODO: Implement economy check and transaction
            // This would need integration with an economy plugin

            plot.setOwnerId(residentUuid);
            plot.setPrice(0.0); // Remove from sale
            plot.resetToDefaultPermissions(); // Set full permissions for new owner

            return updateTownBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to buy plot " + plotId + " by resident " + residentUuid, e);
            return false;
        }
    }

    @Override
    public boolean setPlotForSale(UUID plotId, double price, UUID ownerUuid) {
        try {
            Optional<TownBlock> plotOpt = getTownBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            TownBlock plot = plotOpt.get();

            // Verify ownership
            if (!plot.isOwner(ownerUuid)) {
                return false; // Not the owner
            }

            plot.setPrice(Math.max(0.0, price)); // Ensure non-negative price
            return updateTownBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set plot " + plotId + " for sale", e);
            return false;
        }
    }

    @Override
    public List<TownBlock> getPlotsForSale(String townName) {
        String sql;
        if (townName != null) {
            sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                  "FROM town_blocks WHERE town_id = ? AND price > 0 ORDER BY price, x, z";
        } else {
            sql = "SELECT id, x, z, world, town_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                  "FROM town_blocks WHERE price > 0 ORDER BY world, price, x, z";
        }

        List<TownBlock> plotsForSale = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            if (townName != null) {
                statement.setString(1, townName);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    plotsForSale.add(mapResultSetToTownBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get plots for sale" + (townName != null ? " in town " + townName : ""), e);
        }

        return plotsForSale;
    }

    @Override
    public List<TownBlock> getPlotsOwnedByResident(UUID residentUuid) {
        return getTownBlocksOwnedBy(residentUuid);
    }

    // Plot permission management methods implementation

    @Override
    public boolean setPlotPermissionFlag(UUID plotId, int permissionFlag, boolean value) {
        try {
            Optional<TownBlock> plotOpt = getTownBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            TownBlock plot = plotOpt.get();
            plot.setPermissionFlag(permissionFlag, value);

            return updateTownBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set permission flag for plot " + plotId, e);
            return false;
        }
    }

    @Override
    public boolean setPlotPermissionFlags(UUID plotId, int flags) {
        try {
            Optional<TownBlock> plotOpt = getTownBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            TownBlock plot = plotOpt.get();
            plot.setPermissionsFlags(flags);

            return updateTownBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set permission flags for plot " + plotId, e);
            return false;
        }
    }

    @Override
    public boolean addPlotPermissionFlag(UUID plotId, int permissionFlag) {
        return setPlotPermissionFlag(plotId, permissionFlag, true);
    }

    @Override
    public boolean removePlotPermissionFlag(UUID plotId, int permissionFlag) {
        return setPlotPermissionFlag(plotId, permissionFlag, false);
    }

    @Override
    public List<Permission> getPlotPermissions(UUID plotId) {
        List<Permission> permissions = new ArrayList<>();
        String sql = "SELECT id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid " +
                    "FROM permissions WHERE context = 'plot' AND context_id = ? ORDER BY granted_at";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, plotId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Permission permission = new Permission();
                    permission.setId(UUID.fromString(resultSet.getString("id")));
                    permission.setFlags(resultSet.getInt("permissions_flags"));
                    permission.setContext(resultSet.getString("context"));
                    permission.setContextId(resultSet.getString("context_id"));
                    permission.setTargetType(resultSet.getString("target_type"));
                    permission.setTargetId(resultSet.getString("target_id"));

                    String grantedAtStr = resultSet.getString("granted_at");
                    if (grantedAtStr != null) {
                        permission.setGrantedAt(LocalDateTime.parse(grantedAtStr, DATE_FORMATTER));
                    }

                    String grantedByStr = resultSet.getString("granted_by_uuid");
                    if (grantedByStr != null && !grantedByStr.isEmpty()) {
                        permission.setGrantedBy(UUID.fromString(grantedByStr));
                    }

                    permissions.add(permission);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get permissions for plot " + plotId, e);
        }

        return permissions;
    }

    @Override
    public boolean grantPlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag, UUID grantedBy) {
        String sql = "INSERT OR REPLACE INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid) " +
                    "VALUES (?, 'plot', ?, ?, ?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            UUID permissionId = UUID.randomUUID();
            String grantedAt = LocalDateTime.now().format(DATE_FORMATTER);

            statement.setString(1, permissionId.toString());
            statement.setString(2, plotId.toString());
            statement.setString(3, targetType);
            statement.setString(4, targetId);
            statement.setInt(5, permissionFlag);
            statement.setString(6, grantedAt);
            statement.setString(7, grantedBy.toString());

            int rowsUpdated = statement.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("Granted permission " + permissionFlag + " to " + targetType + ":" + targetId + " for plot " + plotId);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to grant permission for plot " + plotId, e);
        }

        return false;
    }

    @Override
    public boolean revokePlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag) {
        String sql = "DELETE FROM permissions WHERE context = 'plot' AND context_id = ? AND target_type = ? AND target_id = ? AND permissions_flags = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, plotId.toString());
            statement.setString(2, targetType);
            statement.setString(3, targetId);
            statement.setInt(4, permissionFlag);

            int rowsDeleted = statement.executeUpdate();
            if (rowsDeleted > 0) {
                logger.info("Revoked permission " + permissionFlag + " from " + targetType + ":" + targetId + " for plot " + plotId);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to revoke permission for plot " + plotId, e);
        }

        return false;
    }

    // Utility methods implementation

    @Override
    public Optional<TownBlock> getTownBlockAtLocation(String world, int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        return getTownBlock(chunkX, chunkZ, world);
    }

    @Override
    public boolean canResidentClaimPlot(UUID residentUuid, int x, int z, String world) {
        try {
            // Check if resident exists
            String residentName = getResidentName(residentUuid);
            if (residentName == null) {
                return false;
            }

            // Check if resident is in a town
            String townName = getResidentTown(residentUuid);
            if (townName == null) {
                logger.info("Resident " + residentUuid + " is not in a town");
                return false;
            }

            // Check if plot exists and is owned by town (not a resident)
            logger.info("Checking for town block at x=" + x + ", z=" + z + ", world=" + world + " for resident " + residentName + " in town " + townName);
            Optional<TownBlock> existingPlot = getTownBlock(x, z, world);
            if (existingPlot.isPresent()) {
                TownBlock plot = existingPlot.get();
                logger.info("Found town block: town_id=" + plot.getTownId() + ", owner_id=" + plot.getOwnerId());
                // If plot is owned by a resident, can't claim it
                if (plot.getOwnerId() != null) {
                    logger.info("Plot already owned by resident " + plot.getOwnerId());
                    return false;
                }
                // If plot exists and is town-owned, resident can claim it
                Optional<Town> town = townService.getTownById(plot.getTownId());
                if (town.isPresent()) {
                    boolean canClaim = town.get().getName().equals(townName);
                    logger.info("Town check: plot town=" + town.get().getName() + ", resident town=" + townName + ", can claim=" + canClaim);
                    return canClaim;
                } else {
                    logger.info("Town not found for town_id=" + plot.getTownId());
                    return false;
                }
            }

            // If no plot exists, resident can't claim it (town must claim territory first)
            logger.info("No town block found at x=" + x + ", z=" + z + ", world=" + world + " - town must claim territory first");
            return false;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to check if resident can claim plot", e);
            return false;
        }
    }

    @Override
    public boolean canResidentAffordPlot(UUID residentUuid, UUID plotId) {
        // TODO: Implement economy integration
        // This would check the resident's balance against the plot price
        return true;
    }

    // Helper methods

    private String getResidentName(UUID residentUuid) {
        String sql = "SELECT name FROM residents WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("name");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident name for " + residentUuid, e);
        }

        return null;
    }

    private String getResidentTown(UUID residentUuid) {
        String sql = "SELECT town_name FROM residents WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("town_name");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident town for " + residentUuid, e);
        }

        return null;
    }
}
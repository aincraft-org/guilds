package org.aincraft.guilds.services.impl;



import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.models.Permission;
import org.aincraft.guilds.models.PlotTypes;
import org.aincraft.guilds.services.GuildService;

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

public class PlotServiceImpl implements org.aincraft.guilds.services.PlotService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final GuildService guildService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public PlotServiceImpl(DatabaseManager databaseManager, GuildService guildService, Logger logger) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.guildService = guildService;
        this.logger = logger;
    }

    @Override
    public GuildBlock createGuildBlock(int x, int z, String world, String guildName) {
        // First, get the guild ID from the guild name
        String getGuildIdSql = "SELECT id FROM guilds WHERE name = ?";
        String guildId = null;

        try (Connection connection = dataSource.getConnection()) {
            // Get guild ID
            try (PreparedStatement getGuildStmt = connection.prepareStatement(getGuildIdSql)) {
                getGuildStmt.setString(1, guildName);
                try (ResultSet rs = getGuildStmt.executeQuery()) {
                    if (rs.next()) {
                        guildId = rs.getString("id");
                    } else {
                        throw new RuntimeException("Town not found: " + guildName);
                    }
                }
            }

            // Create guild block
            String sql = "INSERT INTO guild_blocks (id, x, z, world, guild_id, plot_type, price, permissions_flags, claimed_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                UUID plotId = UUID.randomUUID();
                String claimedAt = LocalDateTime.now().format(DATE_FORMATTER);

                statement.setString(1, plotId.toString());
                statement.setInt(2, x);
                statement.setInt(3, z);
                statement.setString(4, world);
                statement.setString(5, guildId);
                statement.setString(6, PlotTypes.DEFAULT);
                statement.setDouble(7, 0.0); // Default price
                statement.setInt(8, 0); // Default permission flags
                statement.setString(9, claimedAt);

                statement.executeUpdate();

                GuildBlock guildBlock = new GuildBlock(x, z, world, guildId);
                guildBlock.setId(plotId);
                guildBlock.setClaimedAt(LocalDateTime.parse(claimedAt, DATE_FORMATTER));

                logger.info("Created town block at " + x + "," + z + " in world " + world + " for town " + guildName + " (ID: " + guildId + ")");
                return guildBlock;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create town block at " + x + "," + z + " in world " + world, e);
            throw new RuntimeException("Failed to create town block", e);
        }
    }

    @Override
    public Optional<GuildBlock> getGuildBlock(int x, int z, String world) {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE x = ? AND z = ? AND world = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, x);
            statement.setInt(2, z);
            statement.setString(3, world);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    GuildBlock guildBlock = mapResultSetToGuildBlock(resultSet);
                    logger.info("Found town block in database: x=" + x + ", z=" + z + ", world=" + world +
                              ", guild_id=" + guildBlock.getGuildId() + ", owner_id=" + guildBlock.getOwnerId());
                    return Optional.of(guildBlock);
                }
            }

            logger.info("No town block found in database query for x=" + x + ", z=" + z + ", world=" + world);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town block at " + x + "," + z + " in world " + world, e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<GuildBlock> getGuildBlock(UUID id) {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town block: " + id, e);
        }

        return Optional.empty();
    }

    @Override
    public GuildBlock updateGuildBlock(GuildBlock guildBlock) {
        String sql = "UPDATE guild_blocks SET x = ?, z = ?, world = ?, guild_id = ?, owner_uuid = ?, " +
                    "plot_type = ?, price = ?, permissions_flags = ?, custom_name = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, guildBlock.getX());
            statement.setInt(2, guildBlock.getZ());
            statement.setString(3, guildBlock.getWorld());
            statement.setString(4, guildBlock.getGuildId());

            if (guildBlock.getOwnerId() != null) {
                statement.setString(5, guildBlock.getOwnerId().toString());
            } else {
                statement.setNull(5, Types.VARCHAR);
            }

            statement.setString(6, guildBlock.getPlotType());
            statement.setDouble(7, guildBlock.getPrice());
            statement.setInt(8, guildBlock.getPermissionsFlags()); // Use permission flags from model
            statement.setString(9, guildBlock.getCustomName());
            statement.setString(10, guildBlock.getId().toString());

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Updated town block: " + guildBlock.getId());
            }

            return guildBlock;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update town block: " + guildBlock.getId(), e);
            throw new RuntimeException("Failed to update town block", e);
        }
    }

    @Override
    public boolean deleteGuildBlock(UUID id) {
        String sql = "DELETE FROM guild_blocks WHERE id = ?";

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
    public List<GuildBlock> getAllGuildBlocks() {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks ORDER BY world, x, z";
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                guildBlocks.add(mapResultSetToGuildBlock(resultSet));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all town blocks", e);
        }

        return guildBlocks;
    }

    @Override
    public List<GuildBlock> getGuildBlocksInGuild(String guildName) {
        // First get the guild ID from the guild name
        String getGuildIdSql = "SELECT id FROM guilds WHERE name = ?";
        String guildId = null;

        try (Connection connection = dataSource.getConnection()) {
            // Get guild ID
            try (PreparedStatement getGuildStmt = connection.prepareStatement(getGuildIdSql)) {
                getGuildStmt.setString(1, guildName);
                try (ResultSet rs = getGuildStmt.executeQuery()) {
                    if (rs.next()) {
                        guildId = rs.getString("id");
                    } else {
                        logger.warning("Town not found: " + guildName);
                        return new ArrayList<>();
                    }
                }
            }

            // Get guild blocks for this guild
            String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                        "FROM guild_blocks WHERE guild_id = ? ORDER BY x, z";
            List<GuildBlock> guildBlocks = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, guildId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                    }
                }
            }

            return guildBlocks;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks for town: " + guildName, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<GuildBlock> getGuildBlocksInWorld(String world) {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE world = ? ORDER BY x, z";
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, world);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks in world: " + world, e);
        }

        return guildBlocks;
    }

    @Override
    public List<GuildBlock> getGuildBlocksOwnedBy(UUID residentUuid) {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE owner_uuid = ? ORDER BY world, x, z";
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks owned by: " + residentUuid, e);
        }

        return guildBlocks;
    }

    @Override
    public boolean guildBlockExists(int x, int z, String world) {
        String sql = "SELECT COUNT(*) FROM guild_blocks WHERE x = ? AND z = ? AND world = ?";

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
    public boolean claimGuildBlock(int x, int z, String world, String guildName) {
        // Check if guild block already exists
        if (guildBlockExists(x, z, world)) {
            return false;
        }

        try {
            createGuildBlock(x, z, world, guildName);
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to claim town block at " + x + "," + z + " in world " + world, e);
            return false;
        }
    }

    @Override
    public boolean unclaimGuildBlock(int x, int z, String world) {
        String sql = "DELETE FROM guild_blocks WHERE x = ? AND z = ? AND world = ?";

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
    public boolean setGuildBlockOwner(UUID id, UUID ownerUuid) {
        String sql = "UPDATE guild_blocks SET owner_uuid = ? WHERE id = ?";

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
    public List<GuildBlock> getGuildBlocksInRadius(int centerX, int centerZ, int radius, String world) {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE world = ? AND x >= ? AND x <= ? AND z >= ? AND z <= ? ORDER BY x, z";

        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, world);
            statement.setInt(2, centerX - radius);
            statement.setInt(3, centerX + radius);
            statement.setInt(4, centerZ - radius);
            statement.setInt(5, centerZ + radius);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks in radius around " + centerX + "," + centerZ + " in world " + world, e);
        }

        return guildBlocks;
    }

    @Override
    public List<GuildBlock> getGuildBlocksByType(String plotType) {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE plot_type = ? ORDER BY world, x, z";
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, plotType);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks by type: " + plotType, e);
        }

        return guildBlocks;
    }

    @Override
    public List<GuildBlock> getGuildOwnedBlocks(String guildName) {
        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE guild_id = ? AND owner_uuid IS NULL ORDER BY x, z";
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town-owned blocks for town: " + guildName, e);
        }

        return guildBlocks;
    }

    @Override
    public int getGuildBlockCount(String guildName) {
        String sql = "SELECT COUNT(*) FROM guild_blocks WHERE guild_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town block count for town: " + guildName, e);
        }

        return 0;
    }

    @Override
    public boolean setPlotType(UUID id, String plotType) {
        String sql = "UPDATE guild_blocks SET plot_type = ? WHERE id = ?";

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
    public List<GuildBlock> getGuildBlocksInChunk(int chunkX, int chunkZ, String world) {
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;

        String sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                    "FROM guild_blocks WHERE world = ? AND x >= ? AND x < ? AND z >= ? AND z < ? ORDER BY x, z";

        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, world);
            statement.setInt(2, blockX);
            statement.setInt(3, blockX + 16);
            statement.setInt(4, blockZ);
            statement.setInt(5, blockZ + 16);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get town blocks in chunk " + chunkX + "," + chunkZ + " in world " + world, e);
        }

        return guildBlocks;
    }

    /**
     * Map a ResultSet to a GuildBlock object
     */
    private GuildBlock mapResultSetToGuildBlock(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        int x = resultSet.getInt("x");
        int z = resultSet.getInt("z");
        String world = resultSet.getString("world");
        String guildId = resultSet.getString("guild_id");
        String plotType = resultSet.getString("plot_type");
        double price = resultSet.getDouble("price");
        String claimedAtStr = resultSet.getString("claimed_at");
        String customName = resultSet.getString("custom_name");
        int permissionsFlags = resultSet.getInt("permissions_flags");

        GuildBlock guildBlock = new GuildBlock(x, z, world, guildId);
        guildBlock.setId(id);
        guildBlock.setPlotType(plotType);
        guildBlock.setPrice(price);
        guildBlock.setPermissionsFlags(permissionsFlags);

        String ownerUuidStr = resultSet.getString("owner_uuid");
        if (ownerUuidStr != null && !ownerUuidStr.isEmpty()) {
            guildBlock.setOwnerId(UUID.fromString(ownerUuidStr));
        }

        if (claimedAtStr != null) {
            guildBlock.setClaimedAt(LocalDateTime.parse(claimedAtStr, DATE_FORMATTER));
        }

        guildBlock.setCustomName(customName);

        return guildBlock;
    }

    // Plot claiming and ownership methods implementation

    @Override
    public boolean claimPlotForResident(UUID residentUuid, int x, int z, String world) {
        try {
            // Check if plot exists and is guild-owned
            Optional<GuildBlock> existingPlot = getGuildBlock(x, z, world);
            if (existingPlot.isEmpty()) {
                return false; // Plot doesn't exist (guild must claim territory first)
            }

            GuildBlock plot = existingPlot.get();

            // Plot is owned by a resident, can't claim it
            if (plot.getOwnerId() != null) {
                return false;
            }

            // Plot is guild-owned, resident can claim it
            String residentName = getResidentName(residentUuid);
            if (residentName == null) {
                return false; // Resident doesn't exist
            }

            // Get resident's guild to verify they belong to the same guild
            String guildName = getResidentGuild(residentUuid);
            if (guildName == null) {
                return false; // Resident not in a guild
            }

            // Verify plot belongs to resident's guild
            Optional<Guild> residentGuild = guildService.getGuild(guildName);
            if (residentGuild.isEmpty() || !plot.getGuildId().equals(residentGuild.get().getId())) {
                return false; // Plot belongs to different guild
            }

            // Transfer ownership to resident
            plot.setOwnerId(residentUuid);
            plot.resetToDefaultPermissions(); // Set full permissions for owner

            return updateGuildBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to claim plot for resident " + residentUuid + " at " + x + "," + z + " in " + world, e);
            return false;
        }
    }

    @Override
    public boolean buyPlot(UUID residentUuid, UUID plotId, double price) {
        try {
            Optional<GuildBlock> plotOpt = getGuildBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            GuildBlock plot = plotOpt.get();
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

            return updateGuildBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to buy plot " + plotId + " by resident " + residentUuid, e);
            return false;
        }
    }

    @Override
    public boolean setPlotForSale(UUID plotId, double price, UUID ownerUuid) {
        try {
            Optional<GuildBlock> plotOpt = getGuildBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            GuildBlock plot = plotOpt.get();

            // Verify ownership
            if (!plot.isOwner(ownerUuid)) {
                return false; // Not the owner
            }

            plot.setPrice(Math.max(0.0, price)); // Ensure non-negative price
            return updateGuildBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set plot " + plotId + " for sale", e);
            return false;
        }
    }

    @Override
    public List<GuildBlock> getPlotsForSale(String guildName) {
        String sql;
        if (guildName != null) {
            sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                  "FROM guild_blocks WHERE guild_id = ? AND price > 0 ORDER BY price, x, z";
        } else {
            sql = "SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name " +
                  "FROM guild_blocks WHERE price > 0 ORDER BY world, price, x, z";
        }

        List<GuildBlock> plotsForSale = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            if (guildName != null) {
                statement.setString(1, guildName);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    plotsForSale.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get plots for sale" + (guildName != null ? " in town " + guildName : ""), e);
        }

        return plotsForSale;
    }

    @Override
    public List<GuildBlock> getPlotsOwnedByResident(UUID residentUuid) {
        return getGuildBlocksOwnedBy(residentUuid);
    }

    // Plot permission management methods implementation

    @Override
    public boolean setPlotPermissionFlag(UUID plotId, int permissionFlag, boolean value) {
        try {
            Optional<GuildBlock> plotOpt = getGuildBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            GuildBlock plot = plotOpt.get();
            plot.setPermissionFlag(permissionFlag, value);

            return updateGuildBlock(plot) != null;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set permission flag for plot " + plotId, e);
            return false;
        }
    }

    @Override
    public boolean setPlotPermissionFlags(UUID plotId, int flags) {
        try {
            Optional<GuildBlock> plotOpt = getGuildBlock(plotId);
            if (plotOpt.isEmpty()) {
                return false; // Plot doesn't exist
            }

            GuildBlock plot = plotOpt.get();
            plot.setPermissionsFlags(flags);

            return updateGuildBlock(plot) != null;

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
    public Optional<GuildBlock> getGuildBlockAtLocation(String world, int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        return getGuildBlock(chunkX, chunkZ, world);
    }

    @Override
    public boolean canResidentClaimPlot(UUID residentUuid, int x, int z, String world) {
        try {
            // Check if resident exists
            String residentName = getResidentName(residentUuid);
            if (residentName == null) {
                return false;
            }

            // Check if resident is in a guild
            String guildName = getResidentGuild(residentUuid);
            if (guildName == null) {
                logger.info("Resident " + residentUuid + " is not in a town");
                return false;
            }

            // Check if plot exists and is owned by guild (not a resident)
            logger.info("Checking for town block at x=" + x + ", z=" + z + ", world=" + world + " for resident " + residentName + " in town " + guildName);
            Optional<GuildBlock> existingPlot = getGuildBlock(x, z, world);
            if (existingPlot.isPresent()) {
                GuildBlock plot = existingPlot.get();
                logger.info("Found town block: guild_id=" + plot.getGuildId() + ", owner_id=" + plot.getOwnerId());
                // If plot is owned by a resident, can't claim it
                if (plot.getOwnerId() != null) {
                    logger.info("Plot already owned by resident " + plot.getOwnerId());
                    return false;
                }
                // If plot exists and is guild-owned, resident can claim it
                Optional<Guild> guild = guildService.getGuildById(plot.getGuildId());
                if (guild.isPresent()) {
                    boolean canClaim = guild.get().getName().equals(guildName);
                    logger.info("Town check: plot town=" + guild.get().getName() + ", resident town=" + guildName + ", can claim=" + canClaim);
                    return canClaim;
                } else {
                    logger.info("Town not found for guild_id=" + plot.getGuildId());
                    return false;
                }
            }

            // If no plot exists, resident can't claim it (guild must claim territory first)
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

    private String getResidentGuild(UUID residentUuid) {
        String sql = "SELECT guild_name FROM residents WHERE uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("guild_name");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident town for " + residentUuid, e);
        }

        return null;
    }
}
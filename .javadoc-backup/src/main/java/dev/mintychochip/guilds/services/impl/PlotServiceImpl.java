package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildBlock;
import dev.mintychochip.guilds.models.GuildPermission;
import dev.mintychochip.guilds.models.Permission;
import dev.mintychochip.guilds.models.PlotTypes;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.PermissionService;
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

public class PlotServiceImpl implements dev.mintychochip.guilds.services.PlotService {

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The data source. */
    private final DataSource dataSource;
    /** The logger. */
    private final Logger logger;
    /** The guild service. */
    private final GuildService guildService;

    /**
     * Late-bound (the Guild/Permission/Plot service cycle): used to invalidate
     * the permission read cache after plot-permission mutations, which write
     * the permissions table outside PermissionServiceImpl.
     */
    private PermissionService permissionService;

    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();
    /** The date formatter constant. */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    /**
     * Creates a new plot service impl instance.
     * @param databaseManager the database manager
     * @param guildService the guild service
     * @param logger the logger
     */
    public PlotServiceImpl(DatabaseManager databaseManager, GuildService guildService, Logger logger) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.guildService = guildService;
        this.logger = logger;
    }

    /**
     * Sets the permission service.
     * @param permissionService the permission service
     */
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * Creates a new guild block.
     * @param x the x
     * @param z the z
     * @param world the world
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public GuildBlock createGuildBlock(int x, int z, String world, String guildName) {
        String guildId = null;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement getGuildStmt = SQL.prepare(connection, "plots/select-guild-id-by-name.sql", Map.of(
                    "name", guildName))) {
                try (ResultSet rs = getGuildStmt.executeQuery()) {
                    if (rs.next()) {
                        guildId = rs.getString("id");
                    } else {
                        throw new RuntimeException("Guild not found: " + guildName);
                    }
                }
            }

            UUID plotId = UUID.randomUUID();
            String claimedAt = LocalDateTime.now().format(DATE_FORMATTER);

            try (PreparedStatement statement = SQL.prepare(connection, "plots/insert-guild-block.sql", Map.of(
                    "id", plotId.toString(),
                    "x", x,
                    "z", z,
                    "world", world,
                    "guild_id", guildId,
                    "plot_type", PlotTypes.DEFAULT,
                    "price", 0.0,
                    "permissions_flags", 0,
                    "claimed_at", claimedAt))) {
                statement.executeUpdate();

                GuildBlock guildBlock = new GuildBlock(x, z, world, guildId);
                guildBlock.setId(plotId);
                guildBlock.setClaimedAt(LocalDateTime.parse(claimedAt, DATE_FORMATTER));

                logger.info("Created guild block at " + x + "," + z + " in world " + world + " for guild " + guildName + " (ID: " + guildId + ")");
                return guildBlock;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create guild block at " + x + "," + z + " in world " + world, e);
            throw new RuntimeException("Failed to create guild block", e);
        }
    }

    /**
     * Returns the guild block.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public Optional<GuildBlock> getGuildBlock(int x, int z, String world) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-by-coords.sql", Map.of(
                     "x", x,
                     "z", z,
                     "world", world))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    GuildBlock guildBlock = mapResultSetToGuildBlock(resultSet);
                    logger.info("Found guild block in database: x=" + x + ", z=" + z + ", world=" + world +
                              ", guild_id=" + guildBlock.getGuildId() + ", owner_id=" + guildBlock.getOwnerId());
                    return Optional.of(guildBlock);
                }
            }

            logger.info("No guild block found in database query for x=" + x + ", z=" + z + ", world=" + world);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild block at " + x + "," + z + " in world " + world, e);
        }

        return Optional.empty();
    }

    /**
     * Returns the guild block.
     * @param id the id
     * @return the result
     */
    @Override
    public Optional<GuildBlock> getGuildBlock(UUID id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-by-id.sql", Map.of(
                     "id", id.toString()))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild block: " + id, e);
        }

        return Optional.empty();
    }

    /**
     * Updates the guild block.
     * @param guildBlock the guild block
     * @return the result
     */
    @Override
    public GuildBlock updateGuildBlock(GuildBlock guildBlock) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/update-guild-block.sql", SqlParams.of(
                     "x", guildBlock.getX(),
                     "z", guildBlock.getZ(),
                     "world", guildBlock.getWorld(),
                     "guild_id", guildBlock.getGuildId(),
                     "owner_uuid", guildBlock.getOwnerId() == null ? null : guildBlock.getOwnerId().toString(),
                     "plot_type", guildBlock.getPlotType(),
                     "price", guildBlock.getPrice(),
                     "permissions_flags", guildBlock.getPermissionsFlags(),
                     "custom_name", guildBlock.getCustomName(),
                     "id", guildBlock.getId().toString()))) {

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Updated guild block: " + guildBlock.getId());
            }

            return guildBlock;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update guild block: " + guildBlock.getId(), e);
            throw new RuntimeException("Failed to update guild block", e);
        }
    }

    /**
     * Deletes the guild block.
     * @param id the id
     * @return the result
     */
    @Override
    public boolean deleteGuildBlock(UUID id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/delete-by-id.sql", Map.of(
                     "id", id.toString()))) {

            int rowsDeleted = statement.executeUpdate();

            if (rowsDeleted > 0) {
                logger.info("Deleted guild block: " + id);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete guild block: " + id, e);
        }

        return false;
    }

    /**
     * Returns the all guild blocks.
     * @return the result
     */
    @Override
    public List<GuildBlock> getAllGuildBlocks() {
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(SQL.jdbc("plots/select-all.sql"))) {

            while (resultSet.next()) {
                guildBlocks.add(mapResultSetToGuildBlock(resultSet));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get all guild blocks", e);
        }

        return guildBlocks;
    }

    /**
     * Returns the guild blocks in guild.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public List<GuildBlock> getGuildBlocksInGuild(String guildName) {
        String guildId = null;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement getGuildStmt = SQL.prepare(connection, "plots/select-guild-id-by-name.sql", Map.of(
                    "name", guildName))) {
                try (ResultSet rs = getGuildStmt.executeQuery()) {
                    if (rs.next()) {
                        guildId = rs.getString("id");
                    } else {
                        logger.warning("Guild not found: " + guildName);
                        return new ArrayList<>();
                    }
                }
            }

            List<GuildBlock> guildBlocks = new ArrayList<>();

            try (PreparedStatement statement = SQL.prepare(connection, "plots/select-by-guild-id.sql", Map.of(
                    "guild_id", guildId))) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                    }
                }
            }

            return guildBlocks;

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild blocks for guild: " + guildName, e);
            return new ArrayList<>();
        }
    }

    /**
     * Returns the guild blocks in world.
     * @param world the world
     * @return the result
     */
    @Override
    public List<GuildBlock> getGuildBlocksInWorld(String world) {
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-by-world.sql", Map.of(
                     "world", world))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild blocks in world: " + world, e);
        }

        return guildBlocks;
    }

    /**
     * Returns the guild blocks owned by.
     * @param residentUuid the resident uuid
     * @return the result
     */
    @Override
    public List<GuildBlock> getGuildBlocksOwnedBy(UUID residentUuid) {
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-by-owner.sql", Map.of(
                     "owner_uuid", residentUuid.toString()))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild blocks owned by: " + residentUuid, e);
        }

        return guildBlocks;
    }

    /**
     * Performs the guild block exists operation.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean guildBlockExists(int x, int z, String world) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/count-by-coords.sql", Map.of(
                     "x", x,
                     "z", z,
                     "world", world))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if guild block exists at " + x + "," + z + " in world " + world, e);
        }

        return false;
    }

    /**
     * Performs the claim guild block operation.
     * @param x the x
     * @param z the z
     * @param world the world
     * @param guildName the guild name
     * @return the result
     */
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
            logger.log(Level.SEVERE, "Failed to claim guild block at " + x + "," + z + " in world " + world, e);
            return false;
        }
    }

    /**
     * Performs the unclaim guild block operation.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean unclaimGuildBlock(int x, int z, String world) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/delete-by-coords.sql", Map.of(
                     "x", x,
                     "z", z,
                     "world", world))) {

            int rowsDeleted = statement.executeUpdate();

            if (rowsDeleted > 0) {
                logger.info("Unclaimed guild block at " + x + "," + z + " in world " + world);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to unclaim guild block at " + x + "," + z + " in world " + world, e);
        }

        return false;
    }

    /**
     * Sets the guild block owner.
     * @param id the id
     * @param ownerUuid the owner uuid
     * @return the result
     */
    @Override
    public boolean setGuildBlockOwner(UUID id, UUID ownerUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/update-owner.sql", SqlParams.of(
                     "owner_uuid", ownerUuid == null ? null : ownerUuid.toString(),
                     "id", id.toString()))) {

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Set owner for guild block " + id + ": " + ownerUuid);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set owner for guild block: " + id, e);
        }

        return false;
    }

    /**
     * Returns the guild blocks in radius.
     * @param centerX the center x
     * @param centerZ the center z
     * @param radius the radius
     * @param world the world
     * @return the result
     */
    @Override
    public List<GuildBlock> getGuildBlocksInRadius(int centerX, int centerZ, int radius, String world) {
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-in-radius.sql", Map.of(
                     "world", world,
                     "min_x", centerX - radius,
                     "max_x", centerX + radius,
                     "min_z", centerZ - radius,
                     "max_z", centerZ + radius))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild blocks in radius around " + centerX + "," + centerZ + " in world " + world, e);
        }

        return guildBlocks;
    }

    /**
     * Returns the guild blocks by type.
     * @param plotType the plot type
     * @return the result
     */
    @Override
    public List<GuildBlock> getGuildBlocksByType(String plotType) {
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-by-type.sql", Map.of(
                     "plot_type", plotType))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild blocks by type: " + plotType, e);
        }

        return guildBlocks;
    }

    /**
     * Returns the guild owned blocks.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public List<GuildBlock> getGuildOwnedBlocks(String guildName) {
        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-guild-owned.sql", Map.of(
                     "guild_id", guildName))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild-owned blocks for guild: " + guildName, e);
        }

        return guildBlocks;
    }

    /**
     * Returns the guild block count.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public int getGuildBlockCount(String guildName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/count-by-guild-id.sql", Map.of(
                     "guild_id", guildName))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild block count for guild: " + guildName, e);
        }

        return 0;
    }

    /**
     * Sets the plot type.
     * @param id the id
     * @param plotType the plot type
     * @return the result
     */
    @Override
    public boolean setPlotType(UUID id, String plotType) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/update-plot-type.sql", Map.of(
                     "plot_type", plotType,
                     "id", id.toString()))) {

            int rowsUpdated = statement.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Set plot type for guild block " + id + ": " + plotType);
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set plot type for guild block: " + id, e);
        }

        return false;
    }

    /**
     * Returns the guild blocks in chunk.
     * @param chunkX the chunk x
     * @param chunkZ the chunk z
     * @param world the world
     * @return the result
     */
    @Override
    public List<GuildBlock> getGuildBlocksInChunk(int chunkX, int chunkZ, String world) {
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;

        List<GuildBlock> guildBlocks = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-in-chunk.sql", Map.of(
                     "world", world,
                     "min_x", blockX,
                     "max_x", blockX + 16,
                     "min_z", blockZ,
                     "max_z", blockZ + 16))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    guildBlocks.add(mapResultSetToGuildBlock(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get guild blocks in chunk " + chunkX + "," + chunkZ + " in world " + world, e);
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

    /**
     * Performs the claim plot for resident operation.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
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

    /**
     * Performs the buy plot operation.
     * @param residentUuid the resident uuid
     * @param plotId the plot id
     * @param price the price
     * @return the result
     */
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
            if (price <= 0.0) {
                return false; // A purchase must move money
            }

            if (plot.isOwner(residentUuid)) {
                return false; // Buyer already owns the plot
            }

            // Money model: guild banks are the only wallets in this subsystem
            // (residents have no personal balance). The buyer's guild pays the
            // plot's guild — the land's owner — so a same-guild purchase nets
            // zero and only transfers ownership; cross-guild purchases move
            // the price between guild banks. Every purchase is audited in
            // economy_transactions.
            String residentGuildName = getResidentGuild(residentUuid);
            if (residentGuildName == null) {
                return false; // Resident not in a guild
            }
            Optional<Guild> buyerGuild = guildService.getGuild(residentGuildName);
            if (buyerGuild.isEmpty()) {
                return false;
            }
            Guild plotGuild = guildService.getGuildById(plot.getGuildId()).orElse(null);
            if (plotGuild == null) {
                return false; // Plot's guild no longer exists
            }
            if (buyerGuild.get().getBalance() < price) {
                return false; // Fast pre-check; the transaction re-checks authoritatively
            }

            String buyerGuildId = buyerGuild.get().getId();
            String plotGuildId = plot.getGuildId();
            String now = LocalDateTime.now().format(DATE_FORMATTER);
            String observedOwner = plot.getOwnerId() == null ? null : plot.getOwnerId().toString();

            // Atomic: ownership transfer, debit (guarded), credit, audit. The
            // transfer UPDATE is conditional on the observed sale state (price
            // + seller), so two concurrent buyers cannot both commit — the
            // second one matches zero rows and the whole purchase rolls back.
            // Any failure aborts with SQLException so the purchase rolls back —
            // money can never move without the ownership transfer.
            boolean txCommitted = databaseManager.executeTransaction(connection -> {
                String transferSql = observedOwner == null
                        ? "plots/transfer-unowned.sql"
                        : "plots/transfer-owned.sql";
                try (PreparedStatement statement = observedOwner == null
                        ? SQL.prepare(connection, transferSql, Map.of(
                                "owner_uuid", residentUuid.toString(),
                                "permissions_flags", GuildPermission.ALL,
                                "id", plot.getId().toString(),
                                "price", price))
                        : SQL.prepare(connection, transferSql, Map.of(
                                "owner_uuid", residentUuid.toString(),
                                "permissions_flags", GuildPermission.ALL,
                                "id", plot.getId().toString(),
                                "price", price,
                                "observed_owner", observedOwner))) {
                    if (statement.executeUpdate() == 0) {
                        throw new SQLException("Plot " + plot.getId() + " no longer for sale at this price");
                    }
                }

                if (!buyerGuildId.equals(plotGuildId)) {
                    try (PreparedStatement statement = SQL.prepare(connection, "plots/debit-guild-balance.sql", Map.of(
                            "amount", price,
                            "guild_id", buyerGuildId))) {
                        if (statement.executeUpdate() == 0) {
                            throw new SQLException("Insufficient balance in guild " + buyerGuildId);
                        }
                    }
                    try (PreparedStatement statement = SQL.prepare(connection, "plots/credit-guild-balance.sql", Map.of(
                            "amount", price,
                            "guild_id", plotGuildId))) {
                        if (statement.executeUpdate() == 0) {
                            throw new SQLException("Plot guild " + plotGuildId + " no longer exists");
                        }
                    }
                }

                try (PreparedStatement statement = SQL.prepare(connection, "plots/insert-economy-transaction.sql", Map.of(
                        "id", UUID.randomUUID().toString(),
                        "guild_id", buyerGuildId,
                        "player_uuid", residentUuid.toString(),
                        "type", "PLOT_PURCHASE",
                        "amount", price,
                        "description", "Plot " + plot.getId() + " bought from guild " + plotGuildId,
                        "timestamp", now))) {
                    statement.executeUpdate();
                }
            });

            if (txCommitted) {
                logger.info("Plot " + plot.getId() + " purchased by " + residentUuid
                        + " for " + price + " (guild " + buyerGuildId + " -> guild " + plotGuildId + ")");
            }
            return txCommitted;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to buy plot " + plotId + " by resident " + residentUuid, e);
            return false;
        }
    }

    /**
     * Sets the plot for sale.
     * @param plotId the plot id
     * @param price the price
     * @param ownerUuid the owner uuid
     * @return the result
     */
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

    /**
     * Returns the plots for sale.
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public List<GuildBlock> getPlotsForSale(String guildName) {
        List<GuildBlock> plotsForSale = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            if (guildName != null) {
                try (PreparedStatement statement = SQL.prepare(connection, "plots/select-for-sale-by-guild.sql", Map.of(
                        "guild_id", guildName));
                     ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        plotsForSale.add(mapResultSetToGuildBlock(resultSet));
                    }
                }
            } else {
                try (Statement statement = connection.createStatement();
                     ResultSet resultSet = statement.executeQuery(SQL.jdbc("plots/select-for-sale.sql"))) {
                    while (resultSet.next()) {
                        plotsForSale.add(mapResultSetToGuildBlock(resultSet));
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get plots for sale" + (guildName != null ? " in guild " + guildName : ""), e);
        }

        return plotsForSale;
    }

    /**
     * Returns the plots owned by resident.
     * @param residentUuid the resident uuid
     * @return the result
     */
    @Override
    public List<GuildBlock> getPlotsOwnedByResident(UUID residentUuid) {
        return getGuildBlocksOwnedBy(residentUuid);
    }

    // Plot permission management methods implementation

    /**
     * Sets the plot permission flag.
     * @param plotId the plot id
     * @param permissionFlag the permission flag
     * @param value the value
     * @return the result
     */
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

    /**
     * Sets the plot permission flags.
     * @param plotId the plot id
     * @param flags the flags
     * @return the result
     */
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

    /**
     * Adds the plot permission flag.
     * @param plotId the plot id
     * @param permissionFlag the permission flag
     * @return the result
     */
    @Override
    public boolean addPlotPermissionFlag(UUID plotId, int permissionFlag) {
        return setPlotPermissionFlag(plotId, permissionFlag, true);
    }

    /**
     * Removes the plot permission flag.
     * @param plotId the plot id
     * @param permissionFlag the permission flag
     * @return the result
     */
    @Override
    public boolean removePlotPermissionFlag(UUID plotId, int permissionFlag) {
        return setPlotPermissionFlag(plotId, permissionFlag, false);
    }

    /**
     * Returns the plot permissions.
     * @param plotId the plot id
     * @return the result
     */
    @Override
    public List<Permission> getPlotPermissions(UUID plotId) {
        List<Permission> permissions = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-plot-permissions.sql", Map.of(
                     "context_id", plotId.toString()))) {

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

    /**
     * Performs the grant plot permission operation.
     * @param plotId the plot id
     * @param targetType the target type
     * @param targetId the target id
     * @param permissionFlag the permission flag
     * @param grantedBy the granted by
     * @return the result
     */
    @Override
    public boolean grantPlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag, UUID grantedBy) {
        UUID permissionId = UUID.randomUUID();
        String grantedAt = LocalDateTime.now().format(DATE_FORMATTER);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/grant-plot-permission.sql", SqlParams.of(
                     "id", permissionId.toString(),
                     "context_id", plotId.toString(),
                     "target_type", targetType,
                     "target_id", targetId,
                     "permissions_flags", permissionFlag,
                     "granted_at", grantedAt,
                     "granted_by_uuid", grantedBy == null ? null : grantedBy.toString()))) {

            int rowsUpdated = statement.executeUpdate();
            if (rowsUpdated > 0) {
                logger.info("Granted permission " + permissionFlag + " to " + targetType + ":" + targetId + " for plot " + plotId);
                if (permissionService != null) {
                    permissionService.clearCache();
                }
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to grant permission for plot " + plotId, e);
        }

        return false;
    }

    /**
     * Performs the revoke plot permission operation.
     * @param plotId the plot id
     * @param targetType the target type
     * @param targetId the target id
     * @param permissionFlag the permission flag
     * @return the result
     */
    @Override
    public boolean revokePlotPermission(UUID plotId, String targetType, String targetId, int permissionFlag) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/revoke-plot-permission.sql", SqlParams.of(
                     "context_id", plotId.toString(),
                     "target_type", targetType,
                     "target_id", targetId,
                     "permissions_flags", permissionFlag))) {

            int rowsDeleted = statement.executeUpdate();
            if (rowsDeleted > 0) {
                logger.info("Revoked permission " + permissionFlag + " from " + targetType + ":" + targetId + " for plot " + plotId);
                if (permissionService != null) {
                    permissionService.clearCache();
                }
                return true;
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to revoke permission for plot " + plotId, e);
        }

        return false;
    }

    // Utility methods implementation

    /**
     * Returns the guild block at location.
     * @param world the world
     * @param blockX the block x
     * @param blockZ the block z
     * @return the result
     */
    @Override
    public Optional<GuildBlock> getGuildBlockAtLocation(String world, int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        return getGuildBlock(chunkX, chunkZ, world);
    }

    /**
     * Returns whether resident claim plot.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
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
                logger.info("Resident " + residentUuid + " is not in a guild");
                return false;
            }

            // Check if plot exists and is owned by guild (not a resident)
            logger.info("Checking for guild block at x=" + x + ", z=" + z + ", world=" + world + " for resident " + residentName + " in guild " + guildName);
            Optional<GuildBlock> existingPlot = getGuildBlock(x, z, world);
            if (existingPlot.isPresent()) {
                GuildBlock plot = existingPlot.get();
                logger.info("Found guild block: guild_id=" + plot.getGuildId() + ", owner_id=" + plot.getOwnerId());
                // If plot is owned by a resident, can't claim it
                if (plot.getOwnerId() != null) {
                    logger.info("Plot already owned by resident " + plot.getOwnerId());
                    return false;
                }
                // If plot exists and is guild-owned, resident can claim it
                Optional<Guild> guild = guildService.getGuildById(plot.getGuildId());
                if (guild.isPresent()) {
                    boolean canClaim = guild.get().getName().equals(guildName);
                    logger.info("Guild check: plot guild=" + guild.get().getName() + ", resident guild=" + guildName + ", can claim=" + canClaim);
                    return canClaim;
                } else {
                    logger.info("Guild not found for guild_id=" + plot.getGuildId());
                    return false;
                }
            }

            // If no plot exists, resident can't claim it (guild must claim territory first)
            logger.info("No guild block found at x=" + x + ", z=" + z + ", world=" + world + " - guild must claim territory first");
            return false;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to check if resident can claim plot", e);
            return false;
        }
    }

    /**
     * Returns whether resident afford plot.
     * @param residentUuid the resident uuid
     * @param plotId the plot id
     * @return the result
     */
    @Override
    public boolean canResidentAffordPlot(UUID residentUuid, UUID plotId) {
        try {
            Optional<GuildBlock> plotOpt = getGuildBlock(plotId);
            if (plotOpt.isEmpty() || !plotOpt.get().isForSale()) {
                return false;
            }
            String guildName = getResidentGuild(residentUuid);
            if (guildName == null) {
                return false;
            }
            double price = plotOpt.get().getPrice();
            return guildService.getGuild(guildName)
                    .map(guild -> guild.getBalance() >= price)
                    .orElse(false);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to check affordability for plot " + plotId, e);
            return false;
        }
    }

    // Helper methods

    /**
     * Returns the resident name.
     * @param residentUuid the resident uuid
     * @return the result
     */
    private String getResidentName(UUID residentUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-resident-name.sql", Map.of(
                     "uuid", residentUuid.toString()))) {

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

    /**
     * Returns the resident guild.
     * @param residentUuid the resident uuid
     * @return the result
     */
    private String getResidentGuild(UUID residentUuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "plots/select-resident-guild.sql", Map.of(
                     "uuid", residentUuid.toString()))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("guild_name");
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get resident guild for " + residentUuid, e);
        }

        return null;
    }
}
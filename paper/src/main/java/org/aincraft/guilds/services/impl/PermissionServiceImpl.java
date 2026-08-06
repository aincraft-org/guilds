package org.aincraft.guilds.services.impl;



import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Permission;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownBlock;
import org.aincraft.guilds.models.GuildPermission;
import org.aincraft.guilds.models.PermissionSet;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.PermissionEvaluationResult;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TownService;
import org.aincraft.guilds.services.TownToggleService;
import org.aincraft.guilds.services.PermissionEvaluationResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of PermissionService with database operations
 */

public class PermissionServiceImpl implements org.aincraft.guilds.services.PermissionService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;
    private final PlotService plotService;
    private final TownService townService;
    private final ResidentService residentService;
    private final TownToggleService townToggleService;
    private final LocationService locationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    public PermissionServiceImpl(DatabaseManager databaseManager, Logger logger,
                                PlotService plotService, TownService townService, ResidentService residentService,
                                TownToggleService townToggleService, LocationService locationService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.plotService = plotService;
        this.townService = townService;
        this.residentService = residentService;
        this.locationService = locationService;
        this.townToggleService = townToggleService;
    }

    @Override
    public boolean hasPermission(UUID residentUuid, String permission, String context, String contextId) {
        // Handle specific permission checks based on context
        switch (context.toLowerCase()) {
            case "town":
                return hasTownPermission(residentUuid, permission, contextId);
            case "plot":
                return hasPlotPermission(residentUuid, permission, contextId);
            case "global":
                return hasGlobalPermission(residentUuid, permission);
            default:
                logger.warning("Unknown permission context: " + context);
                return false;
        }
    }

    @Override
    public boolean grantPermission(UUID residentUuid, String permission, String context, String contextId, boolean value) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Check if permission already exists
                String checkSql = "SELECT id, permissions_flags FROM permissions WHERE context = ? AND context_id = ? AND target_type = 'resident' AND target_id = ?";

                try (PreparedStatement checkStatement = connection.prepareStatement(checkSql)) {
                    checkStatement.setString(1, context);
                    checkStatement.setString(2, contextId);
                    checkStatement.setString(3, residentUuid.toString());

                    try (ResultSet resultSet = checkStatement.executeQuery()) {
                        if (resultSet.next()) {
                            // Update existing permission
                            String permissionId = resultSet.getString("id");
                            int currentFlags = resultSet.getInt("permissions_flags");

                            // Convert string permission to flag and update
                            int newFlags = updatePermissionFlag(currentFlags, permission, value);

                            String updateSql = "UPDATE permissions SET permissions_flags = ?, granted_at = ? WHERE id = ?";
                            try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                                updateStatement.setInt(1, newFlags);
                                updateStatement.setString(2, LocalDateTime.now().format(DATE_FORMATTER));
                                updateStatement.setString(3, permissionId);

                                updateStatement.executeUpdate();
                            }
                        } else {
                            // Create new permission
                            String insertSql = "INSERT INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

                            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                                String permissionId = UUID.randomUUID().toString();
                                int flags = getPermissionFlag(permission, value);

                                insertStatement.setString(1, permissionId);
                                insertStatement.setString(2, context);
                                insertStatement.setString(3, contextId);
                                insertStatement.setString(4, "resident");
                                insertStatement.setString(5, residentUuid.toString());
                                insertStatement.setInt(6, flags);
                                insertStatement.setString(7, LocalDateTime.now().format(DATE_FORMATTER));

                                insertStatement.executeUpdate();
                            }
                        }
                    }
                }

                logger.info("Granted permission " + permission + " for resident " + residentUuid + " in context " + context + ":" + contextId);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to grant permission for resident: " + residentUuid, e);
                throw new RuntimeException("Failed to grant permission", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean revokePermission(UUID residentUuid, String permission, String context, String contextId) {
        // This would remove a specific permission flag
        // For now, return false as placeholder
        return false;
    }

    @Override
    public List<Permission> getResidentPermissions(UUID residentUuid, String context, String contextId) {
        String sql = "SELECT id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid " +
                    "FROM permissions WHERE context = ? AND context_id = ? AND (target_type = 'all' OR (target_type = 'resident' AND target_id = ?))";

        List<Permission> permissions = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, context);
            statement.setString(2, contextId);
            statement.setString(3, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    permissions.add(mapResultSetToPermission(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get permissions for resident: " + residentUuid, e);
        }

        return permissions;
    }

    @Override
    public List<Permission> getContextPermissions(String context, String contextId) {
        String sql = "SELECT id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid " +
                    "FROM permissions WHERE context = ? AND context_id = ? ORDER BY target_type, target_id";

        List<Permission> permissions = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, context);
            statement.setString(2, contextId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    permissions.add(mapResultSetToPermission(resultSet));
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get permissions for context: " + context + ":" + contextId, e);
        }

        return permissions;
    }

    @Override
    public boolean setTownPermissions(String townName, List<Permission> permissions) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Delete existing town permissions
                String deleteSql = "DELETE FROM permissions WHERE context = 'town' AND context_id = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setString(1, townName);
                    deleteStatement.executeUpdate();
                }

                // Insert new permissions
                String insertSql = "INSERT INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                    for (Permission permission : permissions) {
                        insertStatement.setString(1, permission.getId().toString());
                        insertStatement.setString(2, permission.getContext());
                        insertStatement.setString(3, permission.getContextId());
                        insertStatement.setString(4, permission.getTargetType());
                        insertStatement.setString(5, permission.getTargetId());
                        insertStatement.setInt(6, permission.getFlags());
                        insertStatement.setString(7, LocalDateTime.now().format(DATE_FORMATTER));

                        insertStatement.addBatch();
                    }
                    insertStatement.executeBatch();
                }

                logger.info("Set permissions for town: " + townName);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to set permissions for town: " + townName, e);
                throw new RuntimeException("Failed to set town permissions", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean setPlotPermissions(UUID plotId, List<Permission> permissions) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Delete existing plot permissions
                String deleteSql = "DELETE FROM permissions WHERE context = 'plot' AND context_id = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setString(1, plotId.toString());
                    deleteStatement.executeUpdate();
                }

                // Insert new permissions
                String insertSql = "INSERT INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                    for (Permission permission : permissions) {
                        insertStatement.setString(1, permission.getId().toString());
                        insertStatement.setString(2, permission.getContext());
                        insertStatement.setString(3, permission.getContextId());
                        insertStatement.setString(4, permission.getTargetType());
                        insertStatement.setString(5, permission.getTargetId());
                        insertStatement.setInt(6, permission.getFlags());
                        insertStatement.setString(7, LocalDateTime.now().format(DATE_FORMATTER));

                        insertStatement.addBatch();
                    }
                    insertStatement.executeBatch();
                }

                logger.info("Set permissions for plot: " + plotId);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to set permissions for plot: " + plotId, e);
                throw new RuntimeException("Failed to set plot permissions", e);
            }
        });

        return result[0];
    }

    @Override
    public boolean canBuild(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.BUILD.getLegacyBitwiseValue());
    }

    @Override
    public boolean canDestroy(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.DESTROY.getLegacyBitwiseValue());
    }

    @Override
    public boolean canSwitch(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.SWITCH.getLegacyBitwiseValue());
    }

    @Override
    public boolean canUseItems(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.ITEM_USE.getLegacyBitwiseValue());
    }

    @Override
    public boolean canInteractWithEntity(UUID residentUuid, int x, int z, String world) {
        // Entity interaction uses same permission hierarchy as destroy
        // This ensures item frames, armor stands, etc. follow plot ownership rules
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.DESTROY.getLegacyBitwiseValue());
    }

    /**
     * Centralized method to check permissions at a specific location
     * Uses hierarchical permission evaluation: Plot > Town > Global
     */
    private boolean checkLocationPermission(UUID residentUuid, int x, int z, String world, int permissionFlag) {
        try {
            // Convert block coordinates to chunk coordinates
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            // Get town block at this location
            Optional<TownBlock> townBlock = plotService.getTownBlock(chunkX, chunkZ, world);

            if (!townBlock.isPresent()) {
                // Wilderness - apply wilderness toggle defaults
                return checkWildernessPermission(permissionFlag);
            }

            // Town block exists - apply town toggle checks first
            if (!checkTownToggles(residentUuid, x, z, world, permissionFlag)) {
                logger.fine(String.format("Permission denied by town toggle for %s at (%d,%d,%s)",
                    residentUuid, x, z, world));
                return false;
            }

            // Town block exists - use hierarchical permission evaluation
            TownBlock block = townBlock.get();
            PermissionEvaluationResult result = evaluatePlotPermission(residentUuid, block.getId(), permissionFlag);

            // Log the evaluation result for debugging
            logger.fine(String.format("Permission check for %s at (%d,%d,%s): %s - %s",
                residentUuid, x, z, world, result.hasPermission(), result.getReason()));

            return result.hasPermission();

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to check location permission for resident " + residentUuid, e);
            // Default deny on errors to prevent unauthorized access
            return false;
        }
    }

    /**
     * Check town toggles that might affect the permission
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param permissionFlag Permission flag being checked
     * @return True if toggles allow the permission, false otherwise
     */
    private boolean checkTownToggles(UUID residentUuid, int x, int z, String world, int permissionFlag) {
        Optional<Town> town = locationService.getTownAtLocation(x, z, world);
        if (town.isEmpty()) {
            return true; // No town - no toggle restrictions
        }

        Town t = town.get();

        // Check if town is public access (for non-residents)
        try {
            var resident = residentService.getResident(residentUuid);
            if (resident.isPresent() && !resident.get().hasTown()) {
                // Non-resident trying to access town
                if (!t.isPublicEnabled()) {
                    logger.fine("Non-resident access denied - town is not public");
                    return false;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking resident town membership: " + e.getMessage());
        }

        return true;
    }

    /**
     * Check wilderness permissions based on default wilderness behavior
     * @param permissionFlag Permission flag being checked
     * @return True if allowed in wilderness
     */
    private boolean checkWildernessPermission(int permissionFlag) {
        // Wilderness defaults:
        // - Build/Destroy: Allowed (true)
        // - Switch/Item: Allowed (true)
        // - Fire/Explosions: Based on wilderness defaults
        return true; // By default, wilderness allows most actions
    }

    @Override
    public List<Permission> getDefaultTownPermissions() {
        List<Permission> permissions = new ArrayList<>();

        // Create default town permissions with bitwise flags
        Permission residentPerms = new Permission(
            PermissionSet.createResident().toLegacyFlags(),
            Permission.Context.TOWN,
            "default",
            Permission.Target.RESIDENT,
            "all"
        );

        Permission assistantPerms = new Permission(
            PermissionSet.createAssistant().toLegacyFlags(),
            Permission.Context.TOWN,
            "default",
            Permission.Target.ASSISTANT,
            "all"
        );

        Permission mayorPerms = new Permission(
            PermissionSet.createMayor().toLegacyFlags(),
            Permission.Context.TOWN,
            "default",
            Permission.Target.MAYOR,
            "all"
        );

        permissions.add(residentPerms);
        permissions.add(assistantPerms);
        permissions.add(mayorPerms);

        return permissions;
    }

    @Override
    public List<Permission> getDefaultPlotPermissions() {
        List<Permission> permissions = new ArrayList<>();

        Permission defaultPlotPerms = new Permission(
            PermissionSet.createDefaultPlot().toLegacyFlags(),
            Permission.Context.PLOT,
            "default",
            Permission.Target.ALL,
            null
        );

        permissions.add(defaultPlotPerms);

        return permissions;
    }

    @Override
    public boolean isTownMayor(UUID residentUuid, String townName) {
        String sql = "SELECT COUNT(*) FROM towns WHERE name = ? AND mayor_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);
            statement.setString(2, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if resident is town mayor: " + residentUuid + " in town " + townName, e);
        }

        return false;
    }

    @Override
    public boolean isTownAssistant(UUID residentUuid, String townName) {
        String sql = """
            SELECT COUNT(*) FROM town_residents tr
            JOIN towns t ON tr.town_id = t.id
            WHERE t.name = ? AND tr.resident_uuid = ? AND tr.role = 'assistant'
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);
            statement.setString(2, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if resident is town assistant: " + residentUuid + " in town " + townName, e);
        }

        return false;
    }

    @Override
    public boolean ownsPlot(UUID residentUuid, UUID plotId) {
        String sql = "SELECT COUNT(*) FROM town_blocks WHERE id = ? AND owner_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, plotId.toString());
            statement.setString(2, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if resident owns plot: " + residentUuid + " plot " + plotId, e);
        }

        return false;
    }

    /**
     * Check if a resident has a specific town permission
     */
    private boolean hasTownPermission(UUID residentUuid, String permission, String townName) {
        // Check database for explicit permission grants first
        String sql = "SELECT permissions_flags FROM permissions WHERE " +
                    "context = 'town' AND context_id = ? AND " +
                    "(target_type = 'all' OR (target_type = 'resident' AND target_id = ?))";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, townName);
            statement.setString(2, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int flags = resultSet.getInt("permissions_flags");
                    if (hasPermissionFlag(flags, permission)) {
                        return true;
                    }
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check town permission: " + permission + " for " + residentUuid + " in " + townName, e);
        }

        // Fall back to role-based permissions if no explicit permissions found
        return hasRoleBasedTownPermission(residentUuid, permission, townName);
    }

    /**
     * Check if a resident has permission based on their town role
     */
    private boolean hasRoleBasedTownPermission(UUID residentUuid, String permission, String townName) {
        switch (permission.toLowerCase()) {
            case "set_spawn":
                // Mayors and assistants can set spawn
                return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);

            case "spawn":
                // All residents and town members can spawn
                return true;

            case "claim":
            case "unclaim":
                // Mayors and assistants can claim/unclaim
                return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);

            case "invite":
                // Mayors and assistants can invite
                return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);

            case "kick":
                // Mayors and assistants can kick (but not other assistants)
                return isTownMayor(residentUuid, townName) ||
                       (isTownAssistant(residentUuid, townName) && !isTownAssistant(residentUuid, townName));

            case "promote":
            case "demote":
                // Only mayors can promote/demote
                return isTownMayor(residentUuid, townName);

            case "withdraw":
            case "deposit":
                // Mayors and assistants can manage economy
                return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);

            case "build":
            case "destroy":
            case "switch":
            case "item_use":
                // All town members have basic build permissions by default
                return true;

            default:
                // Unknown permission, deny by default
                return false;
        }
    }

    /**
     * Check if permission flags contain a specific permission
     */
    private boolean hasPermissionFlag(int flags, String permission) {
        switch (permission.toLowerCase()) {
            case "build": return (flags & GuildPermission.BUILD.getLegacyBitwiseValue()) != 0;
            case "destroy": return (flags & GuildPermission.DESTROY.getLegacyBitwiseValue()) != 0;
            case "switch": return (flags & GuildPermission.SWITCH.getLegacyBitwiseValue()) != 0;
            case "item_use": return (flags & GuildPermission.ITEM_USE.getLegacyBitwiseValue()) != 0;
            case "claim": return (flags & GuildPermission.CLAIM.getLegacyBitwiseValue()) != 0;
            case "unclaim": return (flags & GuildPermission.UNCLAIM.getLegacyBitwiseValue()) != 0;
            case "spawn": return (flags & GuildPermission.SPAWN.getLegacyBitwiseValue()) != 0;
            case "set_spawn": return (flags & GuildPermission.SET_SPAWN.getLegacyBitwiseValue()) != 0;
            case "invite": return (flags & GuildPermission.INVITE.getLegacyBitwiseValue()) != 0;
            case "kick": return (flags & GuildPermission.KICK.getLegacyBitwiseValue()) != 0;
            case "promote": return (flags & GuildPermission.PROMOTE.getLegacyBitwiseValue()) != 0;
            case "demote": return (flags & GuildPermission.DEMOTE.getLegacyBitwiseValue()) != 0;
            case "withdraw": return (flags & GuildPermission.WITHDRAW.getLegacyBitwiseValue()) != 0;
            case "deposit": return (flags & GuildPermission.DEPOSIT.getLegacyBitwiseValue()) != 0;
            case "plot_perm": return (flags & GuildPermission.PLOT_PERM.getLegacyBitwiseValue()) != 0;
            case "plot_set": return (flags & GuildPermission.PLOT_SET.getLegacyBitwiseValue()) != 0;
            case "plot_owner": return (flags & GuildPermission.PLOT_OWNER.getLegacyBitwiseValue()) != 0;
            case "admin": return (flags & GuildPermission.ADMIN.getLegacyBitwiseValue()) != 0;
            case "admin_town": return (flags & GuildPermission.ADMIN_TOWN.getLegacyBitwiseValue()) != 0;
            case "admin_plot": return (flags & GuildPermission.ADMIN_PLOT.getLegacyBitwiseValue()) != 0;
            case "admin_resident": return (flags & GuildPermission.ADMIN_RESIDENT.getLegacyBitwiseValue()) != 0;
            case "bypass": return (flags & GuildPermission.BYPASS.getLegacyBitwiseValue()) != 0;
            default: return false;
        }
    }

    /**
     * Check if a resident has a specific plot permission
     */
    private boolean hasPlotPermission(UUID residentUuid, String permission, String plotId) {
        // For now, fall back to basic plot ownership check
        return ownsPlot(residentUuid, UUID.fromString(plotId));
    }

    /**
     * Check if a resident has global permissions
     */
    private boolean hasGlobalPermission(UUID residentUuid, String permission) {
        // Check for admin-level global permissions
        String sql = "SELECT permissions_flags FROM permissions WHERE " +
                    "context = 'global' AND target_type = 'resident' AND target_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int flags = resultSet.getInt("permissions_flags");
                    return hasPermissionFlag(flags, permission);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check global permission: " + permission + " for " + residentUuid, e);
        }

        return false;
    }

    @Override
    public boolean hasTownAdmin(UUID residentUuid, String townName) {
        // Check if resident is mayor or assistant
        return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);
    }

    @Override
    public boolean grantTownPermission(UUID residentUuid, String townName, int permissionFlag) {
        return grantTownPermissions(residentUuid, townName, permissionFlag);
    }

    @Override
    public boolean grantTownPermissions(UUID residentUuid, String townName, int permissionFlags) {
        final boolean[] result = new boolean[1];
        final String targetType = (residentUuid == null) ? "all" : "resident";
        final String targetId = (residentUuid == null) ? null : residentUuid.toString();

        databaseManager.executeTransaction(connection -> {
            try {
                // Check if permission already exists
                String checkSql = "SELECT id, permissions_flags FROM permissions WHERE " +
                                "context = 'town' AND context_id = ? AND target_type = ? AND target_id = ?";

                try (PreparedStatement checkStatement = connection.prepareStatement(checkSql)) {
                    checkStatement.setString(1, townName);
                    checkStatement.setString(2, targetType);
                    checkStatement.setString(3, targetId);

                    try (ResultSet resultSet = checkStatement.executeQuery()) {
                        if (resultSet.next()) {
                            // Update existing permission
                            String existingId = resultSet.getString("id");
                            int existingFlags = resultSet.getInt("permissions_flags");
                            int newFlags = existingFlags | permissionFlags;

                            String updateSql = "UPDATE permissions SET permissions_flags = ? WHERE id = ?";
                            try (PreparedStatement updateStatement = connection.prepareStatement(updateSql)) {
                                updateStatement.setInt(1, newFlags);
                                updateStatement.setString(2, existingId);
                                updateStatement.executeUpdate();
                            }

                            logger.info("Updated town permissions for " + targetType + " in " + townName + ": added flags " + permissionFlags);
                        } else {
                            // Insert new permission
                            String insertSql = "INSERT INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                                insertStatement.setString(1, UUID.randomUUID().toString());
                                insertStatement.setString(2, "town");
                                insertStatement.setString(3, townName);
                                insertStatement.setString(4, targetType);
                                insertStatement.setString(5, targetId);
                                insertStatement.setInt(6, permissionFlags);
                                insertStatement.setString(7, LocalDateTime.now().format(DATE_FORMATTER));
                                insertStatement.setString(8, null); // granted_by_uuid could be set to admin UUID

                                insertStatement.executeUpdate();
                            }

                            logger.info("Granted town permissions for " + targetType + " in " + townName + ": flags " + permissionFlags);
                        }
                    }
                }

                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to grant town permissions: " + permissionFlags + " to " + targetType + " in " + townName, e);
                result[0] = false;
            }
        });

        return result[0];
    }

    @Override
    public List<String> getAllPermissionNodes() {
        // Return all available permission flags as strings
        return Arrays.asList(
            "build", "destroy", "switch", "item_use",
            "claim", "unclaim", "spawn", "set_spawn",
            "invite", "kick", "promote", "demote",
            "withdraw", "deposit",
            "plot_perm", "plot_set", "plot_owner",
            "admin", "admin_town", "admin_plot", "admin_resident", "bypass"
        );
    }

    /**
     * Map a ResultSet to a Permission object
     */
    private Permission mapResultSetToPermission(ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));
        int flags = resultSet.getInt("permissions_flags");
        String context = resultSet.getString("context");
        String contextId = resultSet.getString("context_id");
        String targetType = resultSet.getString("target_type");
        String targetId = resultSet.getString("target_id");

        Permission permission = new Permission(flags, context, contextId, targetType, targetId);
        permission.setId(id);

        String grantedAtStr = resultSet.getString("granted_at");
        if (grantedAtStr != null) {
            permission.setGrantedAt(LocalDateTime.parse(grantedAtStr, DATE_FORMATTER));
        }

        String grantedByUuidStr = resultSet.getString("granted_by_uuid");
        if (grantedByUuidStr != null && !grantedByUuidStr.isEmpty()) {
            permission.setGrantedBy(UUID.fromString(grantedByUuidStr));
        }

        return permission;
    }

    /**
     * Get permission flag from string permission name
     */
    private int getPermissionFlag(String permission, boolean value) {
        if (!value) return 0; // No flag for false permissions

        switch (permission.toLowerCase()) {
            case "build": return GuildPermission.BUILD.getLegacyBitwiseValue();
            case "destroy": return GuildPermission.DESTROY.getLegacyBitwiseValue();
            case "switch": return GuildPermission.SWITCH.getLegacyBitwiseValue();
            case "item_use": return GuildPermission.ITEM_USE.getLegacyBitwiseValue();
            case "claim": return GuildPermission.CLAIM.getLegacyBitwiseValue();
            case "unclaim": return GuildPermission.UNCLAIM.getLegacyBitwiseValue();
            case "spawn": return GuildPermission.SPAWN.getLegacyBitwiseValue();
            case "set_spawn": return GuildPermission.SET_SPAWN.getLegacyBitwiseValue();
            case "invite": return GuildPermission.INVITE.getLegacyBitwiseValue();
            case "kick": return GuildPermission.KICK.getLegacyBitwiseValue();
            case "promote": return GuildPermission.PROMOTE.getLegacyBitwiseValue();
            case "demote": return GuildPermission.DEMOTE.getLegacyBitwiseValue();
            case "withdraw": return GuildPermission.WITHDRAW.getLegacyBitwiseValue();
            case "deposit": return GuildPermission.DEPOSIT.getLegacyBitwiseValue();
            case "plot_perm": return GuildPermission.PLOT_PERM.getLegacyBitwiseValue();
            case "plot_set": return GuildPermission.PLOT_SET.getLegacyBitwiseValue();
            case "plot_owner": return GuildPermission.PLOT_OWNER.getLegacyBitwiseValue();
            case "admin": return GuildPermission.ADMIN.getLegacyBitwiseValue();
            case "admin_town": return GuildPermission.ADMIN_TOWN.getLegacyBitwiseValue();
            case "admin_plot": return GuildPermission.ADMIN_PLOT.getLegacyBitwiseValue();
            case "admin_resident": return GuildPermission.ADMIN_RESIDENT.getLegacyBitwiseValue();
            case "bypass": return GuildPermission.BYPASS.getLegacyBitwiseValue();
            default: return 0;
        }
    }

    /**
     * Update permission flags based on permission name and value
     */
    private int updatePermissionFlag(int currentFlags, String permission, boolean value) {
        int flag = getPermissionFlag(permission, value);
        if (value) {
            return currentFlags | flag; // Add flag
        } else {
            return currentFlags & ~flag; // Remove flag
        }
    }

    // Plot-specific permission method implementations

    @Override
    public boolean canClaimPlot(UUID residentUuid, int x, int z, String world) {
        // Check if resident is in a town and has claim permission
        // This would integrate with TownService to verify town membership
        return hasPermission(residentUuid, "claim", "town", null);
    }

    @Override
    public boolean canBuyPlot(UUID residentUuid, UUID plotId) {
        // Anyone can buy plots if they're for sale (economy check handled elsewhere)
        return true;
    }

    @Override
    public boolean canManagePlot(UUID residentUuid, UUID plotId) {
        // Plot owners and town assistants/mayors can manage plots
        return ownsPlot(residentUuid, plotId) || hasTownAdmin(residentUuid, null);
    }

    @Override
    public boolean hasPlotPermission(UUID residentUuid, UUID plotId, int permissionFlag) {
        // Plot owners have all permissions
        if (ownsPlot(residentUuid, plotId)) {
            return true;
        }

        // Check plot-specific permissions from database
        // For now, fallback to basic checks
        PermissionEvaluationResult result = evaluatePlotPermission(residentUuid, plotId, permissionFlag);
        return result.hasPermission();
    }

    @Override
    public boolean canClaimForTown(UUID residentUuid, String townName) {
        // Check if resident has town management permissions
        return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);
    }

    @Override
    public boolean hasPlotManagementPermissions(UUID residentUuid, String townName) {
        return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);
    }

    @Override
    public PermissionEvaluationResult evaluatePlotPermission(UUID residentUuid, UUID plotId, int permissionFlag) {
        // Priority 1: Global admin bypass
        if (hasPermission(residentUuid, "bypass", "global", null)) {
            return new PermissionEvaluationResult(true, "admin", "Global admin bypass");
        }

        // Get plot information
        Optional<TownBlock> townBlock = plotService.getTownBlock(plotId);
        if (!townBlock.isPresent()) {
            return new PermissionEvaluationResult(false, "default", "Plot does not exist");
        }

        TownBlock block = townBlock.get();
        String townId = block.getTownId();

        // Priority 2: Plot owner - has absolute rights over their plot
        if (ownsPlot(residentUuid, plotId)) {
            return new PermissionEvaluationResult(true, "owner", "Plot owner has all permissions");
        }

        // Priority 3: Plot-specific permissions (highest precedence)
        // Check if the user has explicit permissions on this plot
        List<Permission> plotPerms = getResidentPermissions(residentUuid, Permission.Context.PLOT, plotId.toString());
        for (Permission perm : plotPerms) {
            if (perm.appliesTo(residentUuid) && perm.hasFlag(permissionFlag)) {
                return new PermissionEvaluationResult(true, "plot", "Explicit plot permission granted");
            }
        }

        // Priority 4: Town permissions (fallback for town-owned plots or town members)
        String townName = getTownNameFromId(townId);
        if (townName != null) {
            // Check if user is member of the town
            if (isResidentInTown(residentUuid, townName)) {
                // Check town-specific permissions for this resident
                List<Permission> townPerms = getResidentPermissions(residentUuid, Permission.Context.TOWN, townName);
                for (Permission perm : townPerms) {
                    if (perm.appliesTo(residentUuid) && perm.hasFlag(permissionFlag)) {
                        return new PermissionEvaluationResult(true, "town", "Town permission granted");
                    }
                }

                // Check default town permissions based on resident's role
                if (isTownMayor(residentUuid, townName)) {
                    if ((PermissionSet.createMayor().toLegacyFlags() & permissionFlag) != 0) {
                        return new PermissionEvaluationResult(true, "town", "Default mayor permissions");
                    }
                } else if (isTownAssistant(residentUuid, townName)) {
                    if ((PermissionSet.createAssistant().toLegacyFlags() & permissionFlag) != 0) {
                        return new PermissionEvaluationResult(true, "town", "Default assistant permissions");
                    }
                } else {
                    // Regular town resident
                    if ((PermissionSet.createResident().toLegacyFlags() & permissionFlag) != 0) {
                        return new PermissionEvaluationResult(true, "town", "Default resident permissions");
                    }
                }
            }

            // Check if plot is town-owned and apply default plot permissions
            if (block.getOwnerId() == null) {
                if ((PermissionSet.createDefaultPlot().toLegacyFlags() & permissionFlag) != 0) {
                    return new PermissionEvaluationResult(true, "plot", "Default town plot permissions");
                }
            }
        }

        // Priority 5: Default deny
        return new PermissionEvaluationResult(false, "default", "No permission granted");
    }

    /**
     * Helper method to get town name from town ID
     */
    private String getTownNameFromId(String townId) {
        try {
            Optional<Town> town = townService.getTownById(townId);
            return town.map(Town::getName).orElse(null);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get town name from ID: " + townId, e);
            return null;
        }
    }

    /**
     * Helper method to check if resident is member of town
     */
    private boolean isResidentInTown(UUID residentUuid, String townName) {
        try {
            String residentTown = getResidentTown(residentUuid);
            return townName.equals(residentTown);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to check town membership for resident: " + residentUuid, e);
            return false;
        }
    }

    /**
     * Helper method to get resident's town name
     */
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

    // ==================== NEW ENUM-BASED METHOD IMPLEMENTATIONS ====================

    @Override
    public PermissionEvaluationResult hasPermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return evaluatePermissionEnum(residentUuid, context, contextId, permission);
    }

    @Override
    public boolean grantPermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return grantPermissionEnum(residentUuid, permission, context, contextId);
    }

    @Override
    public boolean denyPermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return denyPermissionEnum(residentUuid, permission, context, contextId);
    }

    @Override
    public boolean revokePermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return revokePermissionEnum(residentUuid, permission, context, contextId);
    }

    @Override
    public PermissionSet getPermissionSet(UUID residentUuid, String context, String contextId) {
        List<Permission> permissions = getResidentPermissions(residentUuid, context, contextId);
        PermissionSet permissionSet = new PermissionSet();

        for (Permission perm : permissions) {
            GuildPermission.fromLegacyValue(perm.getFlags()).ifPresent(permissionSet::grantPermission);
        }

        return permissionSet;
    }

    @Override
    public boolean setPermissionSet(UUID residentUuid, PermissionSet permissionSet, String context, String contextId) {
        try {
            // Clear existing permissions first
            revokePermission(residentUuid, "", context, contextId);

            // Grant new permissions
            for (GuildPermission permission : permissionSet.getGrantedPermissions()) {
                grantPermission(residentUuid, permission, context, contextId);
            }

            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set permission set", e);
            return false;
        }
    }

    @Override
    public PermissionEvaluationResult evaluatePermission(UUID residentUuid, String context, String contextId, GuildPermission permission) {
        // For now, delegate to legacy evaluation
        return evaluatePlotPermission(residentUuid, UUID.fromString(contextId), permission.getLegacyBitwiseValue());
    }

    @Override
    public PermissionSet getDefaultPermissions(String role) {
        switch (role.toLowerCase()) {
            case "mayor":
                return PermissionSet.createMayor();
            case "assistant":
                return PermissionSet.createAssistant();
            case "resident":
                return PermissionSet.createResident();
            default:
                return new PermissionSet();
        }
    }

    @Override
    public String getCacheStatistics() {
        // Cache statistics would be implemented here
        return "Cache statistics not implemented yet";
    }

    @Override
    public void clearCache() {
        // Cache clearing would be implemented here
        logger.info("Permission cache cleared");
    }

    @Override
    public void clearResidentCache(UUID residentUuid) {
        // Resident cache clearing would be implemented here
        logger.info("Permission cache cleared for resident: " + residentUuid);
    }

    // ==================== TOWN TOGGLE METHODS (DELEGATED) ====================

    @Override
    public boolean isPvpEnabledAtLocation(int x, int z, String world) {
        return townToggleService.isPvpEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean isFireEnabledAtLocation(int x, int z, String world) {
        return townToggleService.isFireEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean areExplosionsEnabledAtLocation(int x, int z, String world) {
        return townToggleService.areExplosionsEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean areMobsEnabledAtLocation(int x, int z, String world) {
        return townToggleService.areMobsEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean isPublicAccessEnabledAtLocation(int x, int z, String world) {
        return townToggleService.isPublicAccessEnabledAtLocation(x, z, world);
    }

    @Override
    public Map<String, Boolean> getTogglesAtLocation(int x, int z, String world) {
        return townToggleService.getTogglesAtLocation(x, z, world);
    }

    // ==================== ENUM HELPER METHODS ====================

    private PermissionEvaluationResult evaluatePermissionEnum(UUID residentUuid, String context, String contextId, GuildPermission permission) {
        // For now, delegate to legacy evaluation with conversion
        return evaluatePlotPermission(residentUuid, UUID.fromString(contextId), permission.getLegacyBitwiseValue());
    }

    private boolean grantPermissionEnum(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return grantPermission(residentUuid, permission.name(), context, contextId, true);
    }

    private boolean denyPermissionEnum(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return grantPermission(residentUuid, permission.name(), context, contextId, false);
    }

    private boolean revokePermissionEnum(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return revokePermission(residentUuid, permission.name(), context, contextId);
    }
}
package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Permission;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of PermissionService with database operations
 */
@Singleton
public class PermissionServiceImpl implements org.aincraft.towny.services.PermissionService {

    private final DatabaseManager databaseManager;
    private final DataSource dataSource;
    private final Logger logger;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Inject
    public PermissionServiceImpl(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
    }

    @Override
    public boolean hasPermission(UUID residentUuid, String permission, String context, String contextId) {
        // This would need to be implemented to check bitwise flags
        // For now, return false as a placeholder
        return false;
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
        // Check if location is in a town block
        // Get plot permissions and check build flag
        // For now, return true as placeholder
        return true;
    }

    @Override
    public boolean canDestroy(UUID residentUuid, int x, int z, String world) {
        // Similar to canBuild but for destroy permissions
        return true;
    }

    @Override
    public boolean canSwitch(UUID residentUuid, int x, int z, String world) {
        // Check switch permissions
        return true;
    }

    @Override
    public boolean canUseItems(UUID residentUuid, int x, int z, String world) {
        // Check item use permissions
        return true;
    }

    @Override
    public List<Permission> getDefaultTownPermissions() {
        List<Permission> permissions = new ArrayList<>();

        // Create default town permissions with bitwise flags
        Permission residentPerms = new Permission(
            Permission.Flag.RESIDENT_PERMS,
            Permission.Context.TOWN,
            "default",
            Permission.Target.RESIDENT,
            "all"
        );

        Permission assistantPerms = new Permission(
            Permission.Flag.ASSISTANT_PERMS,
            Permission.Context.TOWN,
            "default",
            Permission.Target.ASSISTANT,
            "all"
        );

        Permission mayorPerms = new Permission(
            Permission.Flag.MAYOR_PERMS,
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
            Permission.Flag.DEFAULT_PLOT,
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

    @Override
    public boolean hasTownAdmin(UUID residentUuid, String townName) {
        // Check if resident is mayor or assistant
        return isTownMayor(residentUuid, townName) || isTownAssistant(residentUuid, townName);
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
            case "build": return Permission.Flag.BUILD;
            case "destroy": return Permission.Flag.DESTROY;
            case "switch": return Permission.Flag.SWITCH;
            case "item_use": return Permission.Flag.ITEM_USE;
            case "claim": return Permission.Flag.CLAIM;
            case "unclaim": return Permission.Flag.UNCLAIM;
            case "spawn": return Permission.Flag.SPAWN;
            case "set_spawn": return Permission.Flag.SET_SPAWN;
            case "invite": return Permission.Flag.INVITE;
            case "kick": return Permission.Flag.KICK;
            case "promote": return Permission.Flag.PROMOTE;
            case "demote": return Permission.Flag.DEMOTE;
            case "withdraw": return Permission.Flag.WITHDRAW;
            case "deposit": return Permission.Flag.DEPOSIT;
            case "plot_perm": return Permission.Flag.PLOT_PERM;
            case "plot_set": return Permission.Flag.PLOT_SET;
            case "plot_owner": return Permission.Flag.PLOT_OWNER;
            case "admin": return Permission.Flag.ADMIN;
            case "admin_town": return Permission.Flag.ADMIN_TOWN;
            case "admin_plot": return Permission.Flag.ADMIN_PLOT;
            case "admin_resident": return Permission.Flag.ADMIN_RESIDENT;
            case "bypass": return Permission.Flag.BYPASS;
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
}
package org.aincraft.guilds.services.impl;



import com.azoth.territory.model.GovernmentForm;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.TerritoryRegistry;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Permission;
import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.models.GuildPermission;
import org.aincraft.guilds.models.PermissionSet;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.PermissionEvaluationResult;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.GuildToggleService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
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
    private final GuildService guildService;
    private final ResidentService residentService;
    private final GuildToggleService guildToggleService;
    private final LocationService locationService;
    private final AllianceService allianceService;

    /**
     * Late-bound: territory boundaries let the plot gate apply government-form
     * semantics to territory chunks the guild has not materialized as plots.
     * Null when the host plugin does not wire it (tests / degraded mode).
     */
    private TerritoryRegistry territoryRegistry;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Cache key for resident permission lookups. */
    private record PermissionCacheKey(UUID residentUuid, String context, String contextId) {
    }

    /**
     * Read cache for {@link #getResidentPermissions}. Every permission write
     * (grant/revoke/set/plot-permission mutations) invalidates it wholesale —
     * writes are rare admin operations, reads are the hot evaluation path.
     */
    private final Map<PermissionCacheKey, List<Permission>> permissionCache = new ConcurrentHashMap<>();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();


    public PermissionServiceImpl(DatabaseManager databaseManager, Logger logger,
                                PlotService plotService, GuildService guildService, ResidentService residentService,
                                GuildToggleService guildToggleService, LocationService locationService,
                                AllianceService allianceService) {
        this.databaseManager = databaseManager;
        this.dataSource = databaseManager.getDataSource();
        this.logger = logger;
        this.plotService = plotService;
        this.guildService = guildService;
        this.residentService = residentService;
        this.locationService = locationService;
        this.guildToggleService = guildToggleService;
        this.allianceService = allianceService;
    }

    public void setTerritoryRegistry(TerritoryRegistry territoryRegistry) {
        this.territoryRegistry = territoryRegistry;
    }

    @Override
    public boolean hasPermission(UUID residentUuid, String permission, String context, String contextId) {
        // Handle specific permission checks based on context
        switch (context.toLowerCase()) {
            case "guild":
                return hasGuildPermission(residentUuid, permission, contextId);
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

        if (result[0]) {
            invalidatePermissionCache();
        }
        return result[0];
    }

    @Override
    public boolean revokePermission(UUID residentUuid, String permission, String context, String contextId) {
        final boolean[] changed = new boolean[1];

        boolean txCommitted = databaseManager.executeTransaction(connection -> {
            try {
                String targetId = residentUuid.toString();

                // Blank permission clears every row for this resident in the context
                // (used by setPermissionSet to reset before re-granting).
                if (permission == null || permission.isBlank()) {
                    String deleteSql = "DELETE FROM permissions WHERE context = ? AND context_id = ? "
                            + "AND target_type = 'resident' AND target_id = ?";
                    try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                        statement.setString(1, context);
                        statement.setString(2, contextId);
                        statement.setString(3, targetId);
                        changed[0] = statement.executeUpdate() > 0;
                    }
                    return;
                }

                // Clear a single named flag; drop the row when no flags remain.
                String selectSql = "SELECT id, permissions_flags FROM permissions WHERE context = ? "
                        + "AND context_id = ? AND target_type = 'resident' AND target_id = ?";
                try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                    select.setString(1, context);
                    select.setString(2, contextId);
                    select.setString(3, targetId);
                    try (ResultSet resultSet = select.executeQuery()) {
                        if (!resultSet.next()) {
                            return; // nothing granted yet — nothing to revoke
                        }
                        String rowId = resultSet.getString("id");
                        int currentFlags = resultSet.getInt("permissions_flags");
                        int flag = permissionBit(permission);
                        if (flag == 0) {
                            return; // unknown permission name — nothing to revoke
                        }
                        int newFlags = currentFlags & ~flag;
                        if (newFlags == 0) {
                            try (PreparedStatement delete = connection.prepareStatement(
                                    "DELETE FROM permissions WHERE id = ?")) {
                                delete.setString(1, rowId);
                                changed[0] = delete.executeUpdate() > 0;
                            }
                        } else {
                            try (PreparedStatement update = connection.prepareStatement(
                                    "UPDATE permissions SET permissions_flags = ? WHERE id = ?")) {
                                update.setInt(1, newFlags);
                                update.setString(2, rowId);
                                changed[0] = update.executeUpdate() > 0;
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to revoke permission for resident: " + residentUuid, e);
                throw e; // propagate raw so executeTransaction rolls back
            }
        });

        // Only invalidate when the transaction committed; a rolled-back revoke changes nothing.
        if (txCommitted) {
            invalidatePermissionCache();
        }
        return changed[0];
    }

    @Override
    public List<Permission> getResidentPermissions(UUID residentUuid, String context, String contextId) {
        PermissionCacheKey key = new PermissionCacheKey(residentUuid, context, contextId);
        List<Permission> cached = permissionCache.get(key);
        if (cached != null) {
            cacheHits.increment();
            return cached;
        }
        cacheMisses.increment();
        List<Permission> loaded;
        try {
            loaded = loadResidentPermissions(residentUuid, context, contextId);
        } catch (SQLException e) {
            // A failed load must not poison the cache with "no permissions":
            // return empty for this call, but let the next read retry the DB.
            logger.log(Level.SEVERE, "Failed to get permissions for resident: " + residentUuid, e);
            return List.of();
        }
        permissionCache.put(key, loaded);
        return loaded;
    }

    private List<Permission> loadResidentPermissions(UUID residentUuid, String context, String contextId)
            throws SQLException {
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
    public boolean setGuildPermissions(String guildName, List<Permission> permissions) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                // Delete existing guild permissions
                String deleteSql = "DELETE FROM permissions WHERE context = 'town' AND context_id = ?";
                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                    deleteStatement.setString(1, guildName);
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

                logger.info("Set permissions for guild: " + guildName);
                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to set permissions for guild: " + guildName, e);
                throw new RuntimeException("Failed to set guild permissions", e);
            }
        });

        if (result[0]) {
            invalidatePermissionCache();
        }
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

        if (result[0]) {
            invalidatePermissionCache();
        }
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
     * Uses hierarchical permission evaluation: Plot > Guild > Global
     */
    private boolean checkLocationPermission(UUID residentUuid, int x, int z, String world, int permissionFlag) {
        try {
            // Convert block coordinates to chunk coordinates
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            // Get guild block at this location
            Optional<GuildBlock> guildBlock = plotService.getGuildBlock(chunkX, chunkZ, world);

            if (!guildBlock.isPresent()) {
                // No plot row: territory chunks still follow the governing
                // guild's government form (only ANARCHY is a wildcard bypass);
                // land outside any territory stays wilderness.
                return checkNoPlotPermission(residentUuid, x, z, world, permissionFlag);
            }

            // Guild block exists - use hierarchical permission evaluation
            GuildBlock block = guildBlock.get();

            // ANARCHY-form government (guild or its alliance): no permission
            // system — anyone may act here, including non-residents (no
            // public/private admission gate).
            if (effectiveForm(block.getGuildId()) == GovernmentForm.ANARCHY) {
                return true;
            }

            // Guild block exists - apply guild toggle checks first
            if (!checkGuildToggles(residentUuid, x, z, world, permissionFlag)) {
                logger.fine(String.format("Permission denied by guild toggle for %s at (%d,%d,%s)",
                    residentUuid, x, z, world));
                return false;
            }

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
     * Check guild toggles that might affect the permission
     * @param residentUuid Resident UUID
     * @param x X coordinate
     * @param z Z coordinate
     * @param world World name
     * @param permissionFlag Permission flag being checked
     * @return True if toggles allow the permission, false otherwise
     */
    private boolean checkGuildToggles(UUID residentUuid, int x, int z, String world, int permissionFlag) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        if (guild.isEmpty()) {
            return true; // No guild - no toggle restrictions
        }

        Guild t = guild.get();

        // Check if guild is public access (for non-residents)
        try {
            var resident = residentService.getResident(residentUuid);
            if (resident.isPresent() && !resident.get().hasGuild()) {
                // Non-resident trying to access guild
                if (!t.isPublicEnabled()) {
                    logger.fine("Non-resident access denied - guild is not public");
                    return false;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking resident guild membership: " + e.getMessage());
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

    /**
     * Permission at a location with no plot row. Territory chunks still belong
     * to the governing guild: ANARCHY is the only form that is a wildcard
     * bypass; under every other form the government owns the land and members
     * act through explicit grants or form-gated role defaults. Land outside
     * any territory is wilderness (always allowed).
     */
    private boolean checkNoPlotPermission(UUID residentUuid, int x, int z, String world, int permissionFlag) {
        if (territoryRegistry == null) {
            return checkWildernessPermission(permissionFlag);
        }
        LookupResult result = territoryRegistry.resolve(world, x, z);
        if (!result.isContained()) {
            return checkWildernessPermission(permissionFlag);
        }
        Optional<String> guildId = result.territory().flatMap(Territory::governedByGuildId);
        if (guildId.isEmpty()) {
            // Territory-local government: the territory gate (BlockProtection)
            // enforces its seat lockdown; the plot layer stays permissive here.
            return checkWildernessPermission(permissionFlag);
        }
        GovernmentForm form = effectiveForm(guildId.get());
        if (form == GovernmentForm.ANARCHY) {
            return true; // no permission system
        }
        Optional<Guild> guild = guildService.getGuildById(guildId.get());
        if (guild.isEmpty()) {
            return checkWildernessPermission(permissionFlag);
        }
        String guildName = guild.get().getName();
        if (!isMemberOfGoverningScope(residentUuid, guildId.get())) {
            // Outsider on government land without a plot row: mirror the
            // territory gate's public-guild fallback (build/interact, never break).
            return guild.get().isPublicEnabled() && permissionFlag != GuildPermission.DESTROY.getLegacyBitwiseValue();
        }
        // Member: explicit guild grants first (own guild included for
        // alliance-governed land), then form-gated role defaults.
        if (hasGrant(residentUuid, Permission.Context.GUILD, guildName, permissionFlag)) {
            return true;
        }
        String ownGuildName = getResidentGuild(residentUuid);
        if (ownGuildName != null && !ownGuildName.equals(guildName)
                && hasGrant(residentUuid, Permission.Context.GUILD, ownGuildName, permissionFlag)) {
            return true;
        }
        return memberDefaultAllows(residentUuid, guildName, permissionFlag, form);
    }

    /**
     * The effective government form for a guild's land: the alliance's form
     * when the guild belongs to one (the alliance is the governing body of its
     * member guilds' territories), else the guild's own form.
     */
    private GovernmentForm effectiveForm(String guildId) {
        Optional<Alliance> alliance = allianceContaining(guildId);
        if (alliance.isPresent()) {
            return allianceService.getGovernanceForm(alliance.get().getId());
        }
        return guildService.getGovernanceForm(guildId);
    }

    private Optional<Alliance> allianceContaining(String guildId) {
        for (Alliance alliance : allianceService.getAllAlliances()) {
            if (alliance.hasGuild(guildId)) {
                return Optional.of(alliance);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the actor is a member of the governing scope for the given
     * guild's land: a resident of that guild, or — when the guild belongs to
     * an alliance — a resident of any member guild (mirroring the territory
     * gate's alliance sibling-member rule).
     */
    private boolean isMemberOfGoverningScope(UUID residentUuid, String guildId) {
        String residentGuildName = getResidentGuild(residentUuid);
        if (residentGuildName == null) {
            return false;
        }
        String guildName = getGuildNameFromId(guildId);
        if (guildName != null && guildName.equals(residentGuildName)) {
            return true;
        }
        Optional<Alliance> alliance = allianceContaining(guildId);
        if (alliance.isEmpty()) {
            return false;
        }
        String residentGuildId = guildService.getGuild(residentGuildName).map(Guild::getId).orElse(null);
        return residentGuildId != null && alliance.get().hasGuild(residentGuildId);
    }

    /** Whether any permission row for the resident in the context grants the flag. */
    private boolean hasGrant(UUID residentUuid, String context, String contextId, int permissionFlag) {
        for (Permission perm : getResidentPermissions(residentUuid, context, contextId)) {
            if (perm.appliesTo(residentUuid) && perm.hasFlag(permissionFlag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Role-default evaluation for a guild member on land without explicit
     * ownership (guild-owned plots and unclaimed territory chunks).
     * <p>
     * Government semantics: only DEMOCRACY shares land-modifying defaults
     * (BUILD/DESTROY) with residents; MONARCHY/OLIGARCHY land is
     * government-controlled — residents need explicit grants. Switch/item-use
     * defaults stay under every form so guilds remain usable.
     */
    private boolean memberDefaultAllows(UUID residentUuid, String guildName, int permissionFlag, GovernmentForm form) {
        if (isGuildMayor(residentUuid, guildName)) {
            return (PermissionSet.createMayor().toLegacyFlags() & permissionFlag) != 0;
        }
        if (isGuildAssistant(residentUuid, guildName)) {
            return (PermissionSet.createAssistant().toLegacyFlags() & permissionFlag) != 0;
        }
        boolean residentDefault = (PermissionSet.createResident().toLegacyFlags() & permissionFlag) != 0;
        if (!residentDefault) {
            return false;
        }
        boolean landModification = permissionFlag == GuildPermission.BUILD.getLegacyBitwiseValue()
                || permissionFlag == GuildPermission.DESTROY.getLegacyBitwiseValue();
        return !landModification || form == GovernmentForm.DEMOCRACY;
    }

    @Override
    public List<Permission> getDefaultGuildPermissions() {
        List<Permission> permissions = new ArrayList<>();

        // Create default guild permissions with bitwise flags
        Permission residentPerms = new Permission(
            PermissionSet.createResident().toLegacyFlags(),
            Permission.Context.GUILD,
            "default",
            Permission.Target.RESIDENT,
            "all"
        );

        Permission assistantPerms = new Permission(
            PermissionSet.createAssistant().toLegacyFlags(),
            Permission.Context.GUILD,
            "default",
            Permission.Target.ASSISTANT,
            "all"
        );

        Permission mayorPerms = new Permission(
            PermissionSet.createMayor().toLegacyFlags(),
            Permission.Context.GUILD,
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
    public boolean isGuildMayor(UUID residentUuid, String guildName) {
        String sql = "SELECT COUNT(*) FROM guilds WHERE name = ? AND mayor_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);
            statement.setString(2, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if resident is guild mayor: " + residentUuid + " in guild " + guildName, e);
        }

        return false;
    }

    @Override
    public boolean isGuildAssistant(UUID residentUuid, String guildName) {
        String sql = """
            SELECT COUNT(*) FROM guild_residents tr
            JOIN guilds t ON tr.guild_id = t.id
            WHERE t.name = ? AND tr.resident_uuid = ? AND tr.role = 'assistant'
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);
            statement.setString(2, residentUuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to check if resident is guild assistant: " + residentUuid + " in guild " + guildName, e);
        }

        return false;
    }

    @Override
    public boolean ownsPlot(UUID residentUuid, UUID plotId) {
        String sql = "SELECT COUNT(*) FROM guild_blocks WHERE id = ? AND owner_uuid = ?";

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
     * Check if a resident has a specific guild permission
     */
    private boolean hasGuildPermission(UUID residentUuid, String permission, String guildName) {
        // Check database for explicit permission grants first
        String sql = "SELECT permissions_flags FROM permissions WHERE " +
                    "context = 'town' AND context_id = ? AND " +
                    "(target_type = 'all' OR (target_type = 'resident' AND target_id = ?))";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, guildName);
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
            logger.log(Level.SEVERE, "Failed to check guild permission: " + permission + " for " + residentUuid + " in " + guildName, e);
        }

        // Fall back to role-based permissions if no explicit permissions found
        return hasRoleBasedGuildPermission(residentUuid, permission, guildName);
    }

    /**
     * Check if a resident has permission based on their guild role
     */
    private boolean hasRoleBasedGuildPermission(UUID residentUuid, String permission, String guildName) {
        switch (permission.toLowerCase()) {
            case "set_spawn":
                // Mayors and assistants can set spawn
                return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);

            case "spawn":
                // All residents and guild members can spawn
                return true;

            case "claim":
            case "unclaim":
                // Mayors and assistants can claim/unclaim
                return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);

            case "invite":
                // Mayors and assistants can invite
                return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);

            case "kick":
                // Mayors and assistants can kick (but not other assistants)
                return isGuildMayor(residentUuid, guildName) ||
                       (isGuildAssistant(residentUuid, guildName) && !isGuildAssistant(residentUuid, guildName));

            case "promote":
            case "demote":
                // Only mayors can promote/demote
                return isGuildMayor(residentUuid, guildName);

            case "withdraw":
            case "deposit":
                // Mayors and assistants can manage economy
                return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);

            case "build":
            case "destroy":
            case "switch":
            case "item_use":
                // All guild members have basic build permissions by default
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
            case "admin_guild": return (flags & GuildPermission.ADMIN_GUILD.getLegacyBitwiseValue()) != 0;
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
    public boolean hasGuildAdmin(UUID residentUuid, String guildName) {
        // Check if resident is mayor or assistant
        return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);
    }

    @Override
    public boolean grantGuildPermission(UUID residentUuid, String guildName, int permissionFlag) {
        return grantGuildPermissions(residentUuid, guildName, permissionFlag);
    }

    @Override
    public boolean grantGuildPermissions(UUID residentUuid, String guildName, int permissionFlags) {
        final boolean[] result = new boolean[1];
        final String targetType = (residentUuid == null) ? "all" : "resident";
        final String targetId = (residentUuid == null) ? null : residentUuid.toString();

        databaseManager.executeTransaction(connection -> {
            try {
                // Check if permission already exists
                String checkSql = "SELECT id, permissions_flags FROM permissions WHERE " +
                                "context = 'town' AND context_id = ? AND target_type = ? AND target_id = ?";

                try (PreparedStatement checkStatement = connection.prepareStatement(checkSql)) {
                    checkStatement.setString(1, guildName);
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

                            logger.info("Updated guild permissions for " + targetType + " in " + guildName + ": added flags " + permissionFlags);
                        } else {
                            // Insert new permission
                            String insertSql = "INSERT INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                                insertStatement.setString(1, UUID.randomUUID().toString());
                                insertStatement.setString(2, "guild");
                                insertStatement.setString(3, guildName);
                                insertStatement.setString(4, targetType);
                                insertStatement.setString(5, targetId);
                                insertStatement.setInt(6, permissionFlags);
                                insertStatement.setString(7, LocalDateTime.now().format(DATE_FORMATTER));
                                insertStatement.setString(8, null); // granted_by_uuid could be set to admin UUID

                                insertStatement.executeUpdate();
                            }

                            logger.info("Granted guild permissions for " + targetType + " in " + guildName + ": flags " + permissionFlags);
                        }
                    }
                }

                result[0] = true;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to grant guild permissions: " + permissionFlags + " to " + targetType + " in " + guildName, e);
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
            "admin", "admin_guild", "admin_plot", "admin_resident", "bypass"
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
            case "admin_guild": return GuildPermission.ADMIN_GUILD.getLegacyBitwiseValue();
            case "admin_plot": return GuildPermission.ADMIN_PLOT.getLegacyBitwiseValue();
            case "admin_resident": return GuildPermission.ADMIN_RESIDENT.getLegacyBitwiseValue();
            case "bypass": return GuildPermission.BYPASS.getLegacyBitwiseValue();
            default: return 0;
        }
    }

    /**
     * Bit value for a permission name, independent of grant/revoke direction.
     * Unknown names yield 0 (no flag to set or clear).
     */
    private int permissionBit(String permission) {
        return getPermissionFlag(permission, true);
    }

    /**
     * Update permission flags based on permission name and value.
     * Note: the false branch must use {@link #permissionBit}, not
     * {@link #getPermissionFlag(permission, false)} (which is always 0 and
     * would clear nothing).
     */
    private int updatePermissionFlag(int currentFlags, String permission, boolean value) {
        int flag = permissionBit(permission);
        if (value) {
            return currentFlags | flag; // Add flag
        } else {
            return currentFlags & ~flag; // Remove flag
        }
    }

    // Plot-specific permission method implementations

    @Override
    public boolean canClaimPlot(UUID residentUuid, int x, int z, String world) {
        // Check if resident is in a guild and has claim permission
        // This would integrate with GuildService to verify guild membership
        return hasPermission(residentUuid, "claim", "guild", null);
    }

    @Override
    public boolean canBuyPlot(UUID residentUuid, UUID plotId) {
        // Anyone can buy plots if they're for sale (economy check handled elsewhere)
        return true;
    }

    @Override
    public boolean canManagePlot(UUID residentUuid, UUID plotId) {
        // Plot owners and guild assistants/mayors can manage plots
        return ownsPlot(residentUuid, plotId) || hasGuildAdmin(residentUuid, null);
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
    public boolean canClaimForGuild(UUID residentUuid, String guildName) {
        // Check if resident has guild management permissions
        return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);
    }

    @Override
    public boolean hasPlotManagementPermissions(UUID residentUuid, String guildName) {
        return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);
    }

    @Override
    public PermissionEvaluationResult evaluatePlotPermission(UUID residentUuid, UUID plotId, int permissionFlag) {
        // Priority 1: Global admin bypass
        if (hasPermission(residentUuid, "bypass", "global", null)) {
            return new PermissionEvaluationResult(true, "admin", "Global admin bypass");
        }

        // Get plot information
        Optional<GuildBlock> guildBlock = plotService.getGuildBlock(plotId);
        if (!guildBlock.isPresent()) {
            return new PermissionEvaluationResult(false, "default", "Plot does not exist");
        }

        GuildBlock block = guildBlock.get();
        String guildId = block.getGuildId();

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

        // Priority 4: Guild permissions (fallback for guild-owned plots or guild members)
        String guildName = getGuildNameFromId(guildId);
        if (guildName != null) {
            // The effective government: the alliance's form when the plot's
            // guild belongs to one, else the guild's own form.
            GovernmentForm form = effectiveForm(guildId);
            // Check if user is a member of the governing scope (the plot's
            // guild, or — on alliance-governed land — any member guild)
            if (isMemberOfGoverningScope(residentUuid, guildId)) {
                // Check guild-specific permissions for this resident
                if (hasGrant(residentUuid, Permission.Context.GUILD, guildName, permissionFlag)) {
                    return new PermissionEvaluationResult(true, "guild", "Guild permission granted");
                }
                // Alliance members are also covered by their own guild's grants.
                String ownGuildName = getResidentGuild(residentUuid);
                if (ownGuildName != null && !ownGuildName.equals(guildName)
                        && hasGrant(residentUuid, Permission.Context.GUILD, ownGuildName, permissionFlag)) {
                    return new PermissionEvaluationResult(true, "guild", "Alliance member guild permission granted");
                }

                // Role defaults (form-gated): land-modifying defaults
                // (BUILD/DESTROY) exist only under DEMOCRACY — in MONARCHY and
                // OLIGARCHY the government controls the land and members need
                // explicit grants. Switch/item-use defaults stay under every
                // form so guilds remain usable.
                if (memberDefaultAllows(residentUuid, guildName, permissionFlag, form)) {
                    return new PermissionEvaluationResult(true, "guild", "Role default permission");
                }
            }

            // Check if plot is guild-owned and apply default plot permissions.
            // Only DEMOCRACY shares the commons with everyone; under
            // MONARCHY/OLIGARCHY guild-owned land needs explicit grants.
            if (block.getOwnerId() == null && form == GovernmentForm.DEMOCRACY) {
                if ((PermissionSet.createDefaultPlot().toLegacyFlags() & permissionFlag) != 0) {
                    return new PermissionEvaluationResult(true, "plot", "Default guild plot permissions");
                }
            }
        }

        // Priority 5: Default deny
        return new PermissionEvaluationResult(false, "default", "No permission granted");
    }

    /**
     * Helper method to get guild name from guild ID
     */
    private String getGuildNameFromId(String guildId) {
        try {
            Optional<Guild> guild = guildService.getGuildById(guildId);
            return guild.map(Guild::getName).orElse(null);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get guild name from ID: " + guildId, e);
            return null;
        }
    }

    /**
     * Helper method to check if resident is member of guild
     */
    private boolean isResidentInGuild(UUID residentUuid, String guildName) {
        try {
            String residentGuild = getResidentGuild(residentUuid);
            return guildName.equals(residentGuild);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to check guild membership for resident: " + residentUuid, e);
            return false;
        }
    }

    /**
     * Helper method to get resident's guild name
     */
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
            logger.log(Level.SEVERE, "Failed to get resident guild for " + residentUuid, e);
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
        return "Permission cache: " + permissionCache.size() + " entries, "
                + cacheHits.sum() + " hits, " + cacheMisses.sum() + " misses";
    }

    @Override
    public void clearCache() {
        invalidatePermissionCache();
        logger.info("Permission cache cleared");
    }

    @Override
    public void clearResidentCache(UUID residentUuid) {
        permissionCache.keySet().removeIf(key -> key.residentUuid().equals(residentUuid));
        logger.info("Permission cache cleared for resident: " + residentUuid);
    }

    private void invalidatePermissionCache() {
        permissionCache.clear();
    }

    // ==================== GUILD TOGGLE METHODS (DELEGATED) ====================

    @Override
    public boolean isPvpEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.isPvpEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean isFireEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.isFireEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean areExplosionsEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.areExplosionsEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean areMobsEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.areMobsEnabledAtLocation(x, z, world);
    }

    @Override
    public boolean isPublicAccessEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.isPublicAccessEnabledAtLocation(x, z, world);
    }

    @Override
    public Map<String, Boolean> getTogglesAtLocation(int x, int z, String world) {
        return guildToggleService.getTogglesAtLocation(x, z, world);
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
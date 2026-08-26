package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.territory.model.GovernmentForm;
import dev.mintychochip.territory.model.LookupResult;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Permission;
import dev.mintychochip.guilds.models.Alliance;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildBlock;
import dev.mintychochip.guilds.models.GuildPermission;
import dev.mintychochip.guilds.models.PermissionSet;
import dev.mintychochip.guilds.services.AllianceService;
import dev.mintychochip.guilds.services.LocationService;
import dev.mintychochip.guilds.services.PermissionEvaluationResult;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.GuildToggleService;
import dev.mintychochip.guilds.services.PermissionEvaluationResult;
import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.sql.SqlParams;

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

public class PermissionServiceImpl implements dev.mintychochip.guilds.services.PermissionService {

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The data source. */
    private final DataSource dataSource;
    /** The logger. */
    private final Logger logger;
    /** The plot service. */
    private final PlotService plotService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;
    /** The guild toggle service. */
    private final GuildToggleService guildToggleService;
    /** The location service. */
    private final LocationService locationService;
    /** The alliance service. */
    private final AllianceService allianceService;

    /**
     * Late-bound: territory boundaries let the plot gate apply government-form
     * semantics to territory chunks the guild has not materialized as plots.
     * Null when the host plugin does not wire it (tests / degraded mode).
     */
    private TerritoryRegistry territoryRegistry;

    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();
    /** The date formatter constant. */
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
    /** The cache hits. */
    private final LongAdder cacheHits = new LongAdder();
    /** The cache misses. */
    private final LongAdder cacheMisses = new LongAdder();


    /**
     * Creates a new permission service impl instance.
     * @param databaseManager the database manager
     * @param logger the logger
     * @param plotService the plot service
     * @param guildService the guild service
     * @param residentService the resident service
     * @param guildToggleService the guild toggle service
     * @param locationService the location service
     * @param allianceService the alliance service
     */
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

    /**
     * Sets the territory registry.
     * @param territoryRegistry the territory registry
     */
    public void setTerritoryRegistry(TerritoryRegistry territoryRegistry) {
        this.territoryRegistry = territoryRegistry;
    }

    /**
     * Returns whether permission.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
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

    /**
     * Performs the grant permission operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @param value the value
     * @return the result
     */
    @Override
    public boolean grantPermission(UUID residentUuid, String permission, String context, String contextId, boolean value) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                try (PreparedStatement checkStatement = SQL.prepare(connection, "permissions/select-resident-flags.sql", SqlParams.of(
                        "context", context,
                        "context_id", contextId,
                        "target_id", residentUuid.toString()))) {
                    try (ResultSet resultSet = checkStatement.executeQuery()) {
                        if (resultSet.next()) {
                            String permissionId = resultSet.getString("id");
                            int currentFlags = resultSet.getInt("permissions_flags");
                            int newFlags = updatePermissionFlag(currentFlags, permission, value);

                            try (PreparedStatement updateStatement = SQL.prepare(connection, "permissions/update-flags-and-granted-at.sql", Map.of(
                                    "permissions_flags", newFlags,
                                    "granted_at", LocalDateTime.now().format(DATE_FORMATTER),
                                    "id", permissionId))) {
                                updateStatement.executeUpdate();
                            }
                        } else {
                            try (PreparedStatement insertStatement = SQL.prepare(connection, "permissions/insert.sql", SqlParams.of(
                                    "id", UUID.randomUUID().toString(),
                                    "context", context,
                                    "context_id", contextId,
                                    "target_type", "resident",
                                    "target_id", residentUuid.toString(),
                                    "permissions_flags", getPermissionFlag(permission, value),
                                    "granted_at", LocalDateTime.now().format(DATE_FORMATTER)))) {
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

    /**
     * Performs the revoke permission operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    @Override
    public boolean revokePermission(UUID residentUuid, String permission, String context, String contextId) {
        final boolean[] changed = new boolean[1];

        boolean txCommitted = databaseManager.executeTransaction(connection -> {
            try {
                String targetId = residentUuid.toString();

                // Blank permission clears every row for this resident in the context
                // (used by setPermissionSet to reset before re-granting).
                if (permission == null || permission.isBlank()) {
                    try (PreparedStatement statement = SQL.prepare(connection, "permissions/delete-resident-in-context.sql", SqlParams.of(
                            "context", context,
                            "context_id", contextId,
                            "target_id", targetId))) {
                        changed[0] = statement.executeUpdate() > 0;
                    }
                    return;
                }

                // Clear a single named flag; drop the row when no flags remain.
                try (PreparedStatement select = SQL.prepare(connection, "permissions/select-resident-flags.sql", SqlParams.of(
                        "context", context,
                        "context_id", contextId,
                        "target_id", targetId))) {
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
                            try (PreparedStatement delete = SQL.prepare(connection, "permissions/delete-by-id.sql", Map.of(
                                    "id", rowId))) {
                                changed[0] = delete.executeUpdate() > 0;
                            }
                        } else {
                            try (PreparedStatement update = SQL.prepare(connection, "permissions/update-flags.sql", Map.of(
                                    "permissions_flags", newFlags,
                                    "id", rowId))) {
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

    /**
     * Returns the resident permissions.
     * @param residentUuid the resident uuid
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
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

    /**
     * Loads the resident permissions.
     * @param residentUuid the resident uuid
     * @param context the context
     * @param contextId the context id
     * @return the result
     * @throws SQLException if an error occurs
     */
    private List<Permission> loadResidentPermissions(UUID residentUuid, String context, String contextId)
            throws SQLException {
        List<Permission> permissions = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/select-resident-in-context.sql", SqlParams.of(
                     "context", context,
                     "context_id", contextId,
                     "target_id", residentUuid.toString()))) {

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    permissions.add(mapResultSetToPermission(resultSet));
                }
            }
        }

        return permissions;
    }

    /**
     * Returns the context permissions.
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    @Override
    public List<Permission> getContextPermissions(String context, String contextId) {
        List<Permission> permissions = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/select-by-context.sql", SqlParams.of(
                     "context", context,
                     "context_id", contextId))) {

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

    /**
     * Sets the guild permissions.
     * @param guildName the guild name
     * @param permissions the permissions
     * @return the result
     */
    @Override
    public boolean setGuildPermissions(String guildName, List<Permission> permissions) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                try (PreparedStatement deleteStatement = SQL.prepare(connection, "permissions/delete-town-context.sql", Map.of(
                        "context_id", guildName))) {
                    deleteStatement.executeUpdate();
                }

                for (Permission permission : permissions) {
                    try (PreparedStatement insertStatement = SQL.prepare(connection, "permissions/insert.sql", SqlParams.of(
                            "id", permission.getId().toString(),
                            "context", permission.getContext(),
                            "context_id", permission.getContextId(),
                            "target_type", permission.getTargetType(),
                            "target_id", permission.getTargetId(),
                            "permissions_flags", permission.getFlags(),
                            "granted_at", LocalDateTime.now().format(DATE_FORMATTER)))) {
                        insertStatement.executeUpdate();
                    }
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

    /**
     * Sets the plot permissions.
     * @param plotId the plot id
     * @param permissions the permissions
     * @return the result
     */
    @Override
    public boolean setPlotPermissions(UUID plotId, List<Permission> permissions) {
        final boolean[] result = new boolean[1];

        databaseManager.executeTransaction(connection -> {
            try {
                try (PreparedStatement deleteStatement = SQL.prepare(connection, "permissions/delete-plot-context.sql", Map.of(
                        "context_id", plotId.toString()))) {
                    deleteStatement.executeUpdate();
                }

                for (Permission permission : permissions) {
                    try (PreparedStatement insertStatement = SQL.prepare(connection, "permissions/insert.sql", SqlParams.of(
                            "id", permission.getId().toString(),
                            "context", permission.getContext(),
                            "context_id", permission.getContextId(),
                            "target_type", permission.getTargetType(),
                            "target_id", permission.getTargetId(),
                            "permissions_flags", permission.getFlags(),
                            "granted_at", LocalDateTime.now().format(DATE_FORMATTER)))) {
                        insertStatement.executeUpdate();
                    }
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

    /**
     * Returns whether build.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean canBuild(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.BUILD.getLegacyBitwiseValue());
    }

    /**
     * Returns whether destroy.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean canDestroy(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.DESTROY.getLegacyBitwiseValue());
    }

    /**
     * Returns whether switch.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean canSwitch(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.SWITCH.getLegacyBitwiseValue());
    }

    /**
     * Returns whether use items.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean canUseItems(UUID residentUuid, int x, int z, String world) {
        return checkLocationPermission(residentUuid, x, z, world, GuildPermission.ITEM_USE.getLegacyBitwiseValue());
    }

    /**
     * Returns whether interact with entity.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
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

    /**
     * Performs the alliance containing operation.
     * @param guildId the guild id
     * @return the result
     */
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

    /**
     * Returns the default guild permissions.
     * @return the result
     */
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

    /**
     * Returns the default plot permissions.
     * @return the result
     */
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

    /**
     * Returns whether guild mayor.
     * @param residentUuid the resident uuid
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public boolean isGuildMayor(UUID residentUuid, String guildName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/count-guild-mayor.sql", SqlParams.of(
                     "name", guildName,
                     "mayor_uuid", residentUuid.toString()))) {

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

    /**
     * Returns whether guild assistant.
     * @param residentUuid the resident uuid
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public boolean isGuildAssistant(UUID residentUuid, String guildName) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/count-guild-assistant.sql", SqlParams.of(
                     "name", guildName,
                     "resident_uuid", residentUuid.toString()))) {

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

    /**
     * Performs the owns plot operation.
     * @param residentUuid the resident uuid
     * @param plotId the plot id
     * @return the result
     */
    @Override
    public boolean ownsPlot(UUID residentUuid, UUID plotId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/count-plot-owner.sql", Map.of(
                     "id", plotId.toString(),
                     "owner_uuid", residentUuid.toString()))) {

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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/select-town-flags-for-resident.sql", SqlParams.of(
                     "context_id", guildName,
                     "target_id", residentUuid.toString()))) {

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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/select-global-flags.sql", Map.of(
                     "target_id", residentUuid.toString()))) {

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

    /**
     * Returns whether guild admin.
     * @param residentUuid the resident uuid
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public boolean hasGuildAdmin(UUID residentUuid, String guildName) {
        // Check if resident is mayor or assistant
        return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);
    }

    /**
     * Performs the grant guild permission operation.
     * @param residentUuid the resident uuid
     * @param guildName the guild name
     * @param permissionFlag the permission flag
     * @return the result
     */
    @Override
    public boolean grantGuildPermission(UUID residentUuid, String guildName, int permissionFlag) {
        return grantGuildPermissions(residentUuid, guildName, permissionFlag);
    }

    /**
     * Performs the grant guild permissions operation.
     * @param residentUuid the resident uuid
     * @param guildName the guild name
     * @param permissionFlags the permission flags
     * @return the result
     */
    @Override
    public boolean grantGuildPermissions(UUID residentUuid, String guildName, int permissionFlags) {
        final boolean[] result = new boolean[1];
        final String targetType = (residentUuid == null) ? "all" : "resident";
        final String targetId = (residentUuid == null) ? null : residentUuid.toString();

        databaseManager.executeTransaction(connection -> {
            try {
                try (PreparedStatement checkStatement = SQL.prepare(connection, "permissions/select-town-by-target.sql", SqlParams.of(
                        "context_id", guildName,
                        "target_type", targetType,
                        "target_id", targetId))) {
                    try (ResultSet resultSet = checkStatement.executeQuery()) {
                        if (resultSet.next()) {
                            String existingId = resultSet.getString("id");
                            int existingFlags = resultSet.getInt("permissions_flags");
                            int newFlags = existingFlags | permissionFlags;

                            try (PreparedStatement updateStatement = SQL.prepare(connection, "permissions/update-flags.sql", Map.of(
                                    "permissions_flags", newFlags,
                                    "id", existingId))) {
                                updateStatement.executeUpdate();
                            }

                            logger.info("Updated guild permissions for " + targetType + " in " + guildName + ": added flags " + permissionFlags);
                        } else {
                            try (PreparedStatement insertStatement = SQL.prepare(connection, "permissions/insert-with-granted-by.sql", SqlParams.of(
                                    "id", UUID.randomUUID().toString(),
                                    "context", "guild",
                                    "context_id", guildName,
                                    "target_type", targetType,
                                    "target_id", targetId,
                                    "permissions_flags", permissionFlags,
                                    "granted_at", LocalDateTime.now().format(DATE_FORMATTER),
                                    "granted_by_uuid", null))) {
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

    /**
     * Returns the all permission nodes.
     * @return the result
     */
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

    /**
     * Returns whether claim plot.
     * @param residentUuid the resident uuid
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean canClaimPlot(UUID residentUuid, int x, int z, String world) {
        // Check if resident is in a guild and has claim permission
        // This would integrate with GuildService to verify guild membership
        return hasPermission(residentUuid, "claim", "guild", null);
    }

    /**
     * Returns whether buy plot.
     * @param residentUuid the resident uuid
     * @param plotId the plot id
     * @return the result
     */
    @Override
    public boolean canBuyPlot(UUID residentUuid, UUID plotId) {
        // Anyone can buy plots if they're for sale (economy check handled elsewhere)
        return true;
    }

    /**
     * Returns whether manage plot.
     * @param residentUuid the resident uuid
     * @param plotId the plot id
     * @return the result
     */
    @Override
    public boolean canManagePlot(UUID residentUuid, UUID plotId) {
        // Plot owners and guild assistants/mayors can manage plots
        return ownsPlot(residentUuid, plotId) || hasGuildAdmin(residentUuid, null);
    }

    /**
     * Returns whether plot permission.
     * @param residentUuid the resident uuid
     * @param plotId the plot id
     * @param permissionFlag the permission flag
     * @return the result
     */
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

    /**
     * Returns whether claim for guild.
     * @param residentUuid the resident uuid
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public boolean canClaimForGuild(UUID residentUuid, String guildName) {
        // Check if resident has guild management permissions
        return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);
    }

    /**
     * Returns whether plot management permissions.
     * @param residentUuid the resident uuid
     * @param guildName the guild name
     * @return the result
     */
    @Override
    public boolean hasPlotManagementPermissions(UUID residentUuid, String guildName) {
        return isGuildMayor(residentUuid, guildName) || isGuildAssistant(residentUuid, guildName);
    }

    /**
     * Performs the evaluate plot permission operation.
     * @param residentUuid the resident uuid
     * @param plotId the plot id
     * @param permissionFlag the permission flag
     * @return the result
     */
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = SQL.prepare(connection, "permissions/select-resident-guild.sql", Map.of(
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

    // ==================== NEW ENUM-BASED METHOD IMPLEMENTATIONS ====================

    /**
     * Returns whether permission.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    @Override
    public PermissionEvaluationResult hasPermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return evaluatePermissionEnum(residentUuid, context, contextId, permission);
    }

    /**
     * Performs the grant permission operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    @Override
    public boolean grantPermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return grantPermissionEnum(residentUuid, permission, context, contextId);
    }

    /**
     * Performs the deny permission operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    @Override
    public boolean denyPermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return denyPermissionEnum(residentUuid, permission, context, contextId);
    }

    /**
     * Performs the revoke permission operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    @Override
    public boolean revokePermission(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return revokePermissionEnum(residentUuid, permission, context, contextId);
    }

    /**
     * Returns the permission set.
     * @param residentUuid the resident uuid
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    @Override
    public PermissionSet getPermissionSet(UUID residentUuid, String context, String contextId) {
        List<Permission> permissions = getResidentPermissions(residentUuid, context, contextId);
        PermissionSet permissionSet = new PermissionSet();

        for (Permission perm : permissions) {
            GuildPermission.fromLegacyValue(perm.getFlags()).ifPresent(permissionSet::grantPermission);
        }

        return permissionSet;
    }

    /**
     * Sets the permission set.
     * @param residentUuid the resident uuid
     * @param permissionSet the permission set
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
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

    /**
     * Performs the evaluate permission operation.
     * @param residentUuid the resident uuid
     * @param context the context
     * @param contextId the context id
     * @param permission the permission
     * @return the result
     */
    @Override
    public PermissionEvaluationResult evaluatePermission(UUID residentUuid, String context, String contextId, GuildPermission permission) {
        // For now, delegate to legacy evaluation
        return evaluatePlotPermission(residentUuid, UUID.fromString(contextId), permission.getLegacyBitwiseValue());
    }

    /**
     * Returns the default permissions.
     * @param role the role
     * @return the result
     */
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

    /**
     * Returns the cache statistics.
     * @return the result
     */
    @Override
    public String getCacheStatistics() {
        return "Permission cache: " + permissionCache.size() + " entries, "
                + cacheHits.sum() + " hits, " + cacheMisses.sum() + " misses";
    }

    /** Performs the clear cache operation. */
    @Override
    public void clearCache() {
        invalidatePermissionCache();
        logger.info("Permission cache cleared");
    }

    /**
     * Performs the clear resident cache operation.
     * @param residentUuid the resident uuid
     */
    @Override
    public void clearResidentCache(UUID residentUuid) {
        permissionCache.keySet().removeIf(key -> key.residentUuid().equals(residentUuid));
        logger.info("Permission cache cleared for resident: " + residentUuid);
    }

    /** Performs the invalidate permission cache operation. */
    private void invalidatePermissionCache() {
        permissionCache.clear();
    }

    // ==================== GUILD TOGGLE METHODS (DELEGATED) ====================

    /**
     * Returns whether pvp enabled at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean isPvpEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.isPvpEnabledAtLocation(x, z, world);
    }

    /**
     * Returns whether fire enabled at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean isFireEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.isFireEnabledAtLocation(x, z, world);
    }

    /**
     * Performs the are explosions enabled at location operation.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean areExplosionsEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.areExplosionsEnabledAtLocation(x, z, world);
    }

    /**
     * Performs the are mobs enabled at location operation.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean areMobsEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.areMobsEnabledAtLocation(x, z, world);
    }

    /**
     * Returns whether public access enabled at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean isPublicAccessEnabledAtLocation(int x, int z, String world) {
        return guildToggleService.isPublicAccessEnabledAtLocation(x, z, world);
    }

    /**
     * Returns the toggles at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public Map<String, Boolean> getTogglesAtLocation(int x, int z, String world) {
        return guildToggleService.getTogglesAtLocation(x, z, world);
    }

    // ==================== ENUM HELPER METHODS ====================

    /**
     * Performs the evaluate permission enum operation.
     * @param residentUuid the resident uuid
     * @param context the context
     * @param contextId the context id
     * @param permission the permission
     * @return the result
     */
    private PermissionEvaluationResult evaluatePermissionEnum(UUID residentUuid, String context, String contextId, GuildPermission permission) {
        // For now, delegate to legacy evaluation with conversion
        return evaluatePlotPermission(residentUuid, UUID.fromString(contextId), permission.getLegacyBitwiseValue());
    }

    /**
     * Performs the grant permission enum operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    private boolean grantPermissionEnum(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return grantPermission(residentUuid, permission.name(), context, contextId, true);
    }

    /**
     * Performs the deny permission enum operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    private boolean denyPermissionEnum(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return grantPermission(residentUuid, permission.name(), context, contextId, false);
    }

    /**
     * Performs the revoke permission enum operation.
     * @param residentUuid the resident uuid
     * @param permission the permission
     * @param context the context
     * @param contextId the context id
     * @return the result
     */
    private boolean revokePermissionEnum(UUID residentUuid, GuildPermission permission, String context, String contextId) {
        return revokePermission(residentUuid, permission.name(), context, contextId);
    }
}
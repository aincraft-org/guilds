package org.aincraft.towny.services;

import org.aincraft.towny.cache.SimplePermissionCache;
import org.aincraft.towny.models.TownyPermission;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.PermissionSet;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple permission evaluation engine with caching
 * Maintains the hierarchical permission system: Plot → Town → Global
 */
public class PermissionEvaluationEngine {

    private final PlotService plotService;
    private final TownService townService;
    private final SimplePermissionCache cache;
    private final Logger logger;

    public PermissionEvaluationEngine(PlotService plotService, TownService townService, Logger logger) {
        this.plotService = plotService;
        this.townService = townService;
        this.cache = new SimplePermissionCache();
        this.logger = logger;
    }

    /**
     * Evaluate a permission with full context and caching
     */
    public PermissionEvaluationResult evaluatePermission(
            UUID residentUuid,
            String context,
            String contextId,
            TownyPermission permission
    ) {
        // Check cache first
        String cacheKey = context + ":" + contextId;
        Optional<Boolean> cachedResult = cache.getCachedPermission(residentUuid, cacheKey, permission);
        if (cachedResult.isPresent()) {
            return new PermissionEvaluationResult(cachedResult.get(), "cache", "Cached result");
        }

        boolean hasPermission = evaluatePermissionInternal(residentUuid, context, contextId, permission);

        // Cache the result
        cache.cachePermission(residentUuid, cacheKey, permission, hasPermission);

        String source = determineSource(residentUuid, context, contextId, permission, hasPermission);
        return new PermissionEvaluationResult(hasPermission, source, generateReason(hasPermission, source));
    }

    /**
     * Internal permission evaluation logic
     */
    private boolean evaluatePermissionInternal(UUID residentUuid, String context, String contextId, TownyPermission permission) {
        try {
            // Priority 1: Global admin bypass
            if (hasGlobalAdminPermission(residentUuid, permission)) {
                return true;
            }

            switch (context) {
                case "plot":
                    return evaluatePlotPermission(residentUuid, contextId, permission);
                case "town":
                    return evaluateTownPermission(residentUuid, contextId, permission);
                case "global":
                    return evaluateGlobalPermission(residentUuid, permission);
                default:
                    return false;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error evaluating permission for " + residentUuid, e);
            return false; // Fail safe
        }
    }

    /**
     * Evaluate plot-specific permissions
     */
    private boolean evaluatePlotPermission(UUID residentUuid, String plotId, TownyPermission permission) {
        Optional<TownBlock> townBlock = plotService.getTownBlock(UUID.fromString(plotId));
        if (!townBlock.isPresent()) {
            return false;
        }

        TownBlock block = townBlock.get();

        // Priority 2: Plot owner has absolute rights
        if (ownsPlot(residentUuid, block)) {
            return true;
        }

        // Priority 3: Check plot-specific permissions (not implemented yet, fallback to town)
        // Priority 4: Check town permissions
        String townId = block.getTownId();
        return evaluateTownPermission(residentUuid, townId, permission);
    }

    /**
     * Evaluate town-specific permissions
     */
    private boolean evaluateTownPermission(UUID residentUuid, String townId, TownyPermission permission) {
        Optional<Town> town = townService.getTownById(townId);
        if (!town.isPresent()) {
            return false;
        }

        String townName = town.get().getName();

        // Check if resident is member of the town
        if (!isResidentInTown(residentUuid, townName)) {
            return false;
        }

        // Get role-based default permissions
        if (isTownMayor(residentUuid, townName)) {
            return PermissionSet.createMayor().hasPermission(permission);
        } else if (isTownAssistant(residentUuid, townName)) {
            return PermissionSet.createAssistant().hasPermission(permission);
        } else {
            return PermissionSet.createResident().hasPermission(permission);
        }
    }

    /**
     * Evaluate global permissions
     */
    private boolean evaluateGlobalPermission(UUID residentUuid, TownyPermission permission) {
        // Global permissions would be handled by database lookups
        // For now, only admin permissions are supported at global level
        return hasGlobalAdminPermission(residentUuid, permission);
    }

    /**
     * Check for admin-level permissions
     */
    private boolean hasGlobalAdminPermission(UUID residentUuid, TownyPermission permission) {
        // This would integrate with a global admin system
        // For now, return false for non-bypass permissions
        return permission == TownyPermission.BYPASS;
    }

    /**
     * Check if resident owns the plot
     */
    private boolean ownsPlot(UUID residentUuid, TownBlock block) {
        return block.getOwnerId() != null && block.getOwnerId().equals(residentUuid);
    }

    /**
     * Check if resident is in the specified town
     */
    private boolean isResidentInTown(UUID residentUuid, String townName) {
        // This would need to be implemented in ResidentService
        // For now, assume false (to be implemented when service is available)
        return false;
    }

    /**
     * Check if resident is town mayor
     */
    private boolean isTownMayor(UUID residentUuid, String townName) {
        // This would need to be implemented in TownService
        return false;
    }

    /**
     * Check if resident is town assistant
     */
    private boolean isTownAssistant(UUID residentUuid, String townName) {
        // This would need to be implemented in TownService
        return false;
    }

    /**
     * Determine the source of the permission grant/deny
     */
    private String determineSource(UUID residentUuid, String context, String contextId, TownyPermission permission, boolean hasPermission) {
        if (permission == TownyPermission.BYPASS && hasPermission) {
            return "admin";
        }

        switch (context) {
            case "plot":
                Optional<TownBlock> townBlock = plotService.getTownBlock(UUID.fromString(contextId));
                if (townBlock.isPresent() && ownsPlot(residentUuid, townBlock.get())) {
                    return "owner";
                }
                return "plot";
            case "town":
                return "town";
            case "global":
                return "global";
            default:
                return "default";
        }
    }

    /**
     * Generate a human-readable reason for the permission result
     */
    private String generateReason(boolean hasPermission, String source) {
        switch (source) {
            case "admin":
                return "Global admin bypass granted";
            case "owner":
                return "Plot owner has absolute rights";
            case "plot":
                return hasPermission ? "Plot permission granted" : "Plot permission denied";
            case "town":
                return hasPermission ? "Town permission granted" : "Town permission denied";
            case "global":
                return hasPermission ? "Global permission granted" : "Global permission denied";
            default:
                return hasPermission ? "Permission granted" : "Permission denied";
        }
    }

    /**
     * Simple location-based permission check
     */
    public boolean canBuildAtLocation(UUID residentUuid, int x, int z, String world) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        Optional<TownBlock> townBlock = plotService.getTownBlock(chunkX, chunkZ, world);
        if (!townBlock.isPresent()) {
            // Wilderness - allow building
            return true;
        }

        TownBlock block = townBlock.get();
        PermissionEvaluationResult result = evaluatePermission(
            residentUuid, "plot", block.getId().toString(), TownyPermission.BUILD);

        return result.hasPermission();
    }

    /**
     * Get cache statistics
     */
    public SimplePermissionCache.CacheStats getCacheStats() {
        return cache.getStats();
    }

    /**
     * Clear cache for a resident (call when permissions change)
     */
    public void invalidateResident(UUID residentUuid) {
        cache.invalidateResident(residentUuid);
    }

    /**
     * Clear cache for a context (call when town/plot permissions change)
     */
    public void invalidateContext(String contextKey) {
        cache.invalidateContext(contextKey);
    }

    /**
     * Shutdown the cache
     */
    public void shutdown() {
        cache.shutdown();
    }
}
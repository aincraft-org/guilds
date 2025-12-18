package org.aincraft.subregion;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.service.PermissionService;
import org.aincraft.service.TerritoryService;
import org.bukkit.Location;

/**
 * Service for managing subregions with validation and business logic.
 */
public class SubregionService {
    private static final long DEFAULT_MAX_VOLUME = 100_000;

    private final SubregionRepository subregionRepository;
    private final TerritoryService territoryService;
    private final PermissionService permissionService;
    private final SubregionTypeRegistry typeRegistry;
    private final RegionPermissionService regionPermissionService;
    private final RegionTypeLimitRepository limitRepository;
    private long maxVolume = DEFAULT_MAX_VOLUME;

    @Inject
    public SubregionService(SubregionRepository subregionRepository, TerritoryService territoryService,
                            PermissionService permissionService, SubregionTypeRegistry typeRegistry,
                            RegionPermissionService regionPermissionService, RegionTypeLimitRepository limitRepository) {
        this.subregionRepository = Objects.requireNonNull(subregionRepository);
        this.territoryService = Objects.requireNonNull(territoryService);
        this.permissionService = Objects.requireNonNull(permissionService);
        this.typeRegistry = Objects.requireNonNull(typeRegistry);
        this.regionPermissionService = Objects.requireNonNull(regionPermissionService);
        this.limitRepository = Objects.requireNonNull(limitRepository);
    }

    /**
     * Gets the type registry for external access.
     */
    public SubregionTypeRegistry getTypeRegistry() {
        return typeRegistry;
    }

    /**
     * Gets the limit repository for external access.
     */
    public RegionTypeLimitRepository getLimitRepository() {
        return limitRepository;
    }

    public void setMaxVolume(long maxVolume) {
        this.maxVolume = maxVolume;
    }

    /**
     * Gets the total volume used by a guild for a specific region type.
     */
    public long getTypeUsage(String guildId, String typeId) {
        return subregionRepository.getTotalVolumeByGuildAndType(guildId, typeId);
    }

    /**
     * Creates a new subregion after validating all constraints (without type).
     */
    public SubregionCreationResult createSubregion(String guildId, UUID playerId, String name,
                                                    Location pos1, Location pos2) {
        return createSubregion(guildId, playerId, name, pos1, pos2, null);
    }

    /**
     * Creates a new subregion after validating all constraints.
     *
     * @param type optional type ID (null for untyped regions)
     * @return the created subregion, or empty if validation failed
     */
    public SubregionCreationResult createSubregion(String guildId, UUID playerId, String name,
                                                    Location pos1, Location pos2, String type) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(pos1, "Position 1 cannot be null");
        Objects.requireNonNull(pos2, "Position 2 cannot be null");

        // Check permission
        if (!permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS)) {
            return SubregionCreationResult.failure("You don't have permission to manage regions");
        }

        // Validate type if specified
        if (type != null && !typeRegistry.isRegistered(type)) {
            return SubregionCreationResult.failure("Unknown region type: " + type);
        }

        // Check type volume limit
        if (type != null) {
            Optional<RegionTypeLimit> limitOpt = limitRepository.findByTypeId(type);
            if (limitOpt.isPresent()) {
                RegionTypeLimit limit = limitOpt.get();
                // Create temp to calculate volume
                Subregion temp = Subregion.fromLocations(guildId, name, pos1, pos2, playerId, type);
                long currentUsage = subregionRepository.getTotalVolumeByGuildAndType(guildId, type);
                long newVolume = temp.getVolume();
                if (currentUsage + newVolume > limit.maxTotalVolume()) {
                    return SubregionCreationResult.failure(
                            "Exceeds " + type + " type limit: " + currentUsage + "/" + limit.maxTotalVolume() +
                            " blocks used, need " + newVolume + " more");
                }
            }
        }

        // Check same world
        if (!pos1.getWorld().getName().equals(pos2.getWorld().getName())) {
            return SubregionCreationResult.failure("Positions must be in the same world");
        }

        // Check name uniqueness
        if (subregionRepository.findByGuildAndName(guildId, name).isPresent()) {
            return SubregionCreationResult.failure("A region with that name already exists");
        }

        // Create temp subregion to calculate properties
        Subregion temp = Subregion.fromLocations(guildId, name, pos1, pos2, playerId, type);

        // Check volume limit
        long volume = temp.getVolume();
        if (volume > maxVolume) {
            return SubregionCreationResult.failure(
                    "Region volume (" + volume + " blocks) exceeds maximum (" + maxVolume + " blocks)");
        }

        // Get all intersecting chunks
        Set<ChunkKey> intersectingChunks = getIntersectingChunks(temp);

        // Check all chunks are claimed by this guild
        for (ChunkKey chunk : intersectingChunks) {
            Guild owner = territoryService.getChunkOwner(chunk);
            if (owner == null || !owner.getId().equals(guildId)) {
                return SubregionCreationResult.failure(
                        "All chunks in the region must be claimed by your guild. " +
                        "Chunk at " + chunk.x() + ", " + chunk.z() + " is not claimed by your guild.");
            }
        }

        // Check chunks are contiguous (if more than one)
        if (intersectingChunks.size() > 1 && !areChunksContiguous(intersectingChunks)) {
            return SubregionCreationResult.failure(
                    "Multi-chunk regions must use contiguous (adjacent) chunks");
        }

        // All validations passed, save the region
        Subregion region = Subregion.fromLocations(guildId, name, pos1, pos2, playerId, type);
        subregionRepository.save(region);

        return SubregionCreationResult.success(region);
    }

    /**
     * Deletes a subregion.
     *
     * @return true if deleted successfully
     */
    public boolean deleteSubregion(String guildId, UUID playerId, String regionName) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(regionName, "Region name cannot be null");

        Optional<Subregion> regionOpt = subregionRepository.findByGuildAndName(guildId, regionName);
        if (regionOpt.isEmpty()) {
            return false;
        }

        Subregion region = regionOpt.get();

        // Check permission: must have MANAGE_REGIONS or be a region owner
        boolean hasPermission = permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS);
        boolean isOwner = region.isOwner(playerId);

        if (!hasPermission && !isOwner) {
            return false;
        }

        // Clean up region permissions before deleting region
        regionPermissionService.clearRegionPermissions(region.getId());

        subregionRepository.delete(region.getId());
        return true;
    }

    /**
     * Gets a subregion at a specific location.
     */
    public Optional<Subregion> getSubregionAt(Location loc) {
        Objects.requireNonNull(loc, "Location cannot be null");

        List<Subregion> regions = subregionRepository.findByLocation(loc);
        return regions.isEmpty() ? Optional.empty() : Optional.of(regions.get(0));
    }

    /**
     * Gets all subregions at a location (for handling overlaps).
     */
    public List<Subregion> getAllSubregionsAt(Location loc) {
        Objects.requireNonNull(loc, "Location cannot be null");
        return subregionRepository.findByLocation(loc);
    }

    /**
     * Gets all subregions for a guild.
     */
    public List<Subregion> getGuildSubregions(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return subregionRepository.findByGuild(guildId);
    }

    /**
     * Finds a subregion by guild and name.
     */
    public Optional<Subregion> getSubregionByName(String guildId, String name) {
        return subregionRepository.findByGuildAndName(guildId, name);
    }

    /**
     * Adds an owner to a subregion.
     */
    public boolean addSubregionOwner(String guildId, UUID requesterId, String regionName, UUID targetId) {
        Optional<Subregion> regionOpt = subregionRepository.findByGuildAndName(guildId, regionName);
        if (regionOpt.isEmpty()) {
            return false;
        }

        Subregion region = regionOpt.get();

        // Check permission
        boolean hasPermission = permissionService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_REGIONS);
        boolean isOwner = region.isOwner(requesterId);

        if (!hasPermission && !isOwner) {
            return false;
        }

        region.addOwner(targetId);
        subregionRepository.save(region);
        return true;
    }

    /**
     * Removes an owner from a subregion.
     */
    public boolean removeSubregionOwner(String guildId, UUID requesterId, String regionName, UUID targetId) {
        Optional<Subregion> regionOpt = subregionRepository.findByGuildAndName(guildId, regionName);
        if (regionOpt.isEmpty()) {
            return false;
        }

        Subregion region = regionOpt.get();

        // Check permission
        boolean hasPermission = permissionService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_REGIONS);
        boolean isOwner = region.isOwner(requesterId);

        if (!hasPermission && !isOwner) {
            return false;
        }

        if (!region.removeOwner(targetId)) {
            return false; // Can't remove the creator
        }

        subregionRepository.save(region);
        return true;
    }

    /**
     * Sets permissions on a subregion.
     */
    public boolean setSubregionPermissions(String guildId, UUID requesterId, String regionName, int permissions) {
        Optional<Subregion> regionOpt = subregionRepository.findByGuildAndName(guildId, regionName);
        if (regionOpt.isEmpty()) {
            return false;
        }

        Subregion region = regionOpt.get();

        // Check permission
        boolean hasPermission = permissionService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_REGIONS);
        boolean isOwner = region.isOwner(requesterId);

        if (!hasPermission && !isOwner) {
            return false;
        }

        region.setPermissions(permissions);
        subregionRepository.save(region);
        return true;
    }

    /**
     * Checks if a chunk has any subregions (used before unclaim).
     */
    public List<Subregion> getSubregionsInChunk(ChunkKey chunk) {
        return subregionRepository.findOverlappingChunk(chunk);
    }

    /**
     * Gets a subregion by its ID.
     */
    public Optional<Subregion> getSubregionById(String regionId) {
        return subregionRepository.findById(regionId);
    }

    /**
     * Sets the type of an existing subregion.
     *
     * @param guildId     the guild ID
     * @param requesterId the requesting player's ID
     * @param regionName  the region name
     * @param typeId      the new type ID (null to remove type)
     * @return true if updated successfully
     */
    public boolean setSubregionType(String guildId, UUID requesterId, String regionName, String typeId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(requesterId, "Requester ID cannot be null");
        Objects.requireNonNull(regionName, "Region name cannot be null");

        // Validate type if specified
        if (typeId != null && !typeRegistry.isRegistered(typeId)) {
            return false;
        }

        Optional<Subregion> regionOpt = subregionRepository.findByGuildAndName(guildId, regionName);
        if (regionOpt.isEmpty()) {
            return false;
        }

        Subregion region = regionOpt.get();

        // Check permission: must have MANAGE_REGIONS or be a region owner
        boolean hasPermission = permissionService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_REGIONS);
        boolean isOwner = region.isOwner(requesterId);

        if (!hasPermission && !isOwner) {
            return false;
        }

        region.setType(typeId);
        subregionRepository.save(region);
        return true;
    }

    /**
     * Checks if a player has a specific permission in a subregion.
     * Uses new permission hierarchy: Player → Role → Region → Guild
     */
    public boolean hasSubregionPermission(Subregion region, UUID playerId, GuildPermission permission) {
        return regionPermissionService.hasPermission(region, playerId, permission);
    }

    /**
     * Gets all chunk keys that a subregion intersects.
     */
    private Set<ChunkKey> getIntersectingChunks(Subregion region) {
        Set<ChunkKey> chunks = new HashSet<>();
        int minChunkX = region.getMinX() >> 4;
        int maxChunkX = region.getMaxX() >> 4;
        int minChunkZ = region.getMinZ() >> 4;
        int maxChunkZ = region.getMaxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunks.add(new ChunkKey(region.getWorld(), cx, cz));
            }
        }
        return chunks;
    }

    /**
     * Checks if chunks are contiguous using BFS.
     */
    private boolean areChunksContiguous(Set<ChunkKey> chunks) {
        if (chunks.size() <= 1) return true;

        Set<ChunkKey> visited = new HashSet<>();
        Queue<ChunkKey> queue = new LinkedList<>();
        ChunkKey start = chunks.iterator().next();
        queue.add(start);

        while (!queue.isEmpty()) {
            ChunkKey current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            // Check all 4 adjacent chunks
            for (ChunkKey neighbor : getAdjacentChunks(current)) {
                if (chunks.contains(neighbor) && !visited.contains(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return visited.size() == chunks.size();
    }

    private List<ChunkKey> getAdjacentChunks(ChunkKey chunk) {
        return List.of(
                new ChunkKey(chunk.world(), chunk.x() - 1, chunk.z()),
                new ChunkKey(chunk.world(), chunk.x() + 1, chunk.z()),
                new ChunkKey(chunk.world(), chunk.x(), chunk.z() - 1),
                new ChunkKey(chunk.world(), chunk.x(), chunk.z() + 1)
        );
    }

    /**
     * Result of subregion creation attempt.
     */
    public record SubregionCreationResult(boolean success, Subregion region, String errorMessage) {
        public static SubregionCreationResult success(Subregion region) {
            return new SubregionCreationResult(true, region, null);
        }

        public static SubregionCreationResult failure(String errorMessage) {
            return new SubregionCreationResult(false, null, errorMessage);
        }
    }
}

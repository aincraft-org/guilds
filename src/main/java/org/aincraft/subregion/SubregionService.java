package org.aincraft.subregion;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.bukkit.Location;

import java.util.*;

/**
 * Service for managing subregions with validation and business logic.
 */
public class SubregionService {
    private static final long DEFAULT_MAX_VOLUME = 100_000;

    private final SubregionRepository subregionRepository;
    private final GuildService guildService;
    private final SubregionTypeRegistry typeRegistry;
    private long maxVolume = DEFAULT_MAX_VOLUME;

    @Inject
    public SubregionService(SubregionRepository subregionRepository, GuildService guildService,
                            SubregionTypeRegistry typeRegistry) {
        this.subregionRepository = Objects.requireNonNull(subregionRepository);
        this.guildService = Objects.requireNonNull(guildService);
        this.typeRegistry = Objects.requireNonNull(typeRegistry);
    }

    /**
     * Gets the type registry for external access.
     */
    public SubregionTypeRegistry getTypeRegistry() {
        return typeRegistry;
    }

    public void setMaxVolume(long maxVolume) {
        this.maxVolume = maxVolume;
    }

    /**
     * Creates a new subregion after validating all constraints.
     *
     * @return the created subregion, or empty if validation failed
     */
    public SubregionCreationResult createSubregion(String guildId, UUID playerId, String name,
                                                    Location pos1, Location pos2) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(pos1, "Position 1 cannot be null");
        Objects.requireNonNull(pos2, "Position 2 cannot be null");

        // Check permission
        if (!guildService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS)) {
            return SubregionCreationResult.failure("You don't have permission to manage regions");
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
        Subregion temp = Subregion.fromLocations(guildId, name, pos1, pos2, playerId);

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
            Guild owner = guildService.getChunkOwner(chunk);
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
        Subregion region = Subregion.fromLocations(guildId, name, pos1, pos2, playerId);
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
        boolean hasPermission = guildService.hasPermission(guildId, playerId, GuildPermission.MANAGE_REGIONS);
        boolean isOwner = region.isOwner(playerId);

        if (!hasPermission && !isOwner) {
            return false;
        }

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
        boolean hasPermission = guildService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_REGIONS);
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
        boolean hasPermission = guildService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_REGIONS);
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
        boolean hasPermission = guildService.hasPermission(guildId, requesterId, GuildPermission.MANAGE_REGIONS);
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
     * Checks if a player has a specific permission in a subregion.
     * Falls back to guild permissions if subregion doesn't override.
     */
    public boolean hasSubregionPermission(Subregion region, UUID playerId, GuildPermission permission) {
        // Guild owner always has permission
        Guild guild = guildService.getGuildById(region.getGuildId());
        if (guild != null && guild.isOwner(playerId)) {
            return true;
        }

        // Region owners have all permissions in their region
        if (region.isOwner(playerId)) {
            return true;
        }

        // Check if subregion has explicit permissions set
        int regionPerms = region.getPermissions();
        if (regionPerms != 0) {
            return (regionPerms & permission.getBit()) != 0;
        }

        // Fall back to guild permissions
        return guildService.hasPermission(region.getGuildId(), playerId, permission);
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

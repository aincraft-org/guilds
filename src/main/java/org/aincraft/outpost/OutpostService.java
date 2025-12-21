package org.aincraft.outpost;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.bukkit.Location;

/**
 * Service for managing guild outposts.
 * Single Responsibility: Business logic for outpost operations.
 * Dependency Inversion: Depends on repositories and services via injection.
 */
@Singleton
public class OutpostService {
    private final OutpostRepository outpostRepository;
    private final GuildService guildService;
    private final org.aincraft.service.PermissionService permissionService;

    @Inject
    public OutpostService(OutpostRepository outpostRepository, GuildService guildService,
                         org.aincraft.service.PermissionService permissionService) {
        this.outpostRepository = Objects.requireNonNull(outpostRepository, "Outpost repository cannot be null");
        this.guildService = Objects.requireNonNull(guildService, "Guild service cannot be null");
        this.permissionService = Objects.requireNonNull(permissionService, "Permission service cannot be null");
    }

    /**
     * Creates a new outpost at the player's current location.
     *
     * @param guildId the guild ID
     * @param playerId the player creating the outpost
     * @param name the outpost name
     * @param location the location for the outpost
     * @return CreateOutpostResult with status
     */
    public CreateOutpostResult createOutpost(UUID guildId, UUID playerId, String name, Location location) {
        // Validate inputs
        if (guildId == null || playerId == null || name == null || location == null) {
            return CreateOutpostResult.failure("Invalid parameters");
        }

        // Check permission
        if (!permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_SPAWN)) {
            return CreateOutpostResult.failure("You don't have permission to create outposts");
        }

        // Get guild
        Guild guild = guildService.getGuildById(guildId);
        if (guild == null) {
            return CreateOutpostResult.failure("Guild not found");
        }

        // Get chunk
        ChunkKey chunk = ChunkKey.from(location.getChunk());

        // Check guild owns this chunk
        Guild chunkOwner = guildService.getChunkOwner(chunk);
        if (chunkOwner == null || !chunkOwner.getId().equals(guildId)) {
            return CreateOutpostResult.failure("Your guild does not own this chunk");
        }

        // Cannot create outpost in homeblock
        if (guild.hasHomeblock() && guild.getHomeblock().equals(chunk)) {
            return CreateOutpostResult.failure("Cannot create outpost in homeblock (use /g setspawn for main spawn)");
        }

        // Check name not already used
        if (outpostRepository.findByGuildAndName(guildId, name).isPresent()) {
            return CreateOutpostResult.failure("An outpost with that name already exists");
        }

        // Create outpost
        Optional<Outpost> outpostOpt = Outpost.create(
            guildId, name, chunk, location.getWorld().getName(),
            location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch(), playerId
        );

        if (outpostOpt.isEmpty()) {
            return CreateOutpostResult.failure("Invalid outpost name (3-32 chars, alphanumeric + spaces/dashes)");
        }

        Outpost outpost = outpostOpt.get();
        if (!outpostRepository.save(outpost)) {
            return CreateOutpostResult.failure("Failed to save outpost to database");
        }

        return CreateOutpostResult.success(outpost);
    }

    /**
     * Deletes an outpost.
     *
     * @param guildId the guild ID
     * @param playerId the player deleting the outpost
     * @param outpostName the name of the outpost to delete
     * @return true if deleted successfully
     */
    public boolean deleteOutpost(UUID guildId, UUID playerId, String outpostName) {
        // Check permission
        if (!permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_SPAWN)) {
            return false;
        }

        // Find outpost
        Optional<Outpost> outpostOpt = outpostRepository.findByGuildAndName(guildId, outpostName);
        if (outpostOpt.isEmpty()) {
            return false;
        }

        return outpostRepository.delete(outpostOpt.get().getId());
    }

    /**
     * Gets an outpost by name within a guild.
     *
     * @param guildId the guild ID
     * @param name the outpost name
     * @return the outpost, or empty if not found
     */
    public Optional<Outpost> getOutpost(UUID guildId, String name) {
        return outpostRepository.findByGuildAndName(guildId, name);
    }

    /**
     * Gets all outposts for a guild.
     *
     * @param guildId the guild ID
     * @return list of outposts
     */
    public List<Outpost> getOutposts(UUID guildId) {
        return outpostRepository.findByGuild(guildId);
    }

    /**
     * Updates an outpost's spawn location.
     *
     * @param guildId the guild ID
     * @param playerId the player updating the spawn
     * @param outpostName the outpost name
     * @param location the new spawn location
     * @return SetSpawnResult with status
     */
    public SetSpawnResult setOutpostSpawn(UUID guildId, UUID playerId, String outpostName, Location location) {
        // Check permission
        if (!permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_SPAWN)) {
            return SetSpawnResult.failure("You don't have permission to update outpost spawn");
        }

        // Find outpost
        Optional<Outpost> outpostOpt = outpostRepository.findByGuildAndName(guildId, outpostName);
        if (outpostOpt.isEmpty()) {
            return SetSpawnResult.failure("Outpost not found");
        }

        Outpost outpost = outpostOpt.get();

        // Check location is in outpost chunk
        ChunkKey spawnChunk = ChunkKey.from(location.getChunk());
        if (!spawnChunk.equals(outpost.getLocation())) {
            return SetSpawnResult.failure("Spawn location must be in the outpost's chunk");
        }

        // Create updated outpost with new spawn
        Optional<Outpost> updatedOpt = Outpost.restore(
            outpost.getId(), outpost.getGuildId(), outpost.getName(),
            outpost.getLocation(), location.getWorld().getName(),
            location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch(),
            outpost.getCreatedAt(), outpost.getCreatedBy()
        );

        if (updatedOpt.isEmpty()) {
            return SetSpawnResult.failure("Failed to update outpost spawn");
        }

        if (!outpostRepository.save(updatedOpt.get())) {
            return SetSpawnResult.failure("Failed to save updated outpost to database");
        }

        return SetSpawnResult.success(updatedOpt.get());
    }

    /**
     * Renames an outpost.
     *
     * @param guildId the guild ID
     * @param playerId the player renaming the outpost
     * @param oldName the old outpost name
     * @param newName the new outpost name
     * @return RenameResult with status
     */
    public RenameResult renameOutpost(UUID guildId, UUID playerId, String oldName, String newName) {
        // Check permission
        if (!permissionService.hasPermission(guildId, playerId, GuildPermission.MANAGE_SPAWN)) {
            return RenameResult.failure("You don't have permission to rename outposts");
        }

        // Find old outpost
        Optional<Outpost> oldOutpostOpt = outpostRepository.findByGuildAndName(guildId, oldName);
        if (oldOutpostOpt.isEmpty()) {
            return RenameResult.failure("Outpost not found");
        }

        Outpost oldOutpost = oldOutpostOpt.get();

        // Check new name not already used
        if (outpostRepository.findByGuildAndName(guildId, newName).isPresent()) {
            return RenameResult.failure("An outpost with that name already exists");
        }

        // Create renamed outpost
        Optional<Outpost> renamedOpt = Outpost.restore(
            oldOutpost.getId(), oldOutpost.getGuildId(), newName,
            oldOutpost.getLocation(), oldOutpost.getSpawnWorld(),
            oldOutpost.getSpawnX(), oldOutpost.getSpawnY(), oldOutpost.getSpawnZ(),
            oldOutpost.getSpawnYaw(), oldOutpost.getSpawnPitch(),
            oldOutpost.getCreatedAt(), oldOutpost.getCreatedBy()
        );

        if (renamedOpt.isEmpty()) {
            return RenameResult.failure("Invalid outpost name (3-32 chars, alphanumeric + spaces/dashes)");
        }

        if (!outpostRepository.save(renamedOpt.get())) {
            return RenameResult.failure("Failed to save renamed outpost to database");
        }

        return RenameResult.success(renamedOpt.get());
    }

    /**
     * Deletes all outposts when a guild is deleted.
     *
     * @param guildId the guild ID
     */
    public void deleteOutpostsForGuild(UUID guildId) {
        outpostRepository.deleteByGuild(guildId);
    }

    /**
     * Deletes all outposts in a chunk when it's unclaimed.
     *
     * @param chunk the chunk being unclaimed
     */
    public void deleteOutpostsInChunk(ChunkKey chunk) {
        outpostRepository.deleteByChunk(chunk);
    }

    /**
     * Gets the Bukkit Location for an outpost's spawn.
     *
     * @param outpost the outpost
     * @return the spawn location, or null if world not found
     */
    public Location getOutpostSpawnLocation(Outpost outpost) {
        Objects.requireNonNull(outpost, "Outpost cannot be null");

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(outpost.getSpawnWorld());
        if (world == null) {
            return null;
        }

        Location loc = new Location(world, outpost.getSpawnX(), outpost.getSpawnY(),
                                     outpost.getSpawnZ(), outpost.getSpawnYaw(),
                                     outpost.getSpawnPitch());
        return loc;
    }

    // Result classes for better error handling
    public static class CreateOutpostResult {
        private final boolean success;
        private final String message;
        private final Outpost outpost;

        private CreateOutpostResult(boolean success, String message, Outpost outpost) {
            this.success = success;
            this.message = message;
            this.outpost = outpost;
        }

        public static CreateOutpostResult success(Outpost outpost) {
            return new CreateOutpostResult(true, "Outpost created successfully", outpost);
        }

        public static CreateOutpostResult failure(String message) {
            return new CreateOutpostResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Outpost getOutpost() {
            return outpost;
        }
    }

    public static class SetSpawnResult {
        private final boolean success;
        private final String message;
        private final Outpost outpost;

        private SetSpawnResult(boolean success, String message, Outpost outpost) {
            this.success = success;
            this.message = message;
            this.outpost = outpost;
        }

        public static SetSpawnResult success(Outpost outpost) {
            return new SetSpawnResult(true, "Spawn location updated", outpost);
        }

        public static SetSpawnResult failure(String message) {
            return new SetSpawnResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Outpost getOutpost() {
            return outpost;
        }
    }

    public static class RenameResult {
        private final boolean success;
        private final String message;
        private final Outpost outpost;

        private RenameResult(boolean success, String message, Outpost outpost) {
            this.success = success;
            this.message = message;
            this.outpost = outpost;
        }

        public static RenameResult success(Outpost outpost) {
            return new RenameResult(true, "Outpost renamed successfully", outpost);
        }

        public static RenameResult failure(String message) {
            return new RenameResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Outpost getOutpost() {
            return outpost;
        }
    }
}

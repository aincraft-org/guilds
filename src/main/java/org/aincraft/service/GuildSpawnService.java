package org.aincraft.service;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.storage.GuildRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Service for managing guild spawn locations.
 * Single Responsibility: Spawn point management.
 */
public class GuildSpawnService {
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_CENTER_OFFSET = 8;
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final int HEAD_BLOCK_OFFSET = 1;
    private static final int GROUND_BLOCK_OFFSET = -1;

    private final GuildRepository guildRepository;

    @Inject
    public GuildSpawnService(GuildRepository guildRepository) {
        this.guildRepository = Objects.requireNonNull(guildRepository, "GuildRepository cannot be null");
    }

    /**
     * Sets the guild spawn location.
     * If location is outside homeblock, it will be relocated to a safe spot within homeblock.
     *
     * @param guildId the guild ID
     * @param playerId the player setting the spawn
     * @param location the location to set as spawn
     * @param hasManageSpawnPermission whether the player has MANAGE_SPAWN permission
     * @return true if spawn was set successfully
     */
    public boolean setGuildSpawn(UUID guildId, UUID playerId, Location location, boolean hasManageSpawnPermission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");

        if (!hasManageSpawnPermission) {
            return false;
        }

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();

        if (!guild.hasHomeblock()) {
            return false;
        }

        Location finalLocation = location;
        if (!guild.isInHomeblock(location)) {
            finalLocation = findSafeLocationInHomeblock(guild, location);
            if (finalLocation == null) {
                return false;
            }
        }

        guild.setSpawn(finalLocation);
        guildRepository.save(guild);
        return true;
    }

    /**
     * Clears the guild spawn location.
     *
     * @param guildId the guild ID
     * @param playerId the player clearing the spawn
     * @param hasManageSpawnPermission whether the player has MANAGE_SPAWN permission
     * @return true if spawn was cleared successfully
     */
    public boolean clearGuildSpawn(UUID guildId, UUID playerId, boolean hasManageSpawnPermission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        if (!hasManageSpawnPermission) {
            return false;
        }

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return false;
        }

        Guild guild = guildOpt.get();
        guild.clearSpawn();
        guildRepository.save(guild);
        return true;
    }

    /**
     * Gets the guild spawn location as a Bukkit Location.
     *
     * @param guildId the guild ID
     * @return the spawn location, or null if no spawn is set or world is not loaded
     */
    public Location getGuildSpawnLocation(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Optional<Guild> guildOpt = guildRepository.findById(guildId);
        if (guildOpt.isEmpty()) {
            return null;
        }

        Guild guild = guildOpt.get();
        if (!guild.hasSpawn()) {
            return null;
        }

        World world = Bukkit.getWorld(guild.getSpawnWorld());
        if (world == null) {
            return null;
        }

        return new Location(
            world,
            guild.getSpawnX(),
            guild.getSpawnY(),
            guild.getSpawnZ(),
            guild.getSpawnYaw(),
            guild.getSpawnPitch()
        );
    }

    /**
     * Gets the homeblock chunk for a guild.
     *
     * @param guildId the guild ID
     * @return the homeblock ChunkKey, or null if not set
     */
    public ChunkKey getGuildHomeblock(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return guildRepository.findById(guildId)
                .map(Guild::getHomeblock)
                .orElse(null);
    }

    /**
     * Auto-sets spawn at homeblock center for first claim.
     *
     * @param guild the guild
     * @param chunk the homeblock chunk
     */
    public void autoSetSpawnAtHomeblock(Guild guild, ChunkKey chunk) {
        World world = Bukkit.getWorld(chunk.world());
        if (world == null) {
            return;
        }

        int centerX = chunk.x() * CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        int centerZ = chunk.z() * CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        int y = world.getHighestBlockYAt(centerX, centerZ);

        Location spawnLoc = new Location(
            world,
            centerX + BLOCK_CENTER_OFFSET,
            y + HEAD_BLOCK_OFFSET,
            centerZ + BLOCK_CENTER_OFFSET
        );

        if (isSafeSpawnLocation(spawnLoc)) {
            guild.setSpawn(spawnLoc);
            guildRepository.save(guild);
        }
    }

    private Location findSafeLocationInHomeblock(Guild guild, Location originalLoc) {
        if (!guild.hasHomeblock()) {
            return null;
        }

        ChunkKey homeblock = guild.getHomeblock();
        World world = Bukkit.getWorld(homeblock.world());
        if (world == null) {
            return null;
        }

        int centerX = homeblock.x() * CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        int centerZ = homeblock.z() * CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        int y = world.getHighestBlockYAt(centerX, centerZ);

        Location safeLoc = new Location(
            world,
            centerX + BLOCK_CENTER_OFFSET,
            y + HEAD_BLOCK_OFFSET,
            centerZ + BLOCK_CENTER_OFFSET,
            originalLoc.getYaw(),
            originalLoc.getPitch()
        );

        if (isSafeSpawnLocation(safeLoc)) {
            return safeLoc;
        }

        return null;
    }

    private boolean isSafeSpawnLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        Material feetBlock = world.getBlockAt(x, y, z).getType();
        Material headBlock = world.getBlockAt(x, y + HEAD_BLOCK_OFFSET, z).getType();

        if (!feetBlock.isAir() || !headBlock.isAir()) {
            return false;
        }

        Material groundBlock = world.getBlockAt(x, y + GROUND_BLOCK_OFFSET, z).getType();
        return groundBlock.isSolid();
    }
}

package org.aincraft.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildService;
import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

/**
 * Facade service for guild spawn operations.
 * Delegates to GuildService for all operations.
 */
@Singleton
public class SpawnService {
    private final GuildService guildService;

    @Inject
    public SpawnService(GuildService guildService) {
        this.guildService = Objects.requireNonNull(guildService);
    }

    /**
     * Sets the spawn location for a guild.
     *
     * @param guildId the guild ID
     * @param playerId the player UUID (for permission check)
     * @param location the spawn location
     * @return true if set successfully
     */
    public boolean setGuildSpawn(UUID guildId, UUID playerId, Location location) {
        return guildService.setGuildSpawn(guildId, playerId, location);
    }

    /**
     * Gets the spawn location for a guild.
     *
     * @param guildId the guild ID
     * @return the spawn location, or null if not set
     */
    public Location getGuildSpawnLocation(UUID guildId) {
        return guildService.getGuildSpawnLocation(guildId);
    }
}

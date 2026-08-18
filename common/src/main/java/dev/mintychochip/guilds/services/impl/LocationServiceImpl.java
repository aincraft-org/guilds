package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildBlock;
import dev.mintychochip.guilds.services.LocationService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.GuildService;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Implementation of LocationService
 * Provides location-based queries for guilds and plots
 */

public class LocationServiceImpl implements LocationService {

    /** The logger. */
    private static final Logger logger = Logger.getLogger(LocationServiceImpl.class.getName());

    /** The plot service. */
    private final PlotService plotService;
    /** The guild service. */
    private final GuildService guildService;


    /**
     * Creates a new location service impl instance.
     * @param plotService the plot service
     * @param guildService the guild service
     */
    public LocationServiceImpl(PlotService plotService, GuildService guildService) {
        this.plotService = plotService;
        this.guildService = guildService;
    }

    /**
     * Returns the guild at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public Optional<Guild> getGuildAtLocation(int x, int z, String world) {
        try {
            // Convert block coordinates to chunk coordinates
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            // Get the guild block at this location
            Optional<GuildBlock> guildBlock = plotService.getGuildBlock(chunkX, chunkZ, world);
            if (guildBlock.isEmpty()) {
                return Optional.empty(); // Wilderness
            }

            // Get the guild from the guild block
            String guildId = guildBlock.get().getGuildId();
            return guildService.getGuildById(guildId);

        } catch (Exception e) {
            logger.warning("Error getting guild at location (" + x + ", " + z + ") in world " + world + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the guild block at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public Optional<GuildBlock> getGuildBlockAtLocation(int x, int z, String world) {
        try {
            // Convert block coordinates to chunk coordinates
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            return plotService.getGuildBlock(chunkX, chunkZ, world);

        } catch (Exception e) {
            logger.warning("Error getting guild block at location (" + x + ", " + z + ") in world " + world + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns whether wilderness.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean isWilderness(int x, int z, String world) {
        return getGuildBlockAtLocation(x, z, world).isEmpty();
    }
}

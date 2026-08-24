package org.aincraft.guilds.services.impl;



import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.GuildService;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Implementation of LocationService
 * Provides location-based queries for guilds and plots
 */

public class LocationServiceImpl implements LocationService {

    private static final Logger logger = Logger.getLogger(LocationServiceImpl.class.getName());

    private final PlotService plotService;
    private final GuildService guildService;


    public LocationServiceImpl(PlotService plotService, GuildService guildService) {
        this.plotService = plotService;
        this.guildService = guildService;
    }

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

    @Override
    public boolean isWilderness(int x, int z, String world) {
        return getGuildBlockAtLocation(x, z, world).isEmpty();
    }
}

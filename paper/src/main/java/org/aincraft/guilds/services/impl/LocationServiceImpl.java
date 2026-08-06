package org.aincraft.guilds.services.impl;



import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownBlock;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.TownService;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Implementation of LocationService
 * Provides location-based queries for towns and plots
 */

public class LocationServiceImpl implements LocationService {

    private static final Logger logger = Logger.getLogger(LocationServiceImpl.class.getName());

    private final PlotService plotService;
    private final TownService townService;


    public LocationServiceImpl(PlotService plotService, TownService townService) {
        this.plotService = plotService;
        this.townService = townService;
    }

    @Override
    public Optional<Town> getTownAtLocation(int x, int z, String world) {
        try {
            // Convert block coordinates to chunk coordinates
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            // Get the town block at this location
            Optional<TownBlock> townBlock = plotService.getTownBlock(chunkX, chunkZ, world);
            if (townBlock.isEmpty()) {
                return Optional.empty(); // Wilderness
            }

            // Get the town from the town block
            String townId = townBlock.get().getTownId();
            return townService.getTownById(townId);

        } catch (Exception e) {
            logger.warning("Error getting town at location (" + x + ", " + z + ") in world " + world + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<TownBlock> getTownBlockAtLocation(int x, int z, String world) {
        try {
            // Convert block coordinates to chunk coordinates
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            return plotService.getTownBlock(chunkX, chunkZ, world);

        } catch (Exception e) {
            logger.warning("Error getting town block at location (" + x + ", " + z + ") in world " + world + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isWilderness(int x, int z, String world) {
        return getTownBlockAtLocation(x, z, world).isEmpty();
    }
}

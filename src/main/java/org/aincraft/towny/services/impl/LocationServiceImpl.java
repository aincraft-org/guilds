package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.services.LocationService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.TownService;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Implementation of LocationService
 * Provides location-based queries for towns and plots
 */
@Singleton
public class LocationServiceImpl implements LocationService {

    private static final Logger logger = Logger.getLogger(LocationServiceImpl.class.getName());

    private final PlotService plotService;
    private final TownService townService;

    @Inject
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

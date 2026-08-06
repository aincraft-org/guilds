package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.LocationService;
import org.aincraft.towny.services.TownToggleService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of TownToggleService
 * Manages town toggle settings (PvP, fire, explosions, mobs, public access)
 */
@Singleton
public class TownToggleServiceImpl implements TownToggleService {

    private final LocationService locationService;

    @Inject
    public TownToggleServiceImpl(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    public boolean isPvpEnabledAtLocation(int x, int z, String world) {
        Optional<Town> town = locationService.getTownAtLocation(x, z, world);
        return town.map(Town::isPvpEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean isFireEnabledAtLocation(int x, int z, String world) {
        Optional<Town> town = locationService.getTownAtLocation(x, z, world);
        return town.map(Town::isFireEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean areExplosionsEnabledAtLocation(int x, int z, String world) {
        Optional<Town> town = locationService.getTownAtLocation(x, z, world);
        return town.map(Town::isExplosionsEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean areMobsEnabledAtLocation(int x, int z, String world) {
        Optional<Town> town = locationService.getTownAtLocation(x, z, world);
        return town.map(Town::isMobsEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean isPublicAccessEnabledAtLocation(int x, int z, String world) {
        Optional<Town> town = locationService.getTownAtLocation(x, z, world);
        return town.map(Town::isPublicEnabled).orElse(true); // Default to public in wilderness
    }

    @Override
    public Map<String, Boolean> getTogglesAtLocation(int x, int z, String world) {
        Optional<Town> town = locationService.getTownAtLocation(x, z, world);
        return town.map(Town::getAllToggles).orElse(new HashMap<>());
    }
}

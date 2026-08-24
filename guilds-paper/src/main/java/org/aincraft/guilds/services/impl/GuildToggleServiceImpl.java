package org.aincraft.guilds.services.impl;



import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.GuildToggleService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of GuildToggleService
 * Manages guild toggle settings (PvP, fire, explosions, mobs, public access)
 */

public class GuildToggleServiceImpl implements GuildToggleService {

    private final LocationService locationService;


    public GuildToggleServiceImpl(LocationService locationService) {
        this.locationService = locationService;
    }

    @Override
    public boolean isPvpEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isPvpEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean isFireEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isFireEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean areExplosionsEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isExplosionsEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean areMobsEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isMobsEnabled).orElse(true); // Default to enabled in wilderness
    }

    @Override
    public boolean isPublicAccessEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isPublicEnabled).orElse(true); // Default to public in wilderness
    }

    @Override
    public Map<String, Boolean> getTogglesAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::getAllToggles).orElse(new HashMap<>());
    }
}

package dev.mintychochip.guilds.services.impl;



import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.LocationService;
import dev.mintychochip.guilds.services.GuildToggleService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of GuildToggleService
 * Manages guild toggle settings (PvP, fire, explosions, mobs, public access)
 */

public class GuildToggleServiceImpl implements GuildToggleService {

    /** The location service. */
    private final LocationService locationService;


    /**
     * Creates a new guild toggle service impl instance.
     * @param locationService the location service
     */
    public GuildToggleServiceImpl(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Returns whether pvp enabled at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean isPvpEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isPvpEnabled).orElse(true); // Default to enabled in wilderness
    }

    /**
     * Returns whether fire enabled at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean isFireEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isFireEnabled).orElse(true); // Default to enabled in wilderness
    }

    /**
     * Performs the are explosions enabled at location operation.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean areExplosionsEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isExplosionsEnabled).orElse(true); // Default to enabled in wilderness
    }

    /**
     * Performs the are mobs enabled at location operation.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean areMobsEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isMobsEnabled).orElse(true); // Default to enabled in wilderness
    }

    /**
     * Returns whether public access enabled at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public boolean isPublicAccessEnabledAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::isPublicEnabled).orElse(true); // Default to public in wilderness
    }

    /**
     * Returns the toggles at location.
     * @param x the x
     * @param z the z
     * @param world the world
     * @return the result
     */
    @Override
    public Map<String, Boolean> getTogglesAtLocation(int x, int z, String world) {
        Optional<Guild> guild = locationService.getGuildAtLocation(x, z, world);
        return guild.map(Guild::getAllToggles).orElse(new HashMap<>());
    }
}

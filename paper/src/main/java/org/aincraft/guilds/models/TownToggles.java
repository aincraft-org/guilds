package org.aincraft.guilds.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates town toggle settings (pvp, fire, explosions, mobs, public)
 * Extracted from Town.java to follow Single Responsibility Principle
 */
public class TownToggles {

    private boolean pvpEnabled;
    private boolean fireEnabled;
    private boolean explosionsEnabled;
    private boolean mobsEnabled;
    private boolean publicEnabled;

    /**
     * Default constructor with default toggle values
     */
    public TownToggles() {
        this.pvpEnabled = false;
        this.fireEnabled = false;
        this.explosionsEnabled = false;
        this.mobsEnabled = true;
        this.publicEnabled = false;
    }

    /**
     * Constructor with initial toggle values
     * @param pvpEnabled PvP enabled
     * @param fireEnabled Fire enabled
     * @param explosionsEnabled Explosions enabled
     * @param mobsEnabled Mobs enabled
     * @param publicEnabled Public access enabled
     */
    public TownToggles(boolean pvpEnabled, boolean fireEnabled, boolean explosionsEnabled,
                      boolean mobsEnabled, boolean publicEnabled) {
        this.pvpEnabled = pvpEnabled;
        this.fireEnabled = fireEnabled;
        this.explosionsEnabled = explosionsEnabled;
        this.mobsEnabled = mobsEnabled;
        this.publicEnabled = publicEnabled;
    }

    // Getters and Setters

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isFireEnabled() {
        return fireEnabled;
    }

    public void setFireEnabled(boolean fireEnabled) {
        this.fireEnabled = fireEnabled;
    }

    public boolean isExplosionsEnabled() {
        return explosionsEnabled;
    }

    public void setExplosionsEnabled(boolean explosionsEnabled) {
        this.explosionsEnabled = explosionsEnabled;
    }

    public boolean isMobsEnabled() {
        return mobsEnabled;
    }

    public void setMobsEnabled(boolean mobsEnabled) {
        this.mobsEnabled = mobsEnabled;
    }

    public boolean isPublicEnabled() {
        return publicEnabled;
    }

    public void setPublicEnabled(boolean publicEnabled) {
        this.publicEnabled = publicEnabled;
    }

    // Business methods

    /**
     * Toggle PvP setting for the town
     * @return New PvP state after toggle
     */
    public boolean togglePvp() {
        pvpEnabled = !pvpEnabled;
        return pvpEnabled;
    }

    /**
     * Toggle fire setting for the town
     * @return New fire state after toggle
     */
    public boolean toggleFire() {
        fireEnabled = !fireEnabled;
        return fireEnabled;
    }

    /**
     * Toggle explosions setting for the town
     * @return New explosions state after toggle
     */
    public boolean toggleExplosions() {
        explosionsEnabled = !explosionsEnabled;
        return explosionsEnabled;
    }

    /**
     * Toggle mobs setting for the town
     * @return New mobs state after toggle
     */
    public boolean toggleMobs() {
        mobsEnabled = !mobsEnabled;
        return mobsEnabled;
    }

    /**
     * Toggle public setting for the town
     * @return New public state after toggle
     */
    public boolean togglePublic() {
        publicEnabled = !publicEnabled;
        return publicEnabled;
    }

    /**
     * Get all town toggle states
     * @return Map of toggle names to their current states
     */
    public Map<String, Boolean> getAllToggles() {
        Map<String, Boolean> toggles = new HashMap<>();
        toggles.put("pvp", pvpEnabled);
        toggles.put("fire", fireEnabled);
        toggles.put("explosions", explosionsEnabled);
        toggles.put("mobs", mobsEnabled);
        toggles.put("public", publicEnabled);
        return toggles;
    }

    /**
     * Set a specific toggle state
     * @param toggleType The toggle type to set
     * @param value The new value for the toggle
     * @return True if toggle was set successfully, false if toggle type not found
     */
    public boolean setToggle(String toggleType, boolean value) {
        if (toggleType == null) return false;

        switch (toggleType.toLowerCase()) {
            case "pvp":
                setPvpEnabled(value);
                return true;
            case "fire":
                setFireEnabled(value);
                return true;
            case "explosions":
                setExplosionsEnabled(value);
                return true;
            case "mobs":
                setMobsEnabled(value);
                return true;
            case "public":
                setPublicEnabled(value);
                return true;
            default:
                return false;
        }
    }

    /**
     * Get the current state of a specific toggle
     * @param toggleType The toggle type to get
     * @return The toggle state, or false if toggle type not found
     */
    public boolean getToggle(String toggleType) {
        if (toggleType == null) return false;

        switch (toggleType.toLowerCase()) {
            case "pvp":
                return pvpEnabled;
            case "fire":
                return fireEnabled;
            case "explosions":
                return explosionsEnabled;
            case "mobs":
                return mobsEnabled;
            case "public":
                return publicEnabled;
            default:
                return false;
        }
    }

    /**
     * Reset all toggles to default values
     */
    public void resetToDefaults() {
        this.pvpEnabled = false;
        this.fireEnabled = false;
        this.explosionsEnabled = false;
        this.mobsEnabled = true;
        this.publicEnabled = false;
    }

    /**
     * Copy toggle values from another TownToggles instance
     * @param other TownToggles to copy from
     */
    public void copyFrom(TownToggles other) {
        if (other != null) {
            this.pvpEnabled = other.pvpEnabled;
            this.fireEnabled = other.fireEnabled;
            this.explosionsEnabled = other.explosionsEnabled;
            this.mobsEnabled = other.mobsEnabled;
            this.publicEnabled = other.publicEnabled;
        }
    }

    /**
     * Create a copy of this TownToggles
     * @return New TownToggles instance with same values
     */
    public TownToggles copy() {
        return new TownToggles(pvpEnabled, fireEnabled, explosionsEnabled, mobsEnabled, publicEnabled);
    }

    @Override
    public String toString() {
        return "TownToggles{" +
                "pvp=" + pvpEnabled +
                ", fire=" + fireEnabled +
                ", explosions=" + explosionsEnabled +
                ", mobs=" + mobsEnabled +
                ", public=" + publicEnabled +
                '}';
    }
}

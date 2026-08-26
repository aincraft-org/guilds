package dev.mintychochip.guilds.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates guild toggle settings (pvp, fire, explosions, mobs, public)
 * Extracted from Guild.java to follow Single Responsibility Principle
 */
public class GuildToggles {

    /** The pvp enabled. */
    private boolean pvpEnabled;
    /** The fire enabled. */
    private boolean fireEnabled;
    /** The explosions enabled. */
    private boolean explosionsEnabled;
    /** The mobs enabled. */
    private boolean mobsEnabled;
    /** The public enabled. */
    private boolean publicEnabled;

    /**
     * Default constructor with default toggle values
     */
    public GuildToggles() {
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
    public GuildToggles(boolean pvpEnabled, boolean fireEnabled, boolean explosionsEnabled,
                      boolean mobsEnabled, boolean publicEnabled) {
        this.pvpEnabled = pvpEnabled;
        this.fireEnabled = fireEnabled;
        this.explosionsEnabled = explosionsEnabled;
        this.mobsEnabled = mobsEnabled;
        this.publicEnabled = publicEnabled;
    }

    // Getters and Setters

    /**
     * Returns whether pvp enabled.
     * @return the result
     */
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    /**
     * Sets the pvp enabled.
     * @param pvpEnabled the pvp enabled
     */
    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    /**
     * Returns whether fire enabled.
     * @return the result
     */
    public boolean isFireEnabled() {
        return fireEnabled;
    }

    /**
     * Sets the fire enabled.
     * @param fireEnabled the fire enabled
     */
    public void setFireEnabled(boolean fireEnabled) {
        this.fireEnabled = fireEnabled;
    }

    /**
     * Returns whether explosions enabled.
     * @return the result
     */
    public boolean isExplosionsEnabled() {
        return explosionsEnabled;
    }

    /**
     * Sets the explosions enabled.
     * @param explosionsEnabled the explosions enabled
     */
    public void setExplosionsEnabled(boolean explosionsEnabled) {
        this.explosionsEnabled = explosionsEnabled;
    }

    /**
     * Returns whether mobs enabled.
     * @return the result
     */
    public boolean isMobsEnabled() {
        return mobsEnabled;
    }

    /**
     * Sets the mobs enabled.
     * @param mobsEnabled the mobs enabled
     */
    public void setMobsEnabled(boolean mobsEnabled) {
        this.mobsEnabled = mobsEnabled;
    }

    /**
     * Returns whether public enabled.
     * @return the result
     */
    public boolean isPublicEnabled() {
        return publicEnabled;
    }

    /**
     * Sets the public enabled.
     * @param publicEnabled the public enabled
     */
    public void setPublicEnabled(boolean publicEnabled) {
        this.publicEnabled = publicEnabled;
    }

    // Business methods

    /**
     * Toggle PvP setting for the guild
     * @return New PvP state after toggle
     */
    public boolean togglePvp() {
        pvpEnabled = !pvpEnabled;
        return pvpEnabled;
    }

    /**
     * Toggle fire setting for the guild
     * @return New fire state after toggle
     */
    public boolean toggleFire() {
        fireEnabled = !fireEnabled;
        return fireEnabled;
    }

    /**
     * Toggle explosions setting for the guild
     * @return New explosions state after toggle
     */
    public boolean toggleExplosions() {
        explosionsEnabled = !explosionsEnabled;
        return explosionsEnabled;
    }

    /**
     * Toggle mobs setting for the guild
     * @return New mobs state after toggle
     */
    public boolean toggleMobs() {
        mobsEnabled = !mobsEnabled;
        return mobsEnabled;
    }

    /**
     * Toggle public setting for the guild
     * @return New public state after toggle
     */
    public boolean togglePublic() {
        publicEnabled = !publicEnabled;
        return publicEnabled;
    }

    /**
     * Get all guild toggle states
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
     * Copy toggle values from another GuildToggles instance
     * @param other GuildToggles to copy from
     */
    public void copyFrom(GuildToggles other) {
        if (other != null) {
            this.pvpEnabled = other.pvpEnabled;
            this.fireEnabled = other.fireEnabled;
            this.explosionsEnabled = other.explosionsEnabled;
            this.mobsEnabled = other.mobsEnabled;
            this.publicEnabled = other.publicEnabled;
        }
    }

    /**
     * Create a copy of this GuildToggles
     * @return New GuildToggles instance with same values
     */
    public GuildToggles copy() {
        return new GuildToggles(pvpEnabled, fireEnabled, explosionsEnabled, mobsEnabled, publicEnabled);
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "GuildToggles{" +
                "pvp=" + pvpEnabled +
                ", fire=" + fireEnabled +
                ", explosions=" + explosionsEnabled +
                ", mobs=" + mobsEnabled +
                ", public=" + publicEnabled +
                '}';
    }
}

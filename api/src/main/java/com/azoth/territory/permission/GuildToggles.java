package com.azoth.territory.permission;

/**
 * Guild toggle state controlling environmental and PvP behavior on governed territory.
 *
 * @param pvpEnabled whether PvP is allowed between members
 * @param fireEnabled whether fire may burn, spread, or ignite
 * @param explosionsEnabled whether explosions may damage governed land
 * @param mobsEnabled whether natural or hostile mobs may spawn
 * @param publicEnabled whether outsiders may access governed land
 */
public record GuildToggles(
        boolean pvpEnabled,
        boolean fireEnabled,
        boolean explosionsEnabled,
        boolean mobsEnabled,
        boolean publicEnabled
) {
    /** Returns the default toggle state for a new guild.
     *
     * @return the default toggle state
     */
    public static GuildToggles defaults() {
        // Mirrors the guilds subsystem's new-guild defaults.
        return new GuildToggles(false, false, false, true, false);
    }
}

package com.azoth.territory.permission;

/**
 * Guild (guild) toggle state, mirrored from the guilds subsystem's guild toggles.
 * These drive environmental/PvP behavior on guild-governed territory:
 * <ul>
 *   <li>{@code pvpEnabled} — PvP allowed between members on governed land</li>
 *   <li>{@code fireEnabled} — fire may burn/spread/ignite on governed land</li>
 *   <li>{@code explosionsEnabled} — explosions may damage governed land</li>
 *   <li>{@code mobsEnabled} — natural/hostile mob spawning on governed land</li>
 *   <li>{@code publicEnabled} — outsiders may access (build/interact, not break) governed land</li>
 * </ul>
 * Pure domain — no Bukkit.
 */
public record GuildToggles(
        boolean pvpEnabled,
        boolean fireEnabled,
        boolean explosionsEnabled,
        boolean mobsEnabled,
        boolean publicEnabled
) {
    public static GuildToggles defaults() {
        // Mirrors the guilds subsystem's new-guild defaults.
        return new GuildToggles(false, false, false, true, false);
    }
}

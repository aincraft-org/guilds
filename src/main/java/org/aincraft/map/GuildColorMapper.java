package org.aincraft.map;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.UUID;

/**
 * Assigns consistent colors to guilds using hash-based algorithm with LRU caching.
 * Each guild always gets the same color, distributed evenly across the color pool.
 */
public class GuildColorMapper implements ColorMapper {
    private static final List<String> COLOR_POOL = List.of(
        "aqua", "blue", "gold", "green", "light_purple",
        "red", "yellow", "dark_aqua", "dark_blue", "dark_green",
        "dark_purple", "dark_red"
    );

    private final Map<UUID, String> guildIdToColor = new LinkedHashMap<UUID, String>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
            return size() > 100;
        }
    };

    @Override
    public String getColorForGuild(UUID guildId, String guildColor) {
        // If guild has a configured color, use it
        if (guildColor != null && !guildColor.isEmpty()) {
            return guildColor;
        }
        // Otherwise, generate a deterministic color
        return getGeneratedColor(guildId);
    }

    @Override
    public String getGeneratedColor(UUID guildId) {
        return guildIdToColor.computeIfAbsent(guildId, this::assignColor);
    }

    @Override
    public void clearCache(UUID guildId) {
        guildIdToColor.remove(guildId);
    }

    /**
     * Gets the color for a guild. Uses LRU cache for previously assigned colors.
     * @deprecated Use {@link #getColorForGuild(String, String)} instead
     *
     * @param guildId the guild ID
     * @return the color name (e.g., "red", "green")
     */
    @Deprecated
    public String getColorForGuild(UUID guildId) {
        return getGeneratedColor(guildId);
    }

    /**
     * Assigns a color to a guild using hash-based distribution.
     * Ensures same guild always gets same color, distributed evenly across pool.
     *
     * @param guildId the guild ID
     * @return the assigned color name
     */
    private String assignColor(UUID guildId) {
        int hash = Math.abs(guildId.hashCode());
        int index = hash % COLOR_POOL.size();
        return COLOR_POOL.get(index);
    }
}

package org.aincraft.map;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns consistent colors to guilds using hash-based algorithm with LRU caching.
 * Each guild always gets the same color, distributed evenly across the color pool.
 */
public class GuildColorMapper {
    private static final List<String> COLOR_POOL = List.of(
        "aqua", "blue", "gold", "green", "light_purple",
        "red", "yellow", "dark_aqua", "dark_blue", "dark_green",
        "dark_purple", "dark_red"
    );

    private final Map<String, String> guildIdToColor = new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 100;
        }
    };

    /**
     * Gets the color for a guild. Uses LRU cache for previously assigned colors.
     *
     * @param guildId the guild ID
     * @return the color name (e.g., "red", "green")
     */
    public String getColorForGuild(String guildId) {
        return guildIdToColor.computeIfAbsent(guildId, this::assignColor);
    }

    /**
     * Assigns a color to a guild using hash-based distribution.
     * Ensures same guild always gets same color, distributed evenly across pool.
     *
     * @param guildId the guild ID
     * @return the assigned color name
     */
    private String assignColor(String guildId) {
        int hash = Math.abs(guildId.hashCode());
        int index = hash % COLOR_POOL.size();
        return COLOR_POOL.get(index);
    }
}

package org.aincraft.map;

import java.util.UUID;

/**
 * Interface for mapping guilds to display colors.
 * Abstracts the color assignment logic for testability and extensibility.
 */
public interface ColorMapper {

    /**
     * Gets the display color for a guild.
     *
     * @param guildId the guild ID
     * @param guildColor the guild's configured color (may be null)
     * @return the color to use for display (hex format like "#RRGGBB")
     */
    String getColorForGuild(UUID guildId, String guildColor);

    /**
     * Gets the display color for a guild using only the guild ID.
     * Uses a deterministic algorithm to generate a color.
     *
     * @param guildId the guild ID
     * @return the generated color (hex format like "#RRGGBB")
     */
    String getGeneratedColor(UUID guildId);

    /**
     * Clears any cached colors for a guild.
     *
     * @param guildId the guild ID
     */
    void clearCache(UUID guildId);
}

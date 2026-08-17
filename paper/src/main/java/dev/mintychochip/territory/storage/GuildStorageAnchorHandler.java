package dev.mintychochip.territory.storage;

import org.bukkit.entity.Player;

/** Opens the virtual guild item bank at a storage anchor. */
public interface GuildStorageAnchorHandler {
    /**
     * Opens storage at a block coordinate.
     *
     * @param player viewer
     * @param worldId world
     * @param x block X
     * @param y block Y
     * @param z block Z
     */
    void open(Player player, String worldId, int x, int y, int z);
}

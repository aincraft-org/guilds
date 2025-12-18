package org.aincraft.skilltree.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for skill tree GUI events.
 * Ensures player inventories are properly restored when GUI is closed.
 */
@Singleton
public class SkillTreeGUIListener implements Listener {

    private final GuildsPlugin plugin;
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();

    @Inject
    public SkillTreeGUIListener(GuildsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    /**
     * Registers this listener with the Bukkit event system.
     */
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Saves a player's inventory before opening the skill tree GUI.
     *
     * @param player the player
     * @param inventory the player's inventory contents to save
     */
    public void saveInventory(Player player, ItemStack[] inventory) {
        Objects.requireNonNull(player, "Player cannot be null");
        savedInventories.put(player.getUniqueId(), inventory);
    }

    /**
     * Restores a player's inventory after closing the skill tree GUI.
     *
     * @param player the player
     */
    public void restoreInventory(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        ItemStack[] saved = savedInventories.remove(player.getUniqueId());
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
    }

    /**
     * Checks if a player has a saved inventory (indicating GUI is open).
     *
     * @param player the player
     * @return true if player has skill tree GUI open
     */
    public boolean hasOpenGUI(Player player) {
        return savedInventories.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Restore inventory on quit to prevent item loss
        Player player = event.getPlayer();
        ItemStack[] saved = savedInventories.remove(player.getUniqueId());
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
    }

    /**
     * Cleanup method for plugin disable.
     * Restores all saved inventories.
     */
    public void cleanup() {
        for (Map.Entry<UUID, ItemStack[]> entry : savedInventories.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.getInventory().setContents(entry.getValue());
            }
        }
        savedInventories.clear();
    }
}

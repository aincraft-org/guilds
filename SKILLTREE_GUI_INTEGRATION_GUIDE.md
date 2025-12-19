# Skill Tree GUI Integration Guide

## Overview
This guide explains how to integrate the refactored SkillTreeGUI with SkillTreeGUIListener to handle inventory control button clicks.

## Current SkillTreeGUIListener Behavior

The existing `SkillTreeGUIListener` handles:
1. Saving player inventories before GUI opens
2. Restoring inventories when GUI closes
3. Cleanup on player quit

## Required Changes

### 1. Store Open GUI Instances

The listener needs to maintain a map of open GUIs by player UUID:

```java
@Singleton
public class SkillTreeGUIListener implements Listener {

    private final GuildsPlugin plugin;
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();

    // NEW: Track open GUI instances by player UUID
    private final Map<UUID, SkillTreeGUI> openGUIs = new ConcurrentHashMap<>();

    // ... existing code ...

    /**
     * Registers a skill tree GUI as open for a player.
     *
     * @param player the player opening the GUI
     * @param gui the GUI instance
     */
    public void registerOpenGUI(Player player, SkillTreeGUI gui) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(gui, "GUI cannot be null");
        openGUIs.put(player.getUniqueId(), gui);
    }

    /**
     * Gets an open GUI instance for a player.
     *
     * @param player the player
     * @return the open GUI or null if not open
     */
    public SkillTreeGUI getOpenGUI(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        return openGUIs.get(player.getUniqueId());
    }

    /**
     * Closes a player's skill tree GUI.
     *
     * @param player the player
     */
    public void closeGUI(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        openGUIs.remove(player.getUniqueId());
    }
}
```

### 2. Handle Inventory Clicks

Add a new event handler to intercept inventory clicks:

```java
@EventHandler(priority = EventPriority.HIGH)
public void onInventoryClick(InventoryClickEvent event) {
    Player player = (Player) event.getWhoClicked();

    // Check if player has a skill tree GUI open
    SkillTreeGUI gui = getOpenGUI(player);
    if (gui == null) {
        return;  // Not our concern
    }

    // Only intercept clicks in the player's inventory (bottom 36 slots)
    // The top inventory (chest) clicks are handled by triumph-gui
    if (event.getClickedInventory() == player.getInventory()) {
        event.setCancelled(true);  // Prevent item movement

        int slot = event.getSlot();

        // Handle control button slots
        if (slot == 27 || slot == 28 || slot == 35) {
            gui.handleInventoryClick(slot);
        }
    }
}
```

### 3. Update open() Method Usage

When opening a SkillTreeGUI, register it with the listener:

```java
// Example in a command or elsewhere:
Guild guild = // ... get guild
Player player = // ... get player
SkillTreeService skillTreeService = // ... inject
SkillTreeRegistry registry = // ... inject
SkillTreeGUIListener listener = // ... inject

// Create and open GUI
SkillTreeGUI gui = new SkillTreeGUI(guild, player, skillTreeService, registry);
listener.registerOpenGUI(player, gui);
gui.open();
```

### 4. Update closeGUI on Inventory Close

Modify the existing listener to unregister GUIs when closing:

```java
// Update restoreInventory method to also unregister GUI
public void restoreInventory(Player player) {
    Objects.requireNonNull(player, "Player cannot be null");

    // Remove from open GUIs
    openGUIs.remove(player.getUniqueId());

    // Restore inventory
    ItemStack[] saved = savedInventories.remove(player.getUniqueId());
    if (saved != null) {
        player.getInventory().setContents(saved);
    }
}
```

### 5. Update Cleanup on Player Quit

Ensure GUI cleanup on quit:

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onPlayerQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    UUID uuid = player.getUniqueId();

    // Remove open GUI
    openGUIs.remove(uuid);

    // Restore inventory if it was saved
    ItemStack[] saved = savedInventories.remove(uuid);
    if (saved != null) {
        player.getInventory().setContents(saved);
    }
}
```

### 6. Update Cleanup Method

Update the cleanup method for plugin disable:

```java
public void cleanup() {
    // Clear open GUIs
    openGUIs.clear();

    // Restore all saved inventories
    for (Map.Entry<UUID, ItemStack[]> entry : savedInventories.entrySet()) {
        Player player = plugin.getServer().getPlayer(entry.getKey());
        if (player != null && player.isOnline()) {
            player.getInventory().setContents(entry.getValue());
        }
    }
    savedInventories.clear();
}
```

## Complete Updated SkillTreeGUIListener

Here's the complete updated listener class:

```java
package org.aincraft.skilltree.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
 * Handles control button clicks in player inventory.
 */
@Singleton
public class SkillTreeGUIListener implements Listener {

    private final GuildsPlugin plugin;
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, SkillTreeGUI> openGUIs = new ConcurrentHashMap<>();

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
     * Registers a skill tree GUI as open for a player.
     *
     * @param player the player opening the GUI
     * @param gui the GUI instance
     */
    public void registerOpenGUI(Player player, SkillTreeGUI gui) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(gui, "GUI cannot be null");
        openGUIs.put(player.getUniqueId(), gui);
    }

    /**
     * Gets an open GUI instance for a player.
     *
     * @param player the player
     * @return the open GUI or null if not open
     */
    public SkillTreeGUI getOpenGUI(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        return openGUIs.get(player.getUniqueId());
    }

    /**
     * Closes a player's skill tree GUI.
     *
     * @param player the player
     */
    public void closeGUI(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");
        openGUIs.remove(player.getUniqueId());
    }

    /**
     * Checks if a player has a saved inventory (indicating GUI is open).
     *
     * @param player the player
     * @return true if player has skill tree GUI open
     */
    public boolean hasOpenGUI(Player player) {
        return openGUIs.containsKey(player.getUniqueId());
    }

    /**
     * Restores a player's inventory after closing the skill tree GUI.
     *
     * @param player the player
     */
    public void restoreInventory(Player player) {
        Objects.requireNonNull(player, "Player cannot be null");

        // Remove from open GUIs
        openGUIs.remove(player.getUniqueId());

        // Restore inventory
        ItemStack[] saved = savedInventories.remove(player.getUniqueId());
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
    }

    /**
     * Handles clicks on inventory items during skill tree GUI interaction.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // Check if player has a skill tree GUI open
        SkillTreeGUI gui = getOpenGUI(player);
        if (gui == null) {
            return;  // Not our concern
        }

        // Only intercept clicks in the player's inventory (bottom 36 slots)
        // The top inventory (chest) clicks are handled by triumph-gui
        if (event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);  // Prevent item movement

            int slot = event.getSlot();

            // Handle control button slots
            if (slot == 27 || slot == 28 || slot == 35) {
                gui.handleInventoryClick(slot);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Remove open GUI
        openGUIs.remove(uuid);

        // Restore inventory on quit to prevent item loss
        ItemStack[] saved = savedInventories.remove(uuid);
        if (saved != null) {
            player.getInventory().setContents(saved);
        }
    }

    /**
     * Cleanup method for plugin disable.
     * Restores all saved inventories and clears open GUIs.
     */
    public void cleanup() {
        // Clear open GUIs
        openGUIs.clear();

        // Restore all saved inventories
        for (Map.Entry<UUID, ItemStack[]> entry : savedInventories.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                player.getInventory().setContents(entry.getValue());
            }
        }
        savedInventories.clear();
    }
}
```

## Integration Checklist

- [ ] Add `openGUIs` map to SkillTreeGUIListener
- [ ] Add `registerOpenGUI()` method
- [ ] Add `getOpenGUI()` method
- [ ] Add `closeGUI()` method
- [ ] Add `onInventoryClick()` event handler
- [ ] Update `restoreInventory()` to clear openGUIs
- [ ] Update `onPlayerQuit()` to clear openGUIs
- [ ] Update `cleanup()` to clear openGUIs
- [ ] Call `registerOpenGUI()` when opening GUI
- [ ] Test opening GUI
- [ ] Test scroll buttons
- [ ] Test close button
- [ ] Test inventory restoration

## Example Usage

### Opening the GUI (in a command handler)

```java
@Override
public void execute(CommandSender sender, String[] args) {
    Player player = (Player) sender;
    Guild guild = guildService.getPlayerGuild(player.getUniqueId()).orElse(null);

    if (guild == null) {
        player.sendMessage(Component.text("You are not in a guild!")
                .color(NamedTextColor.RED));
        return;
    }

    // Create and open GUI
    SkillTreeGUI gui = new SkillTreeGUI(
            guild,
            player,
            skillTreeService,
            skillTreeRegistry
    );

    // Register with listener
    skillTreeGUIListener.registerOpenGUI(player, gui);

    // Open for player
    gui.open();
}
```

### Dependency Injection (in GuildsModule)

```java
@Provides
@Singleton
public SkillTreeGUI provideSkillTreeGUI(
        Guild guild,
        Player viewer,
        SkillTreeService skillTreeService,
        SkillTreeRegistry registry) {
    return new SkillTreeGUI(guild, viewer, skillTreeService, registry);
}
```

## Event Flow Diagram

```
Player opens GUI
    ↓
Command handler
    ├─ Create SkillTreeGUI
    ├─ listener.registerOpenGUI(player, gui)
    └─ gui.open()
        ├─ encodePlayerInventory()
        ├─ buildGUI()
        └─ gui.open(player)
    ↓
Player clicks button in inventory
    ↓
InventoryClickEvent
    ↓
onInventoryClick()
    ├─ Get GUI from openGUIs map
    ├─ Check if click is in player inventory
    ├─ Check if slot is 27, 28, or 35
    └─ gui.handleInventoryClick(slot)
        ├─ Perform action (scroll or close)
        ├─ encodePlayerInventory() [update buttons]
        └─ render() [update display]
    ↓
Player closes GUI
    ↓
InventoryCloseEvent (triggered by closeInventory())
    ↓
gui.setCloseGuiAction()
    ↓
restorePlayerInventory()
    ├─ Remove from openGUIs
    └─ Restore original inventory
```

## Testing Checklist

1. **Opening GUI**
   - [ ] No errors on open
   - [ ] GUI displays with correct title
   - [ ] All 54 skill slots visible
   - [ ] Inventory shows controls

2. **Scrolling**
   - [ ] Scroll Up button works when scrollOffset > 0
   - [ ] Scroll Down button works when scrollOffset < maxTier
   - [ ] Buttons disable at bounds
   - [ ] Button colors change appropriately
   - [ ] Sound plays on scroll

3. **Control Buttons**
   - [ ] SP Info displays correct values
   - [ ] Close button closes GUI
   - [ ] Click in other inventory slots doesn't interfere

4. **Inventory Restoration**
   - [ ] Original inventory restored on close
   - [ ] Inventory correct after quit mid-GUI
   - [ ] No item loss

5. **Cross-Browser**
   - [ ] Works with 3 branches
   - [ ] Works with 1 branch
   - [ ] Works with empty registry

## Summary

The integration is straightforward:
1. SkillTreeGUIListener tracks open GUIs
2. onInventoryClick() intercepts control button clicks
3. gui.handleInventoryClick() performs the action
4. Inventory is properly restored when GUI closes

No changes needed to SkillTreeGUI itself - it's complete and ready to use.

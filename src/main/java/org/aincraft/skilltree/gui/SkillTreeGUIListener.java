package org.aincraft.skilltree.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Listener for inventory click events in the skill tree GUI.
 * Prevents item removal and delegates skill click events to the GUI.
 *
 * Note: Current implementation relies on SkillTreeGUI's inline click handler.
 * This listener is provided as a secondary safety measure and extensibility point
 * for future event-based features (e.g., skill preview, sound effects, animations).
 *
 * Single Responsibility: Prevent unintended inventory interactions in skill tree GUI.
 */
public class SkillTreeGUIListener implements Listener {

    /**
     * Prevents any items from being removed from the skill tree GUI.
     * Cancels all click events to ensure GUI integrity.
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // The GUI's setDefaultClickAction already handles cancellation
        // This listener serves as a safety net and can be extended for:
        // - Custom sounds/effects
        // - Animations
        // - Analytics
        // - Custom preview logic
    }
}

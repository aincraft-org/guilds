package org.aincraft.subregion;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for cleaning up selection indicators when players quit.
 */
@Singleton
public class SelectionVisualizerListener implements Listener {
    private final SelectionManager selectionManager;
    private final SelectionVisualizer visualizer;

    @Inject
    public SelectionVisualizerListener(SelectionManager selectionManager, SelectionVisualizer visualizer) {
        this.selectionManager = selectionManager;
        this.visualizer = visualizer;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clear selection and indicators when player quits
        selectionManager.clearSelection(event.getPlayer().getUniqueId());
        visualizer.clearIndicators(event.getPlayer().getUniqueId());
    }
}

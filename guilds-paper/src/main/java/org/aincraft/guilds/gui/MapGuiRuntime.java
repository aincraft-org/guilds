package org.aincraft.guilds.gui;

import de.flog99.mapgui.MapGui;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;

/**
 * MapGUI entry points loaded only when {@link MapGuiOpener} delegates here reflectively.
 */
final class MapGuiRuntime {

    private MapGuiRuntime() {
    }

    public static void open(JavaPlugin plugin, Player player, String viewerGuild,
                            GuildService guilds, PlotService plots, PermissionService permissions) {
        MapFollowTask.start(plugin, MapGui.get());
        MapGui.get().open(player, new GuildClaimScreen(viewerGuild, guilds, plots, permissions));
        plugin.getLogger().info("MapGUI claim map opened for player: " + player.getName());
    }

    public static void stop(JavaPlugin plugin) {
        MapFollowTask.stop(plugin);
    }
}

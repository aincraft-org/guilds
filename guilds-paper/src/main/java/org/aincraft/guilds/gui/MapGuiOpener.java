package org.aincraft.guilds.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Lazy boundary for optional MapGUI integration. Callers outside this package must not
 * reference MapGUI types directly.
 */
public final class MapGuiOpener {

  private static final String RUNTIME_CLASS = "org.aincraft.guilds.gui.MapGuiRuntime";

  public enum OpenResult {
    OPENED,
    FAILED,
    NOT_AVAILABLE
  }

  private final JavaPlugin plugin;
  private final GuildService guildService;
  private final PlotService plotService;
  private final PermissionService permissionService;

  public MapGuiOpener(JavaPlugin plugin, GuildService guildService, PlotService plotService,
                      PermissionService permissionService) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.guildService = Objects.requireNonNull(guildService, "guildService");
    this.plotService = Objects.requireNonNull(plotService, "plotService");
    this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
  }

  public boolean isAvailable() {
    return plugin.getServer().getPluginManager().isPluginEnabled("MapGUI");
  }

  public OpenResult open(Player player, String viewerGuild) {
    Objects.requireNonNull(player, "player");
    if (!isAvailable()) {
      return OpenResult.NOT_AVAILABLE;
    }
    try {
      Class<?> runtime = Class.forName(RUNTIME_CLASS);
      runtime.getMethod("open", JavaPlugin.class, Player.class, String.class,
              GuildService.class, PlotService.class, PermissionService.class)
          .invoke(null, plugin, player, viewerGuild, guildService, plotService, permissionService);
      return OpenResult.OPENED;
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      plugin.getLogger().warning("MapGUI runtime unavailable: " + e.getMessage());
      return OpenResult.NOT_AVAILABLE;
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      player.sendMessage(Component.text("Failed to open map: ", NamedTextColor.RED)
          .append(Component.text(cause.getMessage(), NamedTextColor.RED)));
      plugin.getLogger().warning("Failed to open MapGUI map for " + player.getName() + ": " + cause.getMessage());
      return OpenResult.FAILED;
    } catch (ReflectiveOperationException e) {
      player.sendMessage(Component.text("Failed to open map: ", NamedTextColor.RED)
          .append(Component.text(e.getMessage(), NamedTextColor.RED)));
      plugin.getLogger().warning("Failed to open MapGUI map for " + player.getName() + ": " + e.getMessage());
      return OpenResult.FAILED;
    }
  }

  public static void stopIfPresent(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    if (!plugin.getServer().getPluginManager().isPluginEnabled("MapGUI")) {
      return;
    }
    try {
      Class<?> runtime = Class.forName(RUNTIME_CLASS);
      runtime.getMethod("stop", JavaPlugin.class).invoke(null, plugin);
    } catch (ReflectiveOperationException e) {
      plugin.getLogger().warning("Failed to stop MapGUI follow task: " + e.getMessage());
    }
  }
}

package com.azoth.territory.command;

import com.azoth.territory.AzothTerritoryPlugin;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Minimal admin/smoke command: lookup, list, reload.
 */
public final class TerritoryCommand implements CommandExecutor, TabCompleter {
    private final AzothTerritoryPlugin plugin;

    public TerritoryCommand(AzothTerritoryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return lookupHere(sender);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "lookup", "here" -> lookupHere(sender);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            case "save" -> save(sender);
            case "web" -> webStatus(sender);
            default -> {
                sender.sendMessage(Component.text(
                        "Usage: /" + label + " [lookup|list|reload|save|web]", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean lookupHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only for location lookup.", NamedTextColor.RED));
            return true;
        }
        Location loc = player.getLocation();
        String worldId = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        LookupResult result = plugin.getRegistry().resolve(worldId, loc.getBlockX(), loc.getBlockZ());
        if (!result.isContained()) {
            sender.sendMessage(Component.text(
                    "No territory at " + loc.getBlockX() + ", " + loc.getBlockZ() + " in " + worldId,
                    NamedTextColor.GRAY));
            return true;
        }
        Territory t = result.territory().orElseThrow();
        ZoneType type = result.zoneType().orElse(ZoneType.WILDERNESS);
        Territory.ZoneResolution z = result.zone().orElseThrow();
        sender.sendMessage(Component.text("Territory: ", NamedTextColor.GOLD)
                .append(Component.text(t.name(), NamedTextColor.WHITE))
                .append(Component.text(" (" + t.id() + ")", NamedTextColor.DARK_GRAY)));
        String zoneExtra = z.isDefault() ? " [default]" : " [" + z.zoneId() + "]";
        sender.sendMessage(Component.text("Zone: ", NamedTextColor.GOLD)
                .append(Component.text(type.name(), NamedTextColor.WHITE))
                .append(Component.text(zoneExtra, NamedTextColor.DARK_GRAY)));
        return true;
    }

    private boolean list(CommandSender sender) {
        List<Territory> all = plugin.getRegistry().list();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("No territories registered.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("Territories (" + all.size() + "):", NamedTextColor.GOLD));
        for (Territory t : all) {
            sender.sendMessage(Component.text(" • " + t.id(), NamedTextColor.YELLOW)
                    .append(Component.text(
                            " world=" + t.worldId() + " zones=" + t.zones().size(),
                            NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        try {
            plugin.reloadTerritories();
            sender.sendMessage(Component.text(
                    "Reloaded " + plugin.getRegistry().size() + " territor(y/ies).",
                    NamedTextColor.GREEN));
        } catch (IOException e) {
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Reload failed: " + e.getMessage());
        }
        return true;
    }

    private boolean save(CommandSender sender) {
        try {
            plugin.saveTerritories();
            sender.sendMessage(Component.text(
                    "Saved " + plugin.getRegistry().size() + " territor(y/ies).",
                    NamedTextColor.GREEN));
        } catch (IOException e) {
            sender.sendMessage(Component.text("Save failed: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean webStatus(CommandSender sender) {
        var web = plugin.getWebServer();
        var cfg = plugin.getWebConfig();
        if (cfg == null || !cfg.enabled()) {
            sender.sendMessage(Component.text("Web submodule disabled.", NamedTextColor.GRAY));
            return true;
        }
        if (web == null || !web.isRunning()) {
            sender.sendMessage(Component.text("Web submodule not running.", NamedTextColor.RED));
            return true;
        }
        String scheme = cfg.https() ? "https" : "http";
        sender.sendMessage(Component.text(
                "Web: " + scheme + "://" + cfg.bindHost() + ":" + cfg.port()
                        + (cfg.trustProxy() ? " (trust-proxy)" : "")
                        + (cfg.publicBaseUrl().isEmpty() ? "" : " public=" + cfg.publicBaseUrl()),
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("lookup", "list", "reload", "save", "web").stream()
                    .filter(s -> s.startsWith(p))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }
}


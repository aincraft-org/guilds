package com.azoth.territory.command;

import com.azoth.territory.AzothTerritoryPlugin;
import com.azoth.territory.influence.DeclareResult;
import com.azoth.territory.influence.InfluenceBar;
import com.azoth.territory.influence.InfluenceEngine;
import com.azoth.territory.influence.TerritoryInfluenceState;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.permission.GuildBody;
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
import java.util.Optional;
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
            case "govern" -> govern(sender, args);
            case "influence" -> args.length > 1 && ("set".equalsIgnoreCase(args[1])
                    || "reset".equalsIgnoreCase(args[1]))
                    ? influenceAdmin(sender, args)
                    : influence(sender, args);
            case "declare" -> declare(sender, args);
            default -> {
                sender.sendMessage(Component.text(
                        "Usage: /" + label + " [lookup|list|reload|save|web|govern|influence|declare]",
                        NamedTextColor.RED));
                yield true;
            }
        };
    }

    /**
     * Bind/unbind a governing guild (guild) to a territory.
     * <pre>
     * /territory govern &lt;territoryId&gt; &lt;guildId|-&gt;
     * </pre>
     * A dash ({@code -}) removes the binding, falling back to the territory's
     * local government. The guild's own governance form + role holders decide
     * sovereignty and permissions from then on.
     */
    private boolean govern(CommandSender sender, String[] args) {
        if (!sender.hasPermission("azoth.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text(
                    "You need 'azoth.territory.admin' to bind a governing guild.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /territory govern <territoryId> <townId|->", NamedTextColor.RED));
            return true;
        }
        String territoryId = args[1];
        String guildId = args[2];
        var registry = plugin.getRegistry();
        Optional<Territory> existing = registry.get(territoryId);
        if (existing.isEmpty()) {
            sender.sendMessage(Component.text("Unknown territory: " + territoryId, NamedTextColor.RED));
            return true;
        }
        Territory next;
        if ("-".equals(guildId)) {
            next = existing.get().withoutGoverningGuild();
            sender.sendMessage(Component.text(
                    "Cleared governing guild for " + territoryId + ".", NamedTextColor.GREEN));
        } else {
            var guilds = plugin.getGuilds();
            if (guilds == null) {
                sender.sendMessage(Component.text(
                        "Guilds subsystem unavailable — cannot bind a governing guild.", NamedTextColor.RED));
                return true;
            }
            boolean guildExists = guilds.getGovernanceSource().guild(guildId).isPresent();
            if (!guildExists) {
                sender.sendMessage(Component.text("Unknown town (guild): " + guildId, NamedTextColor.RED));
                return true;
            }
            next = existing.get().withGoverningGuild(guildId);
            sender.sendMessage(Component.text(
                    "Territory " + territoryId + " is now governed by guild " + guildId + ".", NamedTextColor.GREEN));
        }
        registry.register(next);
        try {
            plugin.saveTerritories();
        } catch (IOException e) {
            sender.sendMessage(Component.text(
                    "Saved binding in memory; persist failed: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
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

    private InfluenceEngine engine() {
        InfluenceEngine engine = plugin.getInfluenceEngine();
        return engine == null ? null : engine;
    }

    /**
     * /territory influence [territoryId] — show the influence race state.
     */
    private boolean influence(CommandSender sender, String[] args) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        String territoryId = args.length > 1 ? args[1] : territoryAt(sender);
        if (territoryId == null) {
            sender.sendMessage(Component.text(
                    "Usage: /territory influence <territoryId>", NamedTextColor.RED));
            return true;
        }
        Optional<TerritoryInfluenceState> state = engine.influence(territoryId);
        if (state.isEmpty()) {
            sender.sendMessage(Component.text(
                    "No influence recorded for " + territoryId + ".", NamedTextColor.GRAY));
            return true;
        }
        TerritoryInfluenceState s = state.get();
        sender.sendMessage(Component.text("Influence — ", NamedTextColor.GOLD)
                .append(Component.text(s.territoryId(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Owner: ", NamedTextColor.GOLD)
                .append(Component.text(plugin.resolveGuildNameFor(s.ownerGuildId()), NamedTextColor.WHITE)));
        long now = System.currentTimeMillis();
        if (s.cooldownUntilEpochMs() > now) {
            long hours = (s.cooldownUntilEpochMs() - now) / 3_600_000L;
            sender.sendMessage(Component.text("Cooldown: ", NamedTextColor.RED)
                    .append(Component.text(hours + "h until a new race may start", NamedTextColor.WHITE)));
        }
        for (InfluenceBar bar : s.bars()) {
            boolean declarable = engine.isDeclarable(s.territoryId(), bar.guildId(), now);
            sender.sendMessage(Component.text(" • ", NamedTextColor.YELLOW)
                    .append(Component.text(plugin.resolveGuildNameFor(bar.guildId()), NamedTextColor.WHITE))
                    .append(Component.text(" " + bar.value() + "/" + engine.cap(), NamedTextColor.GRAY))
                    .append(declarable
                            ? Component.text(" [DECLARABLE]", NamedTextColor.GOLD)
                            : Component.empty()));
        }
        if (s.declaration() != null) {
            long remaining = Math.max(0, s.declaration().flipAtEpochMs() - now);
            sender.sendMessage(Component.text("Declaration by ", NamedTextColor.GOLD)
                    .append(Component.text(plugin.resolveGuildNameFor(s.declaration().guildId()), NamedTextColor.WHITE))
                    .append(Component.text(" — flips in " + (remaining / 3_600_000L) + "h", NamedTextColor.GRAY)));
        }
        return true;
    }

    /**
     * /territory declare <territoryId> [confirm] | cancel <territoryId>
     */
    private boolean declare(CommandSender sender, String[] args) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /territory declare <territoryId> [confirm] | cancel <territoryId>",
                    NamedTextColor.RED));
            return true;
        }
        if ("cancel".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage(Component.text(
                        "Usage: /territory declare cancel <territoryId>", NamedTextColor.RED));
                return true;
            }
            Optional<String> cancelGuild = plugin.getGovernance().primaryGuildForMember(
                    player.getUniqueId().toString()).map(GuildBody::id);
            if (cancelGuild.isEmpty()) {
                sender.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
                return true;
            }
            DeclareResult result = engine.cancelDeclaration(args[2], cancelGuild.get(),
                    player.getUniqueId().toString(), System.currentTimeMillis());
            sender.sendMessage(Component.text(result.message(),
                    result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED));
            return true;
        }
        if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
            sender.sendMessage(Component.text(
                    "Declaring a takeover is permanent. Confirm with: /territory declare "
                            + args[1] + " confirm", NamedTextColor.YELLOW));
            return true;
        }
        Optional<String> guildId = plugin.getGovernance().primaryGuildForMember(
                player.getUniqueId().toString()).map(GuildBody::id);
        if (guildId.isEmpty()) {
            sender.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
            return true;
        }
        DeclareResult result = engine.declare(args[1], guildId.get(),
                player.getUniqueId().toString(), System.currentTimeMillis());
        sender.sendMessage(Component.text(result.message(),
                result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    /** /territory influence set <territoryId> <guildId> <value> | reset <territoryId> */
    private boolean influenceAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("azoth.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("You need 'azoth.territory.admin'.", NamedTextColor.RED));
            return true;
        }
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: /territory influence reset <territoryId>", NamedTextColor.RED));
                return true;
            }
            boolean removed = engine.adminReset(args[2]);
            sender.sendMessage(Component.text(removed
                    ? "Influence state dropped for " + args[2] + "."
                    : "No influence state for " + args[2] + ".", NamedTextColor.GREEN));
            return true;
        }
        if (args.length < 5 || !"set".equalsIgnoreCase(args[1])) {
            sender.sendMessage(Component.text(
                    "Usage: /territory influence set <territoryId> <guildId> <value> | reset <territoryId>",
                    NamedTextColor.RED));
            return true;
        }
        try {
            double value = Double.parseDouble(args[4]);
            boolean ok = engine.adminSet(args[2], args[3], value, System.currentTimeMillis());
            sender.sendMessage(Component.text(ok
                    ? "Set influence of " + args[3] + " on " + args[2] + " to " + value + "."
                    : "Unknown territory or guild.", NamedTextColor.GREEN));
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Value must be a number.", NamedTextColor.RED));
            return true;
        }
    }

    private String territoryAt(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            return null;
        }
        LookupResult result = plugin.getRegistry().resolve(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
        return result.isContained() ? result.territoryId().orElse(null) : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("lookup", "list", "reload", "save", "web", "govern",
                            "influence", "declare").stream()
                    .filter(s -> s.startsWith(p))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }
}


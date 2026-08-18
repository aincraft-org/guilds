package dev.mintychochip.guilds.commands.brigadier;

import dev.mintychochip.guilds.GuildsPlugin;
import dev.mintychochip.territory.invasion.InvasionRuntime;
import dev.mintychochip.territory.invasion.InvasionStartResult;
import dev.mintychochip.territory.invasion.InvasionState;
import dev.mintychochip.territory.influence.DeclareResult;
import dev.mintychochip.territory.influence.InfluenceBar;
import dev.mintychochip.territory.influence.InfluenceEngine;
import dev.mintychochip.territory.influence.TerritoryInfluenceState;
import dev.mintychochip.territory.model.LookupResult;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.model.ZoneType;
import dev.mintychochip.guilds.Guild;
import dev.mintychochip.territory.standing.StandingBar;
import dev.mintychochip.territory.standing.StandingEngine;
import dev.mintychochip.territory.standing.StandingTier;
import dev.mintychochip.territory.standing.TerritoryStandingState;
import dev.mintychochip.territory.upkeep.UpkeepEngine;
import dev.mintychochip.territory.upkeep.UpkeepState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Territory command behavior invoked by {@link TerritoryBrigadierCommand}.
 */
public final class TerritoryCommand {
    /** The plugin. */
    private final GuildsPlugin plugin;

    /**
     * Creates a new territory command instance.
     * @param plugin the plugin
     */
    public TerritoryCommand(GuildsPlugin plugin) {
        this.plugin = plugin;
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
    boolean govern(CommandSender sender, String territoryId, String guildId) {
        if (!sender.hasPermission("guilds.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text(
                    "You need 'guilds.territory.admin' to bind a governing guild.", NamedTextColor.RED));
            return true;
        }
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
                sender.sendMessage(Component.text("Unknown guild (guild): " + guildId, NamedTextColor.RED));
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

    /**
     * Looks up the here.
     * @param sender the sender
     * @return the result
     */
    boolean lookupHere(CommandSender sender) {
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

    /**
     * Performs the list operation.
     * @param sender the sender
     * @return the result
     */
    boolean list(CommandSender sender) {
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

    /**
     * Performs the reload operation.
     * @param sender the sender
     * @return the result
     */
    boolean reload(CommandSender sender) {
        if (!admin(sender)) return true;
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

    /**
     * saves the data.
     * @param sender the sender
     * @return the result
     */
    boolean save(CommandSender sender) {
        if (!admin(sender)) return true;
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

    /**
     * Performs the web status operation.
     * @param sender the sender
     * @return the result
     */
    boolean webStatus(CommandSender sender) {
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


    /** /territory upkeep [territoryId] — show the latest durable upkeep state. */
    boolean upkeep(CommandSender sender, String territoryIdOrNull) {
        UpkeepEngine engine = plugin.getUpkeepEngine();
        if (engine == null) {
            sender.sendMessage(Component.text("Territory upkeep is disabled.", NamedTextColor.RED));
            return true;
        }
        String territoryId = territoryIdOrNull != null && !territoryIdOrNull.isBlank()
                ? territoryIdOrNull : territoryAt(sender);
        if (territoryId == null) {
            sender.sendMessage(Component.text(
                    "Usage: /territory upkeep <territoryId>", NamedTextColor.RED));
            return true;
        }
        UpkeepState state = engine.state(territoryId).orElse(null);
        if (state == null) {
            sender.sendMessage(Component.text(
                    "No upkeep state for '" + territoryId + "'.", NamedTextColor.YELLOW));
            return true;
        }
        String grace = state.graceDeadlineEpochMs() == 0L
                ? "none" : Long.toString(state.graceDeadlineEpochMs());
        String outcome = state.lastOutcome() == null ? "NONE" : state.lastOutcome().name();
        sender.sendMessage(Component.text("Upkeep — " + state.territoryId(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Amount: " + state.amount(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Status: " + state.status().name(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Next due: " + state.nextDueEpochMs(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Grace deadline: " + grace, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Last outcome: " + outcome, NamedTextColor.WHITE));
        return true;
    }
    /**
     * Performs the engine operation.
     * @return the result
     */
    private InfluenceEngine engine() {
        InfluenceEngine engine = plugin.getInfluenceEngine();
        return engine == null ? null : engine;
    }

    /**
     * /territory influence [territoryId] — show the influence race state.
     */
    boolean influence(CommandSender sender, String territoryIdOrNull) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        String territoryId = territoryIdOrNull != null ? territoryIdOrNull : territoryAt(sender);
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
     * Performs the declare prompt operation.
     * @param sender the sender
     * @param territoryId the territory id
     * @return the result
     */
    boolean declarePrompt(CommandSender sender, String territoryId) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text(
                "Declaring a takeover is permanent. Confirm with: /territory declare "
                        + territoryId + " confirm", NamedTextColor.YELLOW));
        return true;
    }

    /**
     * Performs the declare confirm operation.
     * @param sender the sender
     * @param territoryId the territory id
     * @return the result
     */
    boolean declareConfirm(CommandSender sender, String territoryId) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        Optional<String> guildId = plugin.getGovernance().primaryGuildForMember(
                player.getUniqueId().toString()).map(Guild::id);
        if (guildId.isEmpty()) {
            sender.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
            return true;
        }
        DeclareResult result = engine.declare(territoryId, guildId.get(),
                player.getUniqueId().toString(), System.currentTimeMillis());
        sender.sendMessage(Component.text(result.message(),
                result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    /**
     * Performs the declare cancel operation.
     * @param sender the sender
     * @param territoryId the territory id
     * @return the result
     */
    boolean declareCancel(CommandSender sender, String territoryId) {
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        Optional<String> cancelGuild = plugin.getGovernance().primaryGuildForMember(
                player.getUniqueId().toString()).map(Guild::id);
        if (cancelGuild.isEmpty()) {
            sender.sendMessage(Component.text("You are not in a guild.", NamedTextColor.RED));
            return true;
        }
        DeclareResult result = engine.cancelDeclaration(territoryId, cancelGuild.get(),
                player.getUniqueId().toString(), System.currentTimeMillis());
        sender.sendMessage(Component.text(result.message(),
                result.isSuccess() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    /**
     * Performs the influence reset operation.
     * @param sender the sender
     * @param territoryId the territory id
     * @return the result
     */
    boolean influenceReset(CommandSender sender, String territoryId) {
        if (!sender.hasPermission("guilds.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("You need 'guilds.territory.admin'.", NamedTextColor.RED));
            return true;
        }
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        boolean removed = engine.adminReset(territoryId);
        sender.sendMessage(Component.text(removed
                ? "Influence state dropped for " + territoryId + "."
                : "No influence state for " + territoryId + ".", NamedTextColor.GREEN));
        return true;
    }

    /**
     * Performs the influence set operation.
     * @param sender the sender
     * @param territoryId the territory id
     * @param guildId the guild id
     * @param rawValue the raw value
     * @return the result
     */
    boolean influenceSet(CommandSender sender, String territoryId, String guildId, String rawValue) {
        if (!sender.hasPermission("guilds.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("You need 'guilds.territory.admin'.", NamedTextColor.RED));
            return true;
        }
        InfluenceEngine engine = engine();
        if (engine == null) {
            sender.sendMessage(Component.text("Influence system is disabled.", NamedTextColor.RED));
            return true;
        }
        try {
            double value = Double.parseDouble(rawValue);
            boolean ok = engine.adminSet(territoryId, guildId, value, System.currentTimeMillis());
            sender.sendMessage(Component.text(ok
                    ? "Set influence of " + guildId + " on " + territoryId + " to " + value + "."
                    : "Unknown territory or guild.", NamedTextColor.GREEN));
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Value must be a number.", NamedTextColor.RED));
            return true;
        }
    }

    /**
     * Performs the territory at operation.
     * @param sender the sender
     * @return the result
     */
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

    // ── Standing ──────────────────────────────────────────────────────────

    /**
     * Performs the standing engine operation.
     * @return the result
     */
    private StandingEngine standingEngine() {
        return plugin.getStandingEngine();
    }

    /** /territory standing [territoryId] — show standing bars and tier. */
    boolean standing(CommandSender sender, String territoryIdOrNull) {
        StandingEngine engine = standingEngine();
        if (engine == null) {
            sender.sendMessage(Component.text("Standing subsystem unavailable.", NamedTextColor.RED));
            return true;
        }
        String territoryId = territoryIdOrNull != null && !territoryIdOrNull.isBlank()
                ? territoryIdOrNull : territoryAt(sender);
        if (territoryId == null) {
            sender.sendMessage(Component.text("You must stand inside a territory or name one.",
                    NamedTextColor.RED));
            return true;
        }
        Optional<TerritoryStandingState> state = engine.standing(territoryId);
        if (state.isEmpty()) {
            sender.sendMessage(Component.text("No standing for territory '" + territoryId + "'.",
                    NamedTextColor.YELLOW));
            return true;
        }
        TerritoryStandingState s = state.get();
        sender.sendMessage(Component.text("Standing for " + territoryId
                + " (owner: " + s.ownerGuildId() + "):", NamedTextColor.GOLD));
        for (StandingBar bar : s.bars()) {
            Optional<StandingTier> tier = engine.tierFor(territoryId, bar.guildId());
            sender.sendMessage(Component.text("  " + bar.guildId() + ": " + bar.value()
                    + (tier.isPresent() ? " (tier " + tier.get().level() + ")" : ""),
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    /**
     * Performs the standing reset operation.
     * @param sender the sender
     * @param territoryId the territory id
     * @return the result
     */
    boolean standingReset(CommandSender sender, String territoryId) {
        if (!sender.hasPermission("guilds.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        StandingEngine engine = standingEngine();
        if (engine == null) {
            sender.sendMessage(Component.text("Standing subsystem unavailable.", NamedTextColor.RED));
            return true;
        }
        boolean removed = engine.adminReset(territoryId);
        sender.sendMessage(Component.text(removed
                ? "Standing state dropped for " + territoryId + "."
                : "No standing state for " + territoryId + ".", NamedTextColor.GREEN));
        return true;
    }

    /**
     * Performs the standing set operation.
     * @param sender the sender
     * @param territoryId the territory id
     * @param guildId the guild id
     * @param rawValue the raw value
     * @return the result
     */
    boolean standingSet(CommandSender sender, String territoryId, String guildId, String rawValue) {
        if (!sender.hasPermission("guilds.territory.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        StandingEngine engine = standingEngine();
        if (engine == null) {
            sender.sendMessage(Component.text("Standing subsystem unavailable.", NamedTextColor.RED));
            return true;
        }
        try {
            double value = Double.parseDouble(rawValue);
            boolean ok = engine.adminSet(territoryId, guildId, value);
            sender.sendMessage(Component.text(ok
                    ? "Set standing of " + guildId + " on " + territoryId + " to " + value + "."
                    : "Unknown territory or guild.", NamedTextColor.GREEN));
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Value must be a number.", NamedTextColor.RED));
            return true;
        }
    }

    /**
     * Performs the invasion start operation.
     * @param sender the sender
     * @param guild the guild
     * @return the result
     */
    boolean invasionStart(CommandSender sender, String guild) {
        InvasionRuntime runtime = invasionRuntime(sender);
        if (runtime == null) {
            return true;
        }
        InvasionStartResult result = runtime.start(guild, System.currentTimeMillis());
        sender.sendMessage(Component.text(
                result.status() == dev.mintychochip.territory.invasion.InvasionStartStatus.STARTED
                        ? "Started invasion " + result.invasionId() + " for " + guild + "."
                        : "Could not start invasion: " + result.status() + ".",
                result.status() == dev.mintychochip.territory.invasion.InvasionStartStatus.STARTED
                        ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    /**
     * Performs the invasion stop operation.
     * @param sender the sender
     * @param guild the guild
     * @return the result
     */
    boolean invasionStop(CommandSender sender, String guild) {
        InvasionRuntime runtime = invasionRuntime(sender);
        if (runtime == null) {
            return true;
        }
        Optional<String> guildId = runtime.resolveGuildId(guild);
        if (guildId.isEmpty()) {
            sender.sendMessage(Component.text("Unknown or ineligible guild: " + guild + ".",
                    NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text(
                runtime.cancel(guildId.get(), System.currentTimeMillis())
                        ? "Stopped invasion for " + guild + "."
                        : "No active invasion for " + guild + ".",
                NamedTextColor.YELLOW));
        return true;
    }

    /**
     * Performs the invasion status operation.
     * @param sender the sender
     * @param guild the guild
     * @return the result
     */
    boolean invasionStatus(CommandSender sender, String guild) {
        InvasionRuntime runtime = invasionRuntime(sender);
        if (runtime == null) {
            return true;
        }
        Optional<String> guildId = runtime.resolveGuildId(guild);
        if (guildId.isEmpty()) {
            sender.sendMessage(Component.text("Unknown or ineligible guild: " + guild + ".",
                    NamedTextColor.RED));
            return true;
        }
        Optional<InvasionState> state = runtime.status(guildId.get());
        sender.sendMessage(state.<Component>map(value -> Component.text(
                        value.guildName() + ": " + value.status() + ", wave "
                                + (value.wave() + 1) + ", living "
                                + value.currentWaveEntities().size() + ", damage "
                                + value.damage().percent() + "% at " + value.worldId() + " "
                                + value.x() + ", " + value.y() + ", " + value.z(),
                        NamedTextColor.GOLD))
                .orElseGet(() -> Component.text("No invasion record for " + guild + ".",
                        NamedTextColor.YELLOW)));
        return true;
    }

    /**
     * Performs the invasion runtime operation.
     * @param sender the sender
     * @return the result
     */
    private InvasionRuntime invasionRuntime(CommandSender sender) {
        if (!sender.hasPermission("guilds.territory.invasion") && !sender.isOp()) {
            sender.sendMessage(Component.text("You need 'guilds.territory.invasion'.", NamedTextColor.RED));
            return null;
        }
        InvasionRuntime runtime = plugin.getInvasionRuntime();
        if (runtime == null) {
            sender.sendMessage(Component.text("Invasion subsystem unavailable.", NamedTextColor.RED));
        }
        return runtime;
    }

    /**
     * Performs the admin operation.
     * @param sender the sender
     * @return the result
     */
    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("guilds.territory.admin") || sender.isOp()) {
            return true;
        }
        sender.sendMessage(Component.text("You need 'guilds.territory.admin'.", NamedTextColor.RED));
        return false;
    }
}


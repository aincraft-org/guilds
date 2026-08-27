package org.aincraft.guilds.territory.squaremap;

import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.territory.model.ChunkPos;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Renders guild claims (guild_blocks chunks) as squaremap layers.
 *
 * <p>Soft-depends on squaremap: when absent, degrades to no-op.
 * Refreshes every 5s from {@link PlotService#getAllGuildBlocks()} and groups
 * chunks by guild per world into merged outline polygons via {@link ChunkOutlines}.
 */
public final class GuildClaimSquaremapBridge implements Listener {

    static final long REFRESH_INTERVAL_TICKS = 100L; // 5s

    private final Plugin plugin;
    private final Supplier<PlotService> plotServiceSupplier;
    private final Supplier<GuildService> guildServiceSupplier;

    private int refreshTaskId = -1;
    private boolean apiAvailable;

    private final Map<String, SimpleLayerProvider> claimLayers = new HashMap<>();

    public GuildClaimSquaremapBridge(
            Plugin plugin,
            Supplier<PlotService> plotServiceSupplier,
            Supplier<GuildService> guildServiceSupplier) {
        this.plugin = plugin;
        this.plotServiceSupplier = plotServiceSupplier;
        this.guildServiceSupplier = guildServiceSupplier;
    }

    public synchronized void start() {
        if (refreshTaskId != -1) {
            return;
        }
        this.apiAvailable = resolveSquaremap();
        if (!apiAvailable) {
            plugin.getLogger().warning(
                    "squaremap not present — guild claim map layers disabled (squaremap.jar missing from plugins/)");
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) {
            registerWorld(world);
        }
        refreshTaskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
        plugin.getLogger().info("Guild claim squaremap bridge started (refresh every "
                + (REFRESH_INTERVAL_TICKS / 20L) + "s)");
    }

    public synchronized void stop() {
        if (refreshTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
        unregisterAll();
        claimLayers.clear();
        plugin.getLogger().info("Guild claim squaremap bridge stopped");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        if (apiAvailable) {
            try {
                registerWorld(event.getWorld());
            } catch (IllegalStateException | NoClassDefFoundError ignored) {
            }
        }
    }

    private boolean resolveSquaremap() {
        try {
            SquaremapProvider.get();
            return true;
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return false;
        }
    }

    private void registerWorld(World world) {
        unregisterWorld(world);
        SquaremapProvider.get()
                .getWorldIfEnabled(BukkitAdapter.worldIdentifier(world))
                .ifPresent(mapWorld -> {
                    try {
                        SimpleLayerProvider layer = SimpleLayerProvider.builder("Guild Claims")
                                .showControls(true)
                                .layerPriority(60)
                                .zIndex(60)
                                .build();
                        mapWorld.layerRegistry().register(Key.of("guild_claims"), layer);
                        claimLayers.put(world.getName(), layer);
                        plugin.getLogger().info("Registered guild claims squaremap layer for world '" + world.getName() + "'");
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Could not register guild claims layer for '" + world.getName()
                                + "': " + e.getMessage());
                    }
                });
    }

    private void unregisterWorld(World world) {
        String name = world.getName();
        if (!claimLayers.containsKey(name)) {
            return;
        }
        try {
            SquaremapProvider.get()
                    .getWorldIfEnabled(BukkitAdapter.worldIdentifier(world))
                    .ifPresent(mapWorld -> {
                        try {
                            if (claimLayers.remove(name) != null) {
                                mapWorld.layerRegistry().unregister(Key.of("guild_claims"));
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    });
        } catch (IllegalStateException | NoClassDefFoundError ignored) {
        }
    }

    private void unregisterAll() {
        for (World world : plugin.getServer().getWorlds()) {
            try {
                unregisterWorld(world);
            } catch (IllegalStateException | NoClassDefFoundError ignored) {
            }
        }
    }

    private void refreshAll() {
        if (!apiAvailable) {
            return;
        }
        try {
            SquaremapProvider.get();
        } catch (IllegalStateException | NoClassDefFoundError e) {
            apiAvailable = false;
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            if (!claimLayers.containsKey(world.getName())) {
                registerWorld(world);
            }
        }
        PlotService plotService = null;
        GuildService guildService = null;
        try {
            plotService = plotServiceSupplier.get();
            guildService = guildServiceSupplier.get();
        } catch (Exception e) {
            return;
        }
        if (plotService == null) {
            return;
        }
        List<GuildBlock> allBlocks;
        try {
            allBlocks = plotService.getAllGuildBlocks();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch guild blocks for squaremap: " + e.getMessage());
            return;
        }
        // Group by world -> guildId -> chunk set
        Map<String, Map<String, Set<ChunkPos>>> byWorldGuild = new HashMap<>();
        for (GuildBlock block : allBlocks) {
            String worldName = block.getWorld();
            String guildId = block.getGuildId();
            if (worldName == null || guildId == null) {
                continue;
            }
            byWorldGuild
                    .computeIfAbsent(worldName, k -> new HashMap<>())
                    .computeIfAbsent(guildId, k -> new HashSet<>())
                    .add(new ChunkPos(block.getX(), block.getZ()));
        }
        for (Map.Entry<String, SimpleLayerProvider> entry : claimLayers.entrySet()) {
            String worldName = entry.getKey();
            SimpleLayerProvider layer = entry.getValue();
            layer.clearMarkers();
            Map<String, Set<ChunkPos>> byGuild = byWorldGuild.get(worldName);
            if (byGuild == null || byGuild.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Set<ChunkPos>> guildEntry : byGuild.entrySet()) {
                String guildId = guildEntry.getKey();
                Set<ChunkPos> chunks = guildEntry.getValue();
                if (chunks.isEmpty()) {
                    continue;
                }
                String guildName = guildId;
                if (guildService != null) {
                    try {
                        guildName = guildService.getGuildById(guildId)
                                .map(g -> g.getName())
                                .orElse(guildId);
                    } catch (Exception ignored) {
                    }
                }
                Color color = colorForGuild(guildId);
                Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 90);
                Marker marker = Marker.multiPolygon(ChunkOutlines.toParts(chunks));
                marker.markerOptions(MarkerOptions.builder()
                        .strokeColor(color)
                        .strokeWeight(2)
                        .fillColor(fill)
                        .fillOpacity(0.35)
                        .hoverTooltip(htmlEscape(guildName) + " (" + chunks.size() + " chunks)")
                        .clickTooltip("<b>" + htmlEscape(guildName) + "</b><br/>" + chunks.size() + " claim(s)")
                        .build());
                layer.addMarker(Key.of("guild_" + keyPart(guildId)), marker);
            }
        }
    }

    private static Color colorForGuild(String guildId) {
        int hash = guildId.hashCode();
        // spread hash to hue 0-360
        float hue = (Math.abs(hash) % 360) / 360f;
        // keep saturation/value high for visibility
        return Color.getHSBColor(hue, 0.75f, 0.85f);
    }

    private static String keyPart(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.length() == 0 ? "unnamed" : out.toString();
    }

    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

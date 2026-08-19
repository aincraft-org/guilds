package org.aincraft.guilds.territory.squaremap;

import org.aincraft.guilds.territory.influence.InfluenceBar;
import org.aincraft.guilds.territory.influence.InfluenceService;
import org.aincraft.guilds.territory.influence.TerritoryInfluenceState;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.ChunkPos;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.Zone;
import org.aincraft.guilds.territory.model.ZoneType;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;
import xyz.jpenilla.squaremap.api.marker.MultiPolygon;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Renders guilds boundaries as squaremap layers.
 *
 * <p>Requires the squaremap plugin on the server (soft dependency). When
 * squaremap is missing or its API is unavailable, this class degrades to a
 * no-op and never throws, so territory protection / persistence / the web
 * submodule keep working.</p>
 *
 * <p>Territory boundaries travel through immutable {@link Territory} copies
 * (REST upserts, {@code /territory reload}, influence flips), with no observer
 * hook in {@link TerritoryRegistry}. The bridge therefore refreshes layers on
 * a short timer instead of listening for change events.</p>
 */
public final class TerritorySquaremapBridge implements Listener {

    /** Interval between registry refreshes (ticks). */
    static final long REFRESH_INTERVAL_TICKS = 100L; // 5s

    private final Plugin plugin;
    private final TerritoryRegistry registry;

    private int refreshTaskId = -1;
    private boolean apiAvailable;

    /** World namespace -> provider for the guilds layer on that world. */
    private final Map<String, SimpleLayerProvider> territoryLayers = new HashMap<>();
    /** World namespace -> provider for the zone layer on that world. */
    private final Map<String, SimpleLayerProvider> zoneLayers = new HashMap<>();
    /** World namespace -> provider for the influence contest layer on that world. */
    private final Map<String, SimpleLayerProvider> influenceLayers = new HashMap<>();

    private final Supplier<Optional<InfluenceService>> influenceSupplier;

    public TerritorySquaremapBridge(Plugin plugin, TerritoryRegistry registry) {
        this(plugin, registry, Optional::empty);
    }

    public TerritorySquaremapBridge(
            Plugin plugin,
            TerritoryRegistry registry,
            Supplier<Optional<InfluenceService>> influenceSupplier
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.influenceSupplier = Objects.requireNonNull(influenceSupplier, "influenceSupplier");
    }
    /**
     * Starts the bridge. Safe to call multiple times.
     */
    public synchronized void start() {
        if (refreshTaskId != -1) {
            return;
        }
        this.apiAvailable = resolveSquaremap();
        if (!apiAvailable) {
            plugin.getLogger().warning(
                    "squaremap not present — territory map layers disabled (squaremap.jar missing from plugins/)");
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Register for worlds loaded before this plugin started.
        for (World world : plugin.getServer().getWorlds()) {
            registerWorld(world);
        }

        refreshTaskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
        plugin.getLogger().info("Territory squaremap bridge started (refresh every "
                + (REFRESH_INTERVAL_TICKS / 20L) + "s)");
    }

    /**
     * Stops the bridge and removes all territory layers from loaded worlds.
     * Safe to call before start() or more than once.
     */
    public synchronized void stop() {
        if (refreshTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
        unregisterAll();
        territoryLayers.clear();
        zoneLayers.clear();
        influenceLayers.clear();
        plugin.getLogger().info("Territory squaremap bridge stopped");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        if (apiAvailable) {
            try {
                registerWorld(event.getWorld());
            } catch (IllegalStateException | NoClassDefFoundError ignored) {
                // squaremap not fully loaded yet; the periodic refresh re-discovers it.
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
        // Unregister any leftovers from a previous registration of this world.
        unregisterWorld(world);

        Squaremap api = SquaremapProvider.get();
        WorldIdentifier identifier = BukkitAdapter.worldIdentifier(world);
        api.getWorldIfEnabled(identifier).ifPresent(mapWorld -> {
            try {
                SimpleLayerProvider territoryLayer = SimpleLayerProvider.builder("Guilds Territories")
                        .showControls(true)
                        .layerPriority(50)
                        .zIndex(50)
                        .build();
                SimpleLayerProvider zoneLayer = SimpleLayerProvider.builder("Guilds Zones")
                        .showControls(true)
                        .layerPriority(51)
                        .zIndex(51)
                        .build();
                SimpleLayerProvider influenceLayer = SimpleLayerProvider.builder("Guilds Influence")
                        .showControls(true)
                        .layerPriority(52)
                        .zIndex(52)
                        .build();
                mapWorld.layerRegistry().register(Key.of("guilds_territories"), territoryLayer);
                mapWorld.layerRegistry().register(Key.of("guilds_zones"), zoneLayer);
                mapWorld.layerRegistry().register(Key.of("guilds_influence"), influenceLayer);
                territoryLayers.put(world.getName(), territoryLayer);
                zoneLayers.put(world.getName(), zoneLayer);
                influenceLayers.put(world.getName(), influenceLayer);
                plugin.getLogger().info("Registered squaremap layers for world '" + world.getName() + "'");
            } catch (IllegalArgumentException e) {
                // A layer with the same key is already registered (double enable) — skip.
                plugin.getLogger().warning("Could not register squaremap layers for '" + world.getName()
                        + "': " + e.getMessage());
            }
        });
    }

    private void unregisterWorld(World world) {
        String name = world.getName();
        if (!territoryLayers.containsKey(name)
                && !zoneLayers.containsKey(name)
                && !influenceLayers.containsKey(name)) {
            return;
        }
        Squaremap api = SquaremapProvider.get();
        api.getWorldIfEnabled(BukkitAdapter.worldIdentifier(world)).ifPresent(mapWorld -> {
            try {
                if (territoryLayers.remove(name) != null) {
                    mapWorld.layerRegistry().unregister(Key.of("guilds_territories"));
                }
                if (zoneLayers.remove(name) != null) {
                    mapWorld.layerRegistry().unregister(Key.of("guilds_zones"));
                }
                if (influenceLayers.remove(name) != null) {
                    mapWorld.layerRegistry().unregister(Key.of("guilds_influence"));
                }
            } catch (IllegalArgumentException ignored) {
                // Layer already unregistered (world unload raced our check).
            }
        });
    }

    private void unregisterAll() {
        for (World world : plugin.getServer().getWorlds()) {
            try {
                unregisterWorld(world);
            } catch (IllegalStateException | NoClassDefFoundError ignored) {
                // squaremap may already have unregistered its API during shutdown.
            }
        }
    }

    private void refreshAll() {
        if (!apiAvailable) {
            return;
        }
        try {
            SquaremapProvider.get(); // cheap re-check
        } catch (IllegalStateException | NoClassDefFoundError e) {
            apiAvailable = false;
            return;
        }
        // A mapped world can become available after POSTWORLD enable (for example
        // when squaremap finishes initializing its renderer). Discover it here.
        for (World world : plugin.getServer().getWorlds()) {
            if (!territoryLayers.containsKey(world.getName())) {
                registerWorld(world);
            }
        }
        for (Map.Entry<String, SimpleLayerProvider> e : territoryLayers.entrySet()) {
            refreshWorld(e.getKey(), e.getValue(), zoneLayers.get(e.getKey()),
                    influenceLayers.get(e.getKey()));
        }
    }

    private void refreshWorld(
            String worldName,
            SimpleLayerProvider territoryLayer,
            SimpleLayerProvider zoneLayer,
            SimpleLayerProvider influenceLayer
    ) {
        territoryLayer.clearMarkers();
        if (zoneLayer != null) {
            zoneLayer.clearMarkers();
        }
        if (influenceLayer != null) {
            influenceLayer.clearMarkers();
        }
        List<Territory> inWorld = new ArrayList<>();
        for (Territory territory : registry.list()) {
            if (territory.worldId().equals(worldName)) {
                inWorld.add(territory);
            }
        }
        Optional<InfluenceService> influence = influenceSupplier.get();
        for (Territory territory : inWorld) {
            Key territoryKey = Key.of("guilds_t_" + keyPart(territory.id()));
            Marker territoryMarker = asMultiPolygon(territory.boundary());
            territoryMarker.markerOptions(MarkerOptions.builder()
                    .strokeColor(TERRITORY_STROKE)
                    .strokeWeight(TERRITORY_STROKE_WEIGHT)
                    .fill(false)
                    .hoverTooltip(territory.name())
                    .build());
            territoryLayer.addMarker(territoryKey, territoryMarker);

            if (influenceLayer != null) {
                Optional<TerritoryInfluenceState> state = influence
                        .flatMap(service -> service.influence(territory.id()));
                Marker influenceMarker = asMultiPolygon(territory.boundary());
                boolean contested = state.isPresent() && hasActiveContest(state.get());
                MarkerOptions.Builder options = MarkerOptions.builder()
                        .strokeColor(contested ? INFLUENCE_CONTEST_STROKE : INFLUENCE_NEUTRAL_STROKE)
                        .strokeWeight(contested ? INFLUENCE_CONTEST_STROKE_WEIGHT : 2)
                        .fill(contested)
                        .hoverTooltip(territory.name());
                if (contested) {
                    options.fillColor(INFLUENCE_CONTEST_FILL)
                            .fillOpacity(INFLUENCE_CONTEST_FILL_OPACITY)
                            .clickTooltip(influenceTooltip(territory, state.get()));
                }
                influenceMarker.markerOptions(options.build());
                influenceLayer.addMarker(
                        Key.of("guilds_i_" + keyPart(territory.id())), influenceMarker);
            }

            for (Zone zone : territory.zones()) {
                Key zoneKey = Key.of("guilds_z_" + keyPart(territory.id()) + "_" + keyPart(zone.id()));
                Marker zoneMarker = asMultiPolygon(zone.boundary());
                zoneMarker.markerOptions(MarkerOptions.builder()
                        .strokeColor(zoneColor(zone.type()))
                        .strokeWeight(1)
                        .fillColor(zoneColor(zone.type()))
                        .fillOpacity(0.35)
                        .hoverTooltip(zone.name())
                        .clickTooltip("<b>" + htmlEscape(zone.name()) + "</b><br/>" + zone.type().name())
                        .build());
                zoneLayer.addMarker(zoneKey, zoneMarker);
            }
        }
    }

    // --- Style -----------------------------------------------------------------

    private static final Color TERRITORY_STROKE = new Color(30, 30, 110);
    private static final int TERRITORY_STROKE_WEIGHT = 3;
    private static final Color INFLUENCE_NEUTRAL_STROKE = new Color(100, 100, 100);
    private static final Color INFLUENCE_CONTEST_STROKE = new Color(190, 45, 45);
    private static final Color INFLUENCE_CONTEST_FILL = new Color(220, 75, 55);
    private static final int INFLUENCE_CONTEST_STROKE_WEIGHT = 4;
    private static final double INFLUENCE_CONTEST_FILL_OPACITY = 0.28;

    private static Color zoneColor(ZoneType type) {
        return switch (type) {
            case WILDERNESS -> new Color(60, 140, 60);
            case CLAIMABLE -> new Color(220, 170, 40);
        };
    }

    /**
     * squaremap {@link Key}s only allow {@code [a-zA-Z0-9._-]}. Territory/zone
     * ids may contain other characters (spaces, apostrophes), so map every
     * disallowed character to {@code '_'}.
     */
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

    private static boolean hasActiveContest(TerritoryInfluenceState state) {
        return !state.bars().isEmpty() || state.declaration() != null;
    }

    private static Optional<InfluenceBar> leadingBar(TerritoryInfluenceState state) {
        return state.bars().stream()
                .sorted(Comparator.comparingDouble(InfluenceBar::value)
                        .reversed()
                        .thenComparing(InfluenceBar::guildId))
                .findFirst();
    }

    private static String influenceTooltip(Territory territory, TerritoryInfluenceState state) {
        String owner = state.ownerGuildId() == null || state.ownerGuildId().isBlank()
                ? "none"
                : state.ownerGuildId();
        String leader = leadingBar(state)
                .map(InfluenceBar::guildId)
                .orElse("none");
        StringBuilder tooltip = new StringBuilder("<b>")
                .append(htmlEscape(territory.name()))
                .append("</b><br/>Owner: ")
                .append(htmlEscape(owner))
                .append("<br/>Top attacker: ")
                .append(htmlEscape(leader));
        if (state.declaration() != null) {
            tooltip.append("<br/>Declaration: ")
                    .append(htmlEscape(state.declaration().guildId()));
        }
        return tooltip.toString();
    }

    // --- Geometry --------------------------------------------------------------

    /**
     * Converts a domain {@link Boundary} to a squaremap marker. Polygonal and
     * chunk vertices are combined (union semantics, matching
     * {@link Boundary#contains}). Chunk sets are merged into
     * chunk-border-aligned outline rings (holes preserved) so the map renders
     * one clean boundary instead of a square per chunk. A single part comes
     * out as a plain polygon so the stroke is one clean outline; multiple
     * parts (separate islands, or polygon + chunk outlines) become a
     * MultiPolygon.
     */
    private static Marker asMultiPolygon(Boundary boundary) {
        List<MultiPolygon.MultiPolygonPart> parts = new ArrayList<>();
        if (boundary.hasPolygon()) {
            parts.add(MultiPolygon.part(points(boundary.polygon())));
        }
        if (boundary.hasChunks()) {
            parts.addAll(ChunkOutlines.toParts(boundary.chunks()));
        }
        if (parts.isEmpty()) {
            // Cannot happen for a valid territory/zone (boundary is non-empty),
            // but never hand squaremap an empty marker.
            return Marker.multiPolygon();
        }
        if (parts.size() == 1) {
            MultiPolygon.MultiPolygonPart only = parts.get(0);
            if (only.negativeSpace().isEmpty()) {
                return Marker.polygon(only.mainPolygon());
            }
        }
        return Marker.multiPolygon(parts);
    }

    private static List<Point> points(List<BlockPos> vertices) {
        List<Point> out = new ArrayList<>(vertices.size());
        for (BlockPos v : vertices) {
            out.add(Point.of(v.x(), v.z()));
        }
        return out;
    }
}

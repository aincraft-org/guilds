package org.aincraft.guilds.territory.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Mirrors every {@link Territory} into real WorldGuard regions (soft dependency).
 *
 * <p>This is a one-way, read-only projection — the WorldGuard analogue of
 * {@link org.aincraft.guilds.territory.squaremap.TerritorySquaremapBridge}.
 * Guilds' own governance-aware {@code BlockProtection} remains the sole
 * enforcer of build/PvP rules; the mirrored regions carry no restrictive
 * flags. They exist purely so other WorldGuard-aware plugins, admin tooling
 * ({@code /rg info}), and third-party integrations see accurate territory
 * shapes and the controlling guild's roster as region owners.</p>
 *
 * <p>Territory boundaries travel through immutable {@link Territory} copies
 * with no observer hook in {@link TerritoryRegistry} (see
 * {@code TerritorySquaremapBridge}'s class doc for why). This bridge
 * therefore refreshes regions on the same short timer instead of listening
 * for change events, diffing against the last-applied shape/owners so
 * unchanged regions are never re-submitted to WorldGuard.</p>
 *
 * <p>When WorldGuard is missing or its API is unavailable, this class
 * degrades to a no-op and never throws.</p>
 */
public final class TerritoryWorldGuardBridge implements Listener {

    /** Interval between registry refreshes (ticks), matching the squaremap bridge. */
    static final long REFRESH_INTERVAL_TICKS = 100L; // 5s

    private final Plugin plugin;
    private final TerritoryRegistry registry;
    private final Supplier<Optional<GuildService>> guildServiceSupplier;

    private int refreshTaskId = -1;
    private boolean apiAvailable;

    /** World name -> (region id -> signature of the last-applied shape/owners). */
    private final Map<String, Map<String, String>> appliedSignatures = new HashMap<>();

    public TerritoryWorldGuardBridge(Plugin plugin, TerritoryRegistry registry,
                                      Supplier<Optional<GuildService>> guildServiceSupplier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.guildServiceSupplier = Objects.requireNonNull(guildServiceSupplier, "guildServiceSupplier");
    }

    /**
     * Starts the bridge. Safe to call multiple times.
     */
    public synchronized void start() {
        if (refreshTaskId != -1) {
            return;
        }
        this.apiAvailable = resolveWorldGuard();
        if (!apiAvailable) {
            plugin.getLogger().warning(
                    "WorldGuard not present — territory region mirroring disabled (WorldGuard.jar missing from plugins/)");
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        refreshTaskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::refreshAll, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
        plugin.getLogger().info("Territory WorldGuard bridge started (refresh every "
                + (REFRESH_INTERVAL_TICKS / 20L) + "s)");
    }

    /**
     * Stops the bridge and removes every region it created from loaded worlds.
     * Safe to call before start() or more than once. Regions are removed (rather
     * than left behind) so a plugin uninstall never orphans WorldGuard state;
     * the next start() recreates them within one refresh cycle.
     */
    public synchronized void stop() {
        if (refreshTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
        removeAllManagedRegions();
        appliedSignatures.clear();
        plugin.getLogger().info("Territory WorldGuard bridge stopped");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(WorldLoadEvent event) {
        if (apiAvailable) {
            try {
                refreshWorld(event.getWorld());
            } catch (IllegalStateException | NullPointerException | NoClassDefFoundError ignored) {
                // WorldGuard not fully loaded yet; the periodic refresh re-discovers it.
            }
        }
    }

    private boolean resolveWorldGuard() {
        try {
            // getPlatform() throws IllegalStateException before WorldGuard finishes
            // enabling; an unset platform can also surface as a null getPlatform()
            // (NullPointerException here) depending on WorldGuard's own boot state.
            return WorldGuard.getInstance().getPlatform().getRegionContainer() != null;
        } catch (IllegalStateException | NullPointerException | NoClassDefFoundError e) {
            return false;
        }
    }

    private void refreshAll() {
        if (!apiAvailable) {
            return;
        }
        try {
            WorldGuard.getInstance().getPlatform().getRegionContainer(); // cheap re-check
        } catch (IllegalStateException | NullPointerException | NoClassDefFoundError e) {
            apiAvailable = false;
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            try {
                refreshWorld(world);
            } catch (IllegalStateException | NullPointerException | NoClassDefFoundError e) {
                // WorldGuard hiccup for this world; keep the repeating task alive and retry next cycle.
                plugin.getLogger().log(Level.WARNING,
                        "Failed to refresh WorldGuard regions for world '" + world.getName() + "'", e);
            }
        }
    }

    private void refreshWorld(World world) {
        RegionManager manager = regionManagerFor(world);
        if (manager == null) {
            return;
        }
        Optional<GuildService> guildService = guildServiceSupplier.get();
        Map<String, String> applied = appliedSignatures.computeIfAbsent(world.getName(), k -> new HashMap<>());
        Map<String, String> desired = new HashMap<>();
        boolean dirty = false;

        for (Territory territory : registry.list()) {
            if (!territory.worldId().equals(world.getName())) {
                continue;
            }
            Set<UUID> owners = ownersFor(territory, guildService);
            for (TerritoryRegionPlanner.RegionSpec spec : TerritoryRegionPlanner.plan(territory, owners)) {
                String signature = TerritoryRegionPlanner.signatureOf(spec);
                desired.put(spec.id(), signature);
                if (!signature.equals(applied.get(spec.id()))) {
                    applyRegion(manager, spec, world);
                    dirty = true;
                }
            }
        }

        for (String staleId : new ArrayList<>(applied.keySet())) {
            if (!desired.containsKey(staleId)) {
                manager.removeRegion(staleId);
                dirty = true;
            }
        }

        if (!dirty) {
            return;
        }

        // RegionManager mutations (addRegion/removeRegion) are in-memory only until saved;
        // without this, mirrored regions would vanish on server restart. Only commit the new
        // signatures to `applied` once the save actually succeeds. If we committed unconditionally
        // and the save failed, the next tick would diff against the (wrongly) already-updated
        // `applied` map, see no difference, and silently skip retrying the addRegion/save — the
        // failed write would be forgotten forever instead of retried on the next cycle.
        if (saveQuietly(manager, world)) {
            applied.clear();
            applied.putAll(desired);
        }
    }

    /**
     * Persists a region manager's in-memory changes to disk. Storage failures are logged, not
     * propagated, so a transient save failure never kills the repeating refresh task — instead
     * the caller keeps the previous `applied` signatures so the same diff (and therefore the
     * same save attempt) is retried on the next dirty reconciliation.
     *
     * @return {@code true} if the save succeeded, {@code false} if it failed and was logged.
     */
    private boolean saveQuietly(RegionManager manager, World world) {
        try {
            manager.save();
            return true;
        } catch (com.sk89q.worldguard.protection.managers.storage.StorageException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save WorldGuard regions for world '" + world.getName() + "'", e);
            return false;
        }
    }

    private void applyRegion(RegionManager manager, TerritoryRegionPlanner.RegionSpec spec, World world) {
        ProtectedRegion region = toProtectedRegion(spec, world);
        DefaultDomain owners = new DefaultDomain();
        for (UUID owner : spec.owners()) {
            owners.addPlayer(owner);
        }
        region.setOwners(owners);
        manager.addRegion(region);
    }

    private static ProtectedRegion toProtectedRegion(TerritoryRegionPlanner.RegionSpec spec, World world) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        if (spec.isCuboid()) {
            BlockPos min = spec.chunk().minBlock();
            BlockVector3 minVec = BlockVector3.at(min.x(), minY, min.z());
            BlockVector3 maxVec = BlockVector3.at(min.x() + 15, maxY, min.z() + 15);
            return new ProtectedCuboidRegion(spec.id(), minVec, maxVec);
        }
        List<BlockVector2> points = new ArrayList<>(spec.polygon().size());
        for (BlockPos v : spec.polygon()) {
            points.add(BlockVector2.at(v.x(), v.z()));
        }
        return new ProtectedPolygonalRegion(spec.id(), points, minY, maxY);
    }

    private static RegionManager regionManagerFor(World world) {
        try {
            return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        } catch (IllegalStateException | NullPointerException | NoClassDefFoundError e) {
            return null;
        }
    }

    private static Set<UUID> ownersFor(Territory territory, Optional<GuildService> guildService) {
        if (guildService.isEmpty()) {
            return Set.of();
        }
        return territory.governedByGuildId()
                .flatMap(guildService.get()::getGuildById)
                .map(Guild::getResidents)
                .<Set<UUID>>map(Set::copyOf)
                .orElse(Set.of());
    }

    private void removeAllManagedRegions() {
        if (!apiAvailable) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            Map<String, String> applied = appliedSignatures.get(world.getName());
            if (applied == null || applied.isEmpty()) {
                continue;
            }
            RegionManager manager = regionManagerFor(world);
            if (manager == null) {
                continue;
            }
            for (String id : applied.keySet()) {
                manager.removeRegion(id);
            }
            saveQuietly(manager, world);
        }
    }
}

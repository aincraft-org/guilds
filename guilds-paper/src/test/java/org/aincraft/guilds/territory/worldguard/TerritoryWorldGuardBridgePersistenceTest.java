package org.aincraft.guilds.territory.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;

import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.ChunkPos;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the actual region-manager reconciliation path (not just presence of the code),
 * with a real {@link org.bukkit.plugin.Server} bypassed via mocks and WorldGuard's static
 * {@code WorldGuard.getInstance()} entry point mocked directly.
 *
 * <p>Confirms the fix for the durability gap where {@code RegionManager} mutations
 * (add/removeRegion) are only in-memory until {@code save()} is called: without it, mirrored
 * regions would silently disappear on the next server restart. Also confirms {@code save()} is
 * called only once per <em>dirty</em> reconciliation, not on every unchanged refresh tick.</p>
 */
@ExtendWith(MockitoExtension.class)
class TerritoryWorldGuardBridgePersistenceTest {

    @Mock private Plugin plugin;
    @Mock private org.bukkit.Server server;
    @Mock private PluginManager pluginManager;
    @Mock private BukkitScheduler scheduler;
    @Mock private World world;

    @Mock private WorldGuard worldGuard;
    @Mock private WorldGuardPlatform platform;
    @Mock private RegionContainer regionContainer;
    @Mock private RegionManager regionManager;

    private Runnable startAndCaptureRefreshTick(TerritoryWorldGuardBridge bridge) {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("test-worldguard-persistence"));
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getScheduler()).thenReturn(scheduler);
        lenient().when(server.getWorlds()).thenReturn(List.of(world));
        lenient().when(world.getName()).thenReturn("world");
        lenient().when(world.getMinHeight()).thenReturn(-64);
        lenient().when(world.getMaxHeight()).thenReturn(320);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleSyncRepeatingTask(eq(plugin), captor.capture(), anyLong(), anyLong())).thenReturn(1);

        bridge.start();
        return captor.getValue();
    }

    private TerritoryRegistry registryWithOneChunkTerritory() {
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(new Territory("keep", "Keep", "world",
                Boundary.ofChunks(Set.of(new ChunkPos(0, 0)))));
        return registry;
    }

    @Test
    void dirtyReconciliation_savesOnceThenSkipsSaveOnUnchangedTick() throws Exception {
        try (MockedStatic<WorldGuard> wg = mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> adapter = mockStatic(BukkitAdapter.class)) {
            wg.when(WorldGuard::getInstance).thenReturn(worldGuard);
            when(worldGuard.getPlatform()).thenReturn(platform);
            when(platform.getRegionContainer()).thenReturn(regionContainer);
            adapter.when(() -> BukkitAdapter.adapt(any(World.class)))
                    .thenReturn(mock(com.sk89q.worldedit.world.World.class));
            when(regionContainer.get(any())).thenReturn(regionManager);

            TerritoryWorldGuardBridge bridge = new TerritoryWorldGuardBridge(
                    plugin, registryWithOneChunkTerritory(), Optional::empty);
            Runnable tick = startAndCaptureRefreshTick(bridge);

            // First reconciliation: the territory's region is brand new -> dirty -> must persist.
            tick.run();
            verify(regionManager, times(1)).addRegion(any(ProtectedRegion.class));
            verify(regionManager, times(1)).save();

            // Second reconciliation with nothing changed: must NOT re-save (would be wasted I/O
            // on every 5s tick otherwise).
            tick.run();
            verify(regionManager, times(1)).addRegion(any(ProtectedRegion.class));
            verify(regionManager, times(1)).save();
        }
    }

    @Test
    void stop_removesManagedRegionsAndPersistsTheRemoval() throws Exception {
        try (MockedStatic<WorldGuard> wg = mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> adapter = mockStatic(BukkitAdapter.class)) {
            wg.when(WorldGuard::getInstance).thenReturn(worldGuard);
            when(worldGuard.getPlatform()).thenReturn(platform);
            when(platform.getRegionContainer()).thenReturn(regionContainer);
            adapter.when(() -> BukkitAdapter.adapt(any(World.class)))
                    .thenReturn(mock(com.sk89q.worldedit.world.World.class));
            when(regionContainer.get(any())).thenReturn(regionManager);

            TerritoryWorldGuardBridge bridge = new TerritoryWorldGuardBridge(
                    plugin, registryWithOneChunkTerritory(), Optional::empty);
            Runnable tick = startAndCaptureRefreshTick(bridge);
            tick.run();
            verify(regionManager, times(1)).save();

            bridge.stop();

            verify(regionManager, times(1)).removeRegion("guilds-keep-0_0");
            // One save from the initial dirty reconciliation, one more from stop()'s removal.
            verify(regionManager, times(2)).save();
        }
    }

    @Test
    void failedSave_isRetriedOnTheNextTickInsteadOfBeingForgotten() throws Exception {
        // Regression coverage for a durability bug: applied signatures must NOT be committed
        // when save() fails, otherwise the next tick's diff would see no change (since it was
        // wrongly already recorded as applied) and would skip retrying the addRegion/save
        // entirely -- permanently losing the failed write until the underlying data changes
        // again.
        try (MockedStatic<WorldGuard> wg = mockStatic(WorldGuard.class);
             MockedStatic<BukkitAdapter> adapter = mockStatic(BukkitAdapter.class)) {
            wg.when(WorldGuard::getInstance).thenReturn(worldGuard);
            when(worldGuard.getPlatform()).thenReturn(platform);
            when(platform.getRegionContainer()).thenReturn(regionContainer);
            adapter.when(() -> BukkitAdapter.adapt(any(World.class)))
                    .thenReturn(mock(com.sk89q.worldedit.world.World.class));
            when(regionContainer.get(any())).thenReturn(regionManager);

            org.mockito.Mockito.doThrow(
                            new com.sk89q.worldguard.protection.managers.storage.StorageException("disk full"))
                    .doNothing()
                    .when(regionManager).save();

            TerritoryWorldGuardBridge bridge = new TerritoryWorldGuardBridge(
                    plugin, registryWithOneChunkTerritory(), Optional::empty);
            Runnable tick = startAndCaptureRefreshTick(bridge);

            // First tick: addRegion happens, save() throws (swallowed) -> must NOT be treated
            // as applied.
            tick.run();
            verify(regionManager, times(1)).addRegion(any(ProtectedRegion.class));
            verify(regionManager, times(1)).save();

            // Second tick: nothing about the territory actually changed, but since the failed
            // save was never committed as "applied", the bridge must still see it as dirty and
            // retry both the addRegion and the save. This time save() succeeds.
            tick.run();
            verify(regionManager, times(2)).addRegion(any(ProtectedRegion.class));
            verify(regionManager, times(2)).save();

            // Third tick: the retry's save succeeded and was committed, so a further unchanged
            // tick must NOT re-add or re-save.
            tick.run();
            verify(regionManager, times(2)).addRegion(any(ProtectedRegion.class));
            verify(regionManager, times(2)).save();
        }
    }

    @Test
    void neverAvailable_neverCallsSave() throws Exception {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("test-worldguard-persistence"));

        // No WorldGuard static mocking here: the real WorldGuard.getInstance() throws
        // IllegalStateException in this test process (no live server), so the bridge must
        // degrade before ever touching a RegionManager.
        TerritoryWorldGuardBridge bridge = new TerritoryWorldGuardBridge(
                plugin, registryWithOneChunkTerritory(), Optional::empty);
        bridge.start();
        bridge.stop();

        verify(regionManager, never()).save();
    }
}

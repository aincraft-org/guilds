package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.building.boat.BoatRouteCache;
import org.aincraft.guilds.territory.building.boat.BoatRouteCalculator;
import org.aincraft.guilds.territory.building.boat.BoatRouteResult;
import org.aincraft.guilds.territory.building.boat.BoatRouteService;
import org.aincraft.guilds.territory.building.boat.BoatWaterChangeListener;
import org.aincraft.guilds.territory.building.boat.BoatWaterMask;
import org.aincraft.guilds.territory.building.boat.BoatWaterSnapshot;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoatRouteServiceTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void capturesOnMainExecutorAndReusesCacheWithoutWorkerWorldAccess() {
        BoatWaterMask.Cell start = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell end = new BoatWaterMask.Cell(2, 62, 0);
        BoatWaterSnapshot snapshot = new BoatWaterSnapshot(
                WORLD, 0, 0,
                new BoatWaterMask(0, 0, Set.of(start, new BoatWaterMask.Cell(1, 62, 0), end)),
                Set.of(start, new BoatWaterMask.Cell(1, 62, 0), end));
        AtomicInteger captures = new AtomicInteger();
        AtomicReference<String> captureThread = new AtomicReference<>();
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "route-worker"));
        BoatRouteService service = new BoatRouteService(
                new BoatRouteCache(), new BoatRouteCalculator(), worker,
                Runnable::run,
                (request, maxChunks) -> {
                    captures.incrementAndGet();
                    captureThread.set(Thread.currentThread().getName());
                    return BoatRouteService.SnapshotBatch.complete(List.of(snapshot));
                },
                32, 256, 32, 8);
        try {
            BoatRouteResult first = service.route(WORLD, start, end).toCompletableFuture().join();
            BoatRouteResult second = service.route(WORLD, end, start).toCompletableFuture().join();

            assertEquals(BoatRouteResult.Status.CONNECTED, first.status());
            assertEquals(2.0, first.distance());
            assertEquals(first, second);
            assertEquals(1, captures.get());
            assertEquals(Thread.currentThread().getName(), captureThread.get());
        } finally {
            service.close();
            worker.shutdownNow();
        }
    }

    @Test
    void unavailableLoadedWorldPrerequisiteDoesNotReachWorker() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        AtomicInteger analyzed = new AtomicInteger();
        BoatRouteCalculator calculator = new BoatRouteCalculator() {
            @Override
            public BoatRouteResult calculate(List<BoatWaterSnapshot> snapshots,
                                             BoatWaterMask.Cell origin,
                                             BoatWaterMask.Cell destination,
                                             int chunkBudget,
                                             int nodeBudget) {
                analyzed.incrementAndGet();
                return super.calculate(snapshots, origin, destination, chunkBudget, nodeBudget);
            }
        };
        BoatRouteService service = new BoatRouteService(
                new BoatRouteCache(), calculator, worker, Runnable::run,
                (request, maxChunks) -> BoatRouteService.SnapshotBatch.unavailable(),
                32, 256, 32, 8);
        try {
            BoatRouteResult result = service.route(
                    WORLD, new BoatWaterMask.Cell(0, 62, 0),
                    new BoatWaterMask.Cell(1, 62, 0)).toCompletableFuture().join();
            assertEquals(BoatRouteResult.Status.UNAVAILABLE, result.status());
            assertEquals(0, analyzed.get());
        } finally {
            service.close();
            worker.shutdownNow();
        }
    }

    @Test
    void corridorCapturesEndpointsBeyondSqrtBudget() {
        World world = mock(World.class);
        Block air = mock(Block.class);
        when(world.getUID()).thenReturn(WORLD);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(62);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(air.getType()).thenReturn(Material.AIR);

        AtomicInteger analyses = new AtomicInteger();
        BoatRouteCalculator calculator = new BoatRouteCalculator() {
            @Override
            public BoatRouteResult calculate(List<BoatWaterSnapshot> snapshots,
                                             BoatWaterMask.Cell origin,
                                             BoatWaterMask.Cell destination,
                                             int chunkBudget,
                                             int nodeBudget) {
                analyses.incrementAndGet();
                assertTrue(snapshots.size() <= 256);
                return BoatRouteResult.disconnected();
            }
        };
        BoatRouteService.SnapshotProducer producer = BoatRouteService.bukkitSnapshotProducer(
                ignored -> world, 2, 256);
        BoatRouteService service = new BoatRouteService(
                new BoatRouteCache(), calculator, Runnable::run, Runnable::run,
                producer, 32, 256, 32, 16);
        BoatWaterMask.Cell origin = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell destination = new BoatWaterMask.Cell(17 * 16, 62, 0);
        try {
            BoatRouteResult result = service.route(WORLD, origin, destination)
                    .toCompletableFuture().join();

            assertEquals(BoatRouteResult.Status.DISCONNECTED, result.status());
            assertEquals(1, analyses.get());
        } finally {
            service.close();
        }
    }

    @Test
    void clearSpaceHeightChangesInvalidateWaterRoute() {
        BoatRouteCache cache = new BoatRouteCache();
        BoatWaterMask.Cell start = new BoatWaterMask.Cell(0, 62, 0);
        BoatWaterMask.Cell end = new BoatWaterMask.Cell(1, 62, 0);
        cache.put(WORLD, start, end, BoatRouteResult.connected(1.0));

        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Block changed = mock(Block.class);
        Block air = mock(Block.class);
        Block water = mock(Block.class);
        when(world.getUID()).thenReturn(WORLD);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);
        when(changed.getWorld()).thenReturn(world);
        when(changed.getChunk()).thenReturn(chunk);
        when(changed.getType()).thenReturn(Material.STONE);
        when(air.getType()).thenReturn(Material.AIR);
        when(water.getType()).thenReturn(Material.WATER);
        when(changed.getRelative(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(changed.getRelative(0, -2, 0)).thenReturn(water);

        BoatWaterChangeListener listener = new BoatWaterChangeListener(cache, 2);
        listener.onBlockBreak(new BlockBreakEvent(changed, mock(Player.class)));

        assertTrue(cache.get(WORLD, start, end).isEmpty());
    }

    @Test
    void captureBudgetExhaustionIsPending() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        BoatRouteService service = new BoatRouteService(
                new BoatRouteCache(), new BoatRouteCalculator(), worker, Runnable::run,
                (request, maxChunks) -> BoatRouteService.SnapshotBatch.incomplete(List.of()),
                32, 1, 32, 1);
        try {
            BoatRouteResult result = service.route(
                    WORLD, new BoatWaterMask.Cell(0, 62, 0),
                    new BoatWaterMask.Cell(1, 62, 0)).toCompletableFuture().join();
            assertTrue(result.status() == BoatRouteResult.Status.PENDING
                    || result.status() == BoatRouteResult.Status.UNAVAILABLE);
        } finally {
            service.close();
            worker.shutdownNow();
        }
    }
}

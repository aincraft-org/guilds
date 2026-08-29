package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.building.boat.BoatRouteCache;
import org.aincraft.guilds.territory.building.boat.BoatRouteCalculator;
import org.aincraft.guilds.territory.building.boat.BoatRouteResult;
import org.aincraft.guilds.territory.building.boat.BoatRouteService;
import org.aincraft.guilds.territory.building.boat.BoatWaterMask;
import org.aincraft.guilds.territory.building.boat.BoatWaterSnapshot;
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
                request -> {
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
                request -> BoatRouteService.SnapshotBatch.unavailable(),
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
    void captureBudgetExhaustionIsPending() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        BoatRouteService service = new BoatRouteService(
                new BoatRouteCache(), new BoatRouteCalculator(), worker, Runnable::run,
                request -> BoatRouteService.SnapshotBatch.incomplete(List.of()),
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

package org.aincraft.guilds.territory.building.boat;

import org.aincraft.guilds.territory.building.BuildingConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;

/**
 * Coordinates bounded main-thread snapshot capture with off-thread route
 * analysis. The worker receives only immutable values and never a Bukkit world.
 */
public final class BoatRouteService implements AutoCloseable {
    private final BoatRouteCache cache;
    private final BoatRouteCalculator calculator;
    private final Executor workerExecutor;
    private final MainThreadExecutor mainThreadExecutor;
    private final SnapshotProducer snapshotProducer;
    private final int searchChunkRadius;
    private final int searchChunkBudget;
    private final int nodeBudget;
    private final int snapshotBatchSize;
    private final int clearBoatSpaceHeight;
    private final boolean ownsWorkerExecutor;
    private final ConcurrentMap<BoatRouteCache.Key, RequestState> pending = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public BoatRouteService(BoatRouteCache cache,
                            BoatRouteCalculator calculator,
                            Executor workerExecutor,
                            MainThreadExecutor mainThreadExecutor,
                            SnapshotProducer snapshotProducer,
                            int searchChunkRadius,
                            int searchChunkBudget,
                            int nodeBudget) {
        this(cache, calculator, workerExecutor, mainThreadExecutor, snapshotProducer,
                searchChunkRadius, searchChunkBudget, nodeBudget,
                Math.min(16, searchChunkBudget));
    }

    public BoatRouteService(BoatRouteCache cache,
                            BoatRouteCalculator calculator,
                            Executor workerExecutor,
                            MainThreadExecutor mainThreadExecutor,
                            SnapshotProducer snapshotProducer,
                            int searchChunkRadius,
                            int searchChunkBudget,
                            int nodeBudget,
                            int snapshotBatchSize) {
        this(cache, calculator, workerExecutor, mainThreadExecutor, snapshotProducer,
                searchChunkRadius, searchChunkBudget, nodeBudget, snapshotBatchSize, false, 2);
    }

    public BoatRouteService(BoatRouteCache cache,
                            BoatRouteCalculator calculator,
                            Executor workerExecutor,
                            MainThreadExecutor mainThreadExecutor,
                            SnapshotProducer snapshotProducer,
                            BuildingConfig.TransportGeometry geometry) {
        this(cache, calculator, workerExecutor, mainThreadExecutor, snapshotProducer,
                geometry.searchChunkRadius(), geometry.searchChunkBudget(),
                Math.max(1, geometry.searchChunkBudget() * 64),
                Math.min(16, geometry.searchChunkBudget()), false,
                geometry.clearBoatSpaceHeight());
    }

    /**
     * Production constructor. Bukkit snapshot work is scheduled through Paper's
     * scheduler and the owned route executor is stopped by {@link #close()}.
     */
    public BoatRouteService(JavaPlugin plugin,
                            BoatRouteCache cache,
                            BuildingConfig.TransportGeometry geometry) {
        this(cache,
                new BoatRouteCalculator(),
                newRouteExecutor(),
                task -> {
                    if (plugin.getServer().getScheduler().runTask(plugin, task) == null) {
                        throw new IllegalStateException("unable to schedule boat route capture");
                    }
                },
                new BukkitSnapshotProducer(plugin.getServer()::getWorld,
                        geometry.clearBoatSpaceHeight(), geometry.searchChunkBudget()),
                geometry.searchChunkRadius(), geometry.searchChunkBudget(),
                Math.max(1, geometry.searchChunkBudget() * 64),
                Math.min(16, geometry.searchChunkBudget()), true,
                geometry.clearBoatSpaceHeight());
    }

    public BoatRouteService(JavaPlugin plugin, BuildingConfig.TransportGeometry geometry) {
        this(plugin, new BoatRouteCache(), geometry);
    }

    public static SnapshotProducer bukkitSnapshotProducer(Function<UUID, World> worldLookup,
                                                           int clearBoatSpaceHeight,
                                                           int searchChunkBudget) {
        if (clearBoatSpaceHeight <= 0 || searchChunkBudget <= 0) {
            throw new IllegalArgumentException("snapshot limits must be positive");
        }
        return new BukkitSnapshotProducer(
                Objects.requireNonNull(worldLookup, "worldLookup"),
                clearBoatSpaceHeight, searchChunkBudget);
    }

    private BoatRouteService(BoatRouteCache cache,
                             BoatRouteCalculator calculator,
                             Executor workerExecutor,
                             MainThreadExecutor mainThreadExecutor,
                             SnapshotProducer snapshotProducer,
                             int searchChunkRadius,
                             int searchChunkBudget,
                             int nodeBudget,
                             int snapshotBatchSize,
                             boolean ownsWorkerExecutor,
                             int clearBoatSpaceHeight) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "workerExecutor");
        this.snapshotProducer = Objects.requireNonNull(snapshotProducer, "snapshotProducer");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        if (searchChunkRadius <= 0 || searchChunkBudget <= 0 || nodeBudget <= 0
                || snapshotBatchSize <= 0 || clearBoatSpaceHeight <= 0) {
            throw new IllegalArgumentException("route limits must be positive");
        }
        this.searchChunkRadius = searchChunkRadius;
        this.searchChunkBudget = searchChunkBudget;
        this.nodeBudget = nodeBudget;
        this.snapshotBatchSize = snapshotBatchSize;
        this.ownsWorkerExecutor = ownsWorkerExecutor;
        this.clearBoatSpaceHeight = clearBoatSpaceHeight;
    }

    public int clearBoatSpaceHeight() {
        return clearBoatSpaceHeight;
    }

    public CompletionStage<BoatRouteResult> route(UUID worldId,
                                                   BoatWaterMask.Cell origin,
                                                   BoatWaterMask.Cell destination) {
        if (closed || worldId == null || origin == null || destination == null) {
            return CompletableFuture.completedFuture(BoatRouteResult.unavailable());
        }
        BoatRouteCache.Key key = cache.key(worldId, origin, destination);
        var cached = cache.get(key);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }
        CompletableFuture<BoatRouteResult> result = new CompletableFuture<>();
        RequestState state = new RequestState(
                key,
                new RouteRequest(worldId, origin, destination, searchChunkRadius),
                result);
        RequestState existing = pending.putIfAbsent(key, state);
        if (existing != null) {
            return existing.result();
        }
        dispatchCapture(state);
        return result;
    }

    public CompletionStage<BoatRouteResult> check(UUID worldId,
                                                  BoatWaterMask.Cell origin,
                                                  BoatWaterMask.Cell destination) {
        return route(worldId, origin, destination);
    }

    public CompletionStage<BoatRouteResult> route(RouteRequest request) {
        Objects.requireNonNull(request, "request");
        return route(request.worldId(), request.origin(), request.destination());
    }

    public int pendingCount() {
        return pending.size();
    }

    public BoatRouteCache cache() {
        return cache;
    }

    private void dispatchCapture(RequestState state) {
        try {
            mainThreadExecutor.execute(() -> captureBatch(state));
        } catch (RuntimeException exception) {
            finish(state, BoatRouteResult.unavailable());
        }
    }

    private void captureBatch(RequestState state) {
        if (closed) {
            finish(state, BoatRouteResult.unavailable());
            return;
        }
        RouteRequest request = state.request();
        SnapshotBatch batch;
        try {
            batch = snapshotProducer.capture(request, snapshotBatchSize);
        } catch (RuntimeException exception) {
            finish(state, BoatRouteResult.unavailable());
            return;
        }
        if (batch == null || !batch.available()) {
            finish(state, BoatRouteResult.unavailable());
            return;
        }
        int previousChunks = state.capturedChunks().size();
        state.snapshots().addAll(batch.snapshots());
        state.capturedChunks().addAll(batch.examinedChunks());
        if (state.capturedChunks().size() > searchChunkBudget) {
            finish(state, BoatRouteResult.pending());
            return;
        }
        if (!batch.complete()) {
            if (state.capturedChunks().size() >= searchChunkBudget
                    || state.capturedChunks().size() == previousChunks) {
                finish(state, BoatRouteResult.pending());
                return;
            }
            state.request(new RouteRequest(request.worldId(), request.origin(), request.destination(),
                    request.searchChunkRadius(), state.capturedChunks()));
            dispatchCapture(state);
            return;
        }
        List<BoatWaterSnapshot> snapshots = List.copyOf(state.snapshots());
        Set<BoatWaterMask.Chunk> dependencies = Set.copyOf(state.capturedChunks());
        submitAnalysis(state, snapshots, dependencies);
    }

    private void submitAnalysis(RequestState state,
                                 List<BoatWaterSnapshot> snapshots,
                                 Set<BoatWaterMask.Chunk> dependencies) {
        for (BoatWaterSnapshot snapshot : snapshots) {
            if (snapshot == null || !state.key().worldId().equals(snapshot.worldId())) {
                finish(state, BoatRouteResult.unavailable());
                return;
            }
        }

        try {
            CompletableFuture.supplyAsync(
                    () -> calculator.calculate(snapshots, state.request().origin(),
                            state.request().destination(), searchChunkBudget, nodeBudget),
                    workerExecutor).whenComplete((result, error) -> {
                        if (error != null || result == null) {
                            finish(state, BoatRouteResult.unavailable());
                        } else if (result.isCacheable()
                                && !cache.put(state.key(), result, dependencies)) {
                            // A water invalidation raced analysis; do not expose a
                            // result computed against an obsolete revision.
                            finish(state, BoatRouteResult.pending());
                        } else {
                            finish(state, result);
                        }
                    });
        } catch (RuntimeException exception) {
            finish(state, BoatRouteResult.unavailable());
        }
    }

    private void finish(RequestState state, BoatRouteResult result) {
        if (pending.remove(state.key(), state)) {
            state.result().complete(result);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (RequestState state : pending.values()) {
            state.result().complete(BoatRouteResult.unavailable());
        }
        pending.clear();
        if (ownsWorkerExecutor && workerExecutor instanceof ExecutorService executor) {
            executor.shutdownNow();
        }
    }

    public void shutdown() {
        close();
    }

    private static ExecutorService newRouteExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "guilds-boat-route");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(1, factory);
    }

    @FunctionalInterface
    public interface MainThreadExecutor {
        void execute(Runnable task);
    }

    @FunctionalInterface
    public interface SnapshotProducer {
        SnapshotBatch capture(RouteRequest request, int maxChunks);
    }

    public record RouteRequest(UUID worldId,
                               BoatWaterMask.Cell origin,
                               BoatWaterMask.Cell destination,
                               int searchChunkRadius,
                               Set<BoatWaterMask.Chunk> capturedChunks) {
        public RouteRequest {
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(destination, "destination");
            if (searchChunkRadius <= 0) {
                throw new IllegalArgumentException("searchChunkRadius must be positive");
            }
            capturedChunks = capturedChunks == null ? Set.of() : Set.copyOf(capturedChunks);
        }

        public RouteRequest(UUID worldId,
                            BoatWaterMask.Cell origin,
                            BoatWaterMask.Cell destination,
                            int searchChunkRadius) {
            this(worldId, origin, destination, searchChunkRadius, Set.of());
        }
    }

    public record SnapshotBatch(List<BoatWaterSnapshot> snapshots,
                                Set<BoatWaterMask.Chunk> examinedChunks,
                                boolean complete,
                                boolean available) {
        public SnapshotBatch {
            snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
            if (examinedChunks == null) {
                Set<BoatWaterMask.Chunk> inferred = new HashSet<>();
                for (BoatWaterSnapshot snapshot : snapshots) {
                    if (snapshot != null) {
                        inferred.add(snapshot.chunk());
                    }
                }
                examinedChunks = Set.copyOf(inferred);
            } else {
                examinedChunks = Set.copyOf(examinedChunks);
            }
        }

        public SnapshotBatch(List<BoatWaterSnapshot> snapshots,
                             boolean complete,
                             boolean available) {
            this(snapshots, null, complete, available);
        }

        public static SnapshotBatch complete(Collection<BoatWaterSnapshot> snapshots) {
            return new SnapshotBatch(List.copyOf(snapshots), true, true);
        }

        public static SnapshotBatch incomplete(Collection<BoatWaterSnapshot> snapshots) {
            return new SnapshotBatch(List.copyOf(snapshots), false, true);
        }
        public static SnapshotBatch pending() {
            return new SnapshotBatch(List.of(), Set.of(), false, true);
        }


        public static SnapshotBatch unavailable() {
            return new SnapshotBatch(List.of(), Set.of(), true, false);
        }
    }

    private static final class RequestState {
        private final BoatRouteCache.Key key;
        private final CompletableFuture<BoatRouteResult> result;
        private final List<BoatWaterSnapshot> snapshots = new ArrayList<>();
        private final Set<BoatWaterMask.Chunk> capturedChunks = new HashSet<>();
        private volatile RouteRequest request;

        private RequestState(BoatRouteCache.Key key, RouteRequest request,
                             CompletableFuture<BoatRouteResult> result) {
            this.key = key;
            this.request = request;
            this.result = result;
        }

        private BoatRouteCache.Key key() {
            return key;
        }

        private CompletableFuture<BoatRouteResult> result() {
            return result;
        }

        private List<BoatWaterSnapshot> snapshots() {
            return snapshots;
        }

        private Set<BoatWaterMask.Chunk> capturedChunks() {
            return capturedChunks;
        }

        private RouteRequest request() {
            return request;
        }

        private void request(RouteRequest request) {
            this.request = request;
        }
    }

    private static final class BukkitSnapshotProducer implements SnapshotProducer {
        private final Function<UUID, World> worldLookup;
        private final int clearBoatSpaceHeight;
        private final int searchChunkBudget;

        private BukkitSnapshotProducer(Function<UUID, World> worldLookup,
                                       int clearBoatSpaceHeight,
                                       int searchChunkBudget) {
            this.worldLookup = worldLookup;
            this.clearBoatSpaceHeight = clearBoatSpaceHeight;
            this.searchChunkBudget = searchChunkBudget;
        }

        @Override
        public SnapshotBatch capture(RouteRequest request, int maxChunks) {
            World world = worldLookup.apply(request.worldId());
            if (world == null) {
                return SnapshotBatch.unavailable();
            }
            long originChunkX = Math.floorDiv(request.origin().x(), BoatWaterMask.CHUNK_SIZE);
            long originChunkZ = Math.floorDiv(request.origin().z(), BoatWaterMask.CHUNK_SIZE);
            long destinationChunkX = Math.floorDiv(request.destination().x(), BoatWaterMask.CHUNK_SIZE);
            long destinationChunkZ = Math.floorDiv(request.destination().z(), BoatWaterMask.CHUNK_SIZE);
            long spanX = Math.abs(destinationChunkX - originChunkX);
            long spanZ = Math.abs(destinationChunkZ - originChunkZ);
            if (spanX > 2L * request.searchChunkRadius()
                    || spanZ > 2L * request.searchChunkRadius()) {
                return SnapshotBatch.pending();
            }
            long corridorLength = spanX + spanZ + 1L;
            if (corridorLength > searchChunkBudget) {
                return SnapshotBatch.pending();
            }
            List<BoatWaterMask.Chunk> candidates = prioritizedChunks(request,
                    originChunkX, originChunkZ, destinationChunkX, destinationChunkZ);
            List<BoatWaterSnapshot> snapshots = new ArrayList<>();
            Set<BoatWaterMask.Chunk> examined = new HashSet<>();
            int captured = 0;
            for (BoatWaterMask.Chunk chunk : candidates) {
                if (captured >= maxChunks) {
                    break;
                }
                CaptureStatus status = captureIfNeeded(world, request, chunk,
                        snapshots, examined);
                if (status == CaptureStatus.UNAVAILABLE) {
                    return SnapshotBatch.unavailable();
                }
                if (status == CaptureStatus.CAPTURED) {
                    captured++;
                }
            }
            boolean complete = allCaptured(candidates, request.capturedChunks(), examined);
            return new SnapshotBatch(snapshots, examined, complete, true);
        }

        private CaptureStatus captureIfNeeded(World world,
                                              RouteRequest request,
                                              BoatWaterMask.Chunk chunk,
                                              List<BoatWaterSnapshot> snapshots,
                                              Set<BoatWaterMask.Chunk> examined) {
            if (request.capturedChunks().contains(chunk) || examined.contains(chunk)) {
                return CaptureStatus.SKIPPED;
            }
            if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                return CaptureStatus.UNAVAILABLE;
            }
            snapshots.add(captureChunk(world, chunk, clearBoatSpaceHeight));
            examined.add(chunk);
            return CaptureStatus.CAPTURED;
        }

        private boolean allCaptured(List<BoatWaterMask.Chunk> candidates,
                                    Set<BoatWaterMask.Chunk> captured,
                                    Set<BoatWaterMask.Chunk> examined) {
            for (BoatWaterMask.Chunk chunk : candidates) {
                if (!captured.contains(chunk) && !examined.contains(chunk)) {
                    return false;
                }
            }
            return true;
        }

        private List<BoatWaterMask.Chunk> prioritizedChunks(RouteRequest request,
                                                              long originChunkX,
                                                              long originChunkZ,
                                                              long destinationChunkX,
                                                              long destinationChunkZ) {
            List<BoatWaterMask.Chunk> result = new ArrayList<>();
            Set<BoatWaterMask.Chunk> seen = new HashSet<>();
            addCorridor(result, seen, originChunkX, originChunkZ,
                    destinationChunkX, destinationChunkZ);
            for (int distance = 0; distance <= request.searchChunkRadius()
                    && result.size() < searchChunkBudget; distance++) {
                addRing(result, seen, originChunkX, originChunkZ, distance);
                addRing(result, seen, destinationChunkX, destinationChunkZ, distance);
            }
            return result;
        }

        private void addCorridor(List<BoatWaterMask.Chunk> result,
                                 Set<BoatWaterMask.Chunk> seen,
                                 long originChunkX,
                                 long originChunkZ,
                                 long destinationChunkX,
                                 long destinationChunkZ) {
            long stepX = Long.compare(destinationChunkX, originChunkX);
            long stepZ = Long.compare(destinationChunkZ, originChunkZ);
            long x = originChunkX;
            long z = originChunkZ;
            while (x != destinationChunkX) {
                addChunk(result, seen, x, z);
                x += stepX;
            }
            while (z != destinationChunkZ) {
                addChunk(result, seen, x, z);
                z += stepZ;
            }
            addChunk(result, seen, destinationChunkX, destinationChunkZ);
        }

        private void addRing(List<BoatWaterMask.Chunk> result,
                             Set<BoatWaterMask.Chunk> seen,
                             long centerX,
                             long centerZ,
                             int distance) {
            long min = -distance;
            long max = distance;
            for (long offset = min; offset <= max; offset++) {
                addChunk(result, seen, centerX + offset, centerZ + min);
                addChunk(result, seen, centerX + offset, centerZ + max);
            }
            for (long offset = min + 1; offset < max; offset++) {
                addChunk(result, seen, centerX + min, centerZ + offset);
                addChunk(result, seen, centerX + max, centerZ + offset);
            }
        }

        private void addChunk(List<BoatWaterMask.Chunk> result,
                              Set<BoatWaterMask.Chunk> seen,
                              long chunkX,
                              long chunkZ) {
            if (result.size() >= searchChunkBudget
                    || chunkX < Integer.MIN_VALUE || chunkX > Integer.MAX_VALUE
                    || chunkZ < Integer.MIN_VALUE || chunkZ > Integer.MAX_VALUE) {
                return;
            }
            BoatWaterMask.Chunk chunk = new BoatWaterMask.Chunk((int) chunkX, (int) chunkZ);
            if (seen.add(chunk)) {
                result.add(chunk);
            }
        }

        private enum CaptureStatus {
            CAPTURED,
            SKIPPED,
            UNAVAILABLE
        }

        private static BoatWaterSnapshot captureChunk(World world,
                                                      BoatWaterMask.Chunk chunk,
                                                      int clearBoatSpaceHeight) {
            Set<BoatWaterMask.Cell> water = new HashSet<>();
            Set<BoatWaterMask.Cell> clear = new HashSet<>();
            int minX = chunk.x() * BoatWaterMask.CHUNK_SIZE;
            int minZ = chunk.z() * BoatWaterMask.CHUNK_SIZE;
            for (int localX = 0; localX < BoatWaterMask.CHUNK_SIZE; localX++) {
                for (int localZ = 0; localZ < BoatWaterMask.CHUNK_SIZE; localZ++) {
                    int x = minX + localX;
                    int z = minZ + localZ;
                    int y = world.getHighestBlockYAt(x, z);
                    Block surface = world.getBlockAt(x, y, z);
                    if (!isWater(surface.getType())) {
                        continue;
                    }
                    BoatWaterMask.Cell cell = new BoatWaterMask.Cell(x, y, z);
                    boolean isClear = true;
                    for (int offset = 1; offset <= clearBoatSpaceHeight; offset++) {
                        if (!world.getBlockAt(x, y + offset, z).isPassable()) {
                            isClear = false;
                            break;
                        }
                    }
                    if (isClear) {
                        water.add(cell);
                        clear.add(cell);
                    }
                }
            }
            return new BoatWaterSnapshot(world.getUID(), chunk.x(), chunk.z(),
                    new BoatWaterMask(chunk.x(), chunk.z(), water), clear);
        }

        private static boolean isWater(Material material) {
            return material == Material.WATER || material == Material.BUBBLE_COLUMN;
        }
    }
}

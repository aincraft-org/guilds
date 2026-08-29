package org.aincraft.guilds.territory.building.boat;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe cache for geometry-only route results.
 *
 * <p>Entries are tied to a world water revision. Revision changes reject stale
 * writes and make old entries invisible even if an invalidation races a worker.
 * Dependency chunks are indexes only; no route path is retained.</p>
 */
public final class BoatRouteCache {
    private final Map<UUID, AtomicLong> revisions = new ConcurrentHashMap<>();
    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();

    public long currentRevision(UUID worldId) {
        return revisions.computeIfAbsent(requireWorld(worldId), ignored -> new AtomicLong()).get();
    }
    public long revision(UUID worldId) {
        return currentRevision(worldId);
    }


    public Key key(UUID worldId, BoatWaterMask.Cell first, BoatWaterMask.Cell second) {
        return new Key(worldId, first, second, currentRevision(worldId));
    }

    public Optional<BoatRouteResult> get(UUID worldId,
                                         BoatWaterMask.Cell first,
                                         BoatWaterMask.Cell second) {
        return get(key(worldId, first, second));
    }

    public Optional<BoatRouteResult> get(Key key) {
        if (key == null) {
            return Optional.empty();
        }
        long current = currentRevision(key.worldId());
        if (key.waterRevision() != current) {
            return Optional.empty();
        }
        Entry entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.result());
    }

    public void put(UUID worldId,
                    BoatWaterMask.Cell first,
                    BoatWaterMask.Cell second,
                    BoatRouteResult result) {
        Key key = key(worldId, first, second);
        put(key, result, Set.of());
    }

    /**
     * Stores a result under a caller-supplied dependency set. The operation is
     * rejected when the key no longer names the current water revision.
     */
    public boolean put(Key key, BoatRouteResult result, Collection<BoatWaterMask.Chunk> dependencies) {
        if (key == null || result == null || !result.isCacheable()) {
            return false;
        }
        if (key.waterRevision() != currentRevision(key.worldId())) {
            return false;
        }
        Set<BoatWaterMask.Chunk> copy = new HashSet<>();
        copy.add(new BoatWaterMask.Chunk(
                Math.floorDiv(key.first().x(), BoatWaterMask.CHUNK_SIZE),
                Math.floorDiv(key.first().z(), BoatWaterMask.CHUNK_SIZE)));
        copy.add(new BoatWaterMask.Chunk(
                Math.floorDiv(key.second().x(), BoatWaterMask.CHUNK_SIZE),
                Math.floorDiv(key.second().z(), BoatWaterMask.CHUNK_SIZE)));
        if (dependencies != null) {
            for (BoatWaterMask.Chunk dependency : dependencies) {
                copy.add(java.util.Objects.requireNonNull(dependency, "dependency chunk"));
            }
        }
        entries.put(key, new Entry(result, Set.copyOf(copy)));
        return true;
    }

    public boolean put(Key key, BoatRouteResult result) {
        return put(key, result, Set.of());
    }

    public long invalidateChunk(UUID worldId, int chunkX, int chunkZ) {
        UUID requiredWorld = requireWorld(worldId);
        long nextRevision = revisions.computeIfAbsent(requiredWorld, ignored -> new AtomicLong())
                .incrementAndGet();
        BoatWaterMask.Chunk changed = new BoatWaterMask.Chunk(chunkX, chunkZ);
        entries.entrySet().removeIf(entry -> entry.getKey().worldId().equals(requiredWorld)
                && touches(entry.getValue().dependencies(), changed));
        return nextRevision;
    }

    public long invalidateChunk(UUID worldId, BoatWaterMask.Chunk chunk) {
        if (chunk == null) {
            throw new NullPointerException("chunk");
        }
        return invalidateChunk(worldId, chunk.x(), chunk.z());
    }

    public void clear() {
        entries.clear();
        revisions.clear();
    }

    public int size() {
        return entries.size();
    }

    private static boolean touches(Set<BoatWaterMask.Chunk> dependencies,
                                   BoatWaterMask.Chunk changed) {
        for (BoatWaterMask.Chunk dependency : dependencies) {
            if (Math.abs((long) dependency.x() - changed.x()) <= 1
                    && Math.abs((long) dependency.z() - changed.z()) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static UUID requireWorld(UUID worldId) {
        return java.util.Objects.requireNonNull(worldId, "worldId");
    }

    public record Key(UUID worldId,
                      BoatWaterMask.Cell first,
                      BoatWaterMask.Cell second,
                      long waterRevision) {
        public Key {
            requireWorld(worldId);
            java.util.Objects.requireNonNull(first, "first endpoint");
            java.util.Objects.requireNonNull(second, "second endpoint");
            if (compare(first, second) > 0) {
                BoatWaterMask.Cell swap = first;
                first = second;
                second = swap;
            }
            if (waterRevision < 0L) {
                throw new IllegalArgumentException("water revision cannot be negative");
            }
        }

        private static int compare(BoatWaterMask.Cell first, BoatWaterMask.Cell second) {
            int x = Integer.compare(first.x(), second.x());
            if (x != 0) {
                return x;
            }
            int y = Integer.compare(first.y(), second.y());
            if (y != 0) {
                return y;
            }
            return Integer.compare(first.z(), second.z());
        }
    }

    private record Entry(BoatRouteResult result, Set<BoatWaterMask.Chunk> dependencies) {
        private Entry {
            dependencies = Collections.unmodifiableSet(new HashSet<>(dependencies));
        }
    }
}

package org.aincraft.guilds.territory.registry;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.LookupResult;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.Zone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of territories with spatial resolve.
 * <p>
 * Pure domain — no Bukkit types. Thread-safe for concurrent reads after load;
 * mutations are synchronized on register/unregister/clear/replaceAll.
 * <p>
 * <strong>Rule:</strong> territories in the same world must not overlap
 * (shared edges/corners are allowed). Zone non-overlap is enforced on the
 * {@link Territory} model itself.
 */
public final class TerritoryRegistry {
    private final Map<String, Territory> byId = new ConcurrentHashMap<>();

    public TerritoryRegistry() {
    }

    public TerritoryRegistry(Collection<Territory> initial) {
        if (initial != null) {
            for (Territory t : initial) {
                register(t);
            }
        }
    }

    /**
     * Register or replace a territory by id.
     *
     * @throws IllegalArgumentException if its boundary overlaps another territory
     *                                  in the same world (other than itself when replacing)
     */
    public synchronized void register(Territory territory) {
        Objects.requireNonNull(territory, "territory");
        Optional<Territory> clash = findOverlap(territory, territory.id());
        if (clash.isPresent()) {
            throw new IllegalArgumentException(
                    "territories must not overlap: '" + territory.id()
                            + "' overlaps '" + clash.get().id()
                            + "' in world '" + territory.worldId() + "'"
            );
        }
        byId.put(territory.id(), territory);
    }

    public synchronized boolean unregister(String territoryId) {
        return byId.remove(territoryId) != null;
    }

    public synchronized void clear() {
        byId.clear();
    }

    /**
     * Replace all territories. Validates non-overlap on the full set (atomic:
     * registry unchanged if validation fails).
     */
    public synchronized void replaceAll(Collection<Territory> territories) {
        Map<String, Territory> next = new ConcurrentHashMap<>();
        if (territories != null) {
            for (Territory t : territories) {
                Objects.requireNonNull(t, "territory");
                for (Territory existing : next.values()) {
                    if (!existing.worldId().equals(t.worldId())) {
                        continue;
                    }
                    if (existing.id().equals(t.id())) {
                        throw new IllegalArgumentException("duplicate territory id in load set: " + t.id());
                    }
                    if (existing.boundary().overlaps(t.boundary())) {
                        throw new IllegalArgumentException(
                                "territories must not overlap: '" + t.id()
                                        + "' overlaps '" + existing.id()
                                        + "' in world '" + t.worldId() + "'"
                        );
                    }
                }
                if (next.put(t.id(), t) != null) {
                    throw new IllegalArgumentException("duplicate territory id in load set: " + t.id());
                }
            }
        }
        byId.clear();
        byId.putAll(next);
    }

    public Optional<Territory> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Territory> list() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    /**
     * Find another registered territory in the same world whose boundary overlaps
     * {@code candidate}, ignoring {@code excludeId} (for replace-in-place).
     */
    public Optional<Territory> findOverlap(Territory candidate, String excludeId) {
        Objects.requireNonNull(candidate, "candidate");
        for (Territory existing : byId.values()) {
            if (excludeId != null && existing.id().equals(excludeId)) {
                continue;
            }
            if (!existing.worldId().equals(candidate.worldId())) {
                continue;
            }
            if (existing.boundary().overlaps(candidate.boundary())) {
                return Optional.of(existing);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve which territory and zone type apply at the given world location.
     * <p>
     * With non-overlap registration, at most one territory should match.
     * If corrupt/legacy data still overlaps, the first match by id order is used.
     * Locations outside every territory yield {@link LookupResult#uncontained()}.
     */
    public LookupResult resolve(String worldId, int blockX, int blockZ) {
        Objects.requireNonNull(worldId, "worldId");
        String world = worldId.trim();
        List<Territory> candidates = new ArrayList<>();
        for (Territory t : byId.values()) {
            if (!t.worldId().equals(world)) {
                continue;
            }
            if (t.contains(blockX, blockZ)) {
                candidates.add(t);
            }
        }
        if (candidates.isEmpty()) {
            return LookupResult.uncontained();
        }
        candidates.sort((a, b) -> a.id().compareTo(b.id()));
        Territory chosen = candidates.get(0);
        Territory.ZoneResolution zone = chosen.resolveZone(blockX, blockZ);
        return LookupResult.of(chosen, zone);
    }

    public LookupResult resolve(String worldId, BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        return resolve(worldId, pos.x(), pos.z());
    }

    /**
     * Convenience: register a zone onto an existing territory (replaces by zone id).
     * Zone non-overlap is enforced by {@link Territory#withZone(Zone)}.
     */
    public synchronized void putZone(String territoryId, Zone zone) {
        Territory t = byId.get(territoryId);
        if (t == null) {
            throw new IllegalArgumentException("unknown territory: " + territoryId);
        }
        register(t.withZone(zone));
    }

    /**
     * Attach or replace the government of an existing territory.
     */
    public synchronized void putGovernment(String territoryId, Government government) {
        Territory t = byId.get(territoryId);
        if (t == null) {
            throw new IllegalArgumentException("unknown territory: " + territoryId);
        }
        register(t.withGovernment(government));
    }
}

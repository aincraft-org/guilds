package com.azoth.territory.registry;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.Zone;

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

    /**
     * Creates an empty territory registry.
     */
    public TerritoryRegistry() {
    }

    /**
     * Creates a registry populated with the supplied territories.
     *
     * @param initial territories to register, or {@code null} for an empty registry
     */
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
     * @param territory territory to register
     * @throws NullPointerException if {@code territory} is {@code null}
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

    /**
     * Removes a territory by id.
     *
     * @param territoryId id of the territory to remove
     * @return {@code true} if a territory was removed
     */
    public synchronized boolean unregister(String territoryId) {
        return byId.remove(territoryId) != null;
    }

    /**
     * Removes all registered territories.
     */
    public synchronized void clear() {
        byId.clear();
    }

    /**
     * Replace all territories. Validates non-overlap on the full set (atomic:
     * registry unchanged if validation fails).
     *
     * @param territories territories to load, or {@code null} for an empty registry
     * @throws NullPointerException if an element is {@code null}
     * @throws IllegalArgumentException if ids are duplicated or boundaries overlap
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

    /**
     * Finds a territory by id.
     *
     * @param id territory id
     * @return the matching territory, or empty if none is registered
     */
    public Optional<Territory> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Lists all registered territories.
     *
     * @return an immutable snapshot of registered territories
     */
    public List<Territory> list() {
        return List.copyOf(byId.values());
    }

    /**
     * Returns the number of registered territories.
     *
     * @return the registry size
     */
    public int size() {
        return byId.size();
    }

    /**
     * Find another registered territory in the same world whose boundary overlaps
     * {@code candidate}, ignoring {@code excludeId} (for replace-in-place).
     *
     * @param candidate territory whose boundary is checked
     * @param excludeId id to ignore, or {@code null}
     * @return an overlapping territory, or empty if none exists
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
     *
     * @param worldId world identifier
     * @param blockX block x-coordinate
     * @param blockZ block z-coordinate
     * @return the lookup result for the location
     * @throws NullPointerException if {@code worldId} is {@code null}
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

    /**
     * Resolves a block position within a world.
     *
     * @param worldId world identifier
     * @param pos block position
     * @return the lookup result for the location
     * @throws NullPointerException if {@code pos} is {@code null}
     */
    public LookupResult resolve(String worldId, BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        return resolve(worldId, pos.x(), pos.z());
    }

    /**
     * Convenience: register a zone onto an existing territory (replaces by zone id).
     * Zone non-overlap is enforced by {@link Territory#withZone(Zone)}.
     *
     * @param territoryId id of the territory to update
     * @param zone zone to attach
     * @throws IllegalArgumentException if the territory does not exist
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
     *
     * @param territoryId id of the territory to update
     * @param government government to attach
     * @throws IllegalArgumentException if the territory does not exist
     */
    public synchronized void putGovernment(String territoryId, Government government) {
        Territory t = byId.get(territoryId);
        if (t == null) {
            throw new IllegalArgumentException("unknown territory: " + territoryId);
        }
        register(t.withGovernment(government));
    }
}

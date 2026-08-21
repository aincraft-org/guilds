package org.aincraft.guilds.territory.registry;

import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe directory of settlement facility locations. Records are immutable;
 * each mutation validates and replaces the complete map atomically.
 */
public final class FacilityRegistry {
    private final TerritoryRegistry territories;
    private volatile Map<String, SettlementFacility> byId = Map.of();

    public FacilityRegistry(TerritoryRegistry territories) {
        this.territories = Objects.requireNonNull(territories, "territories");
    }

    public synchronized void register(SettlementFacility facility) {
        Objects.requireNonNull(facility, "facility");
        if (byId.containsKey(facility.id())) {
            throw new IllegalArgumentException("duplicate facility id: " + facility.id());
        }
        Map<String, SettlementFacility> next = new LinkedHashMap<>(byId);
        next.put(facility.id(), facility);
        validateAll(next.values());
        byId = immutable(next);
    }

    public synchronized boolean unregister(String id) {
        if (!byId.containsKey(id)) {
            return false;
        }
        Map<String, SettlementFacility> next = new LinkedHashMap<>(byId);
        next.remove(id);
        byId = immutable(next);
        return true;
    }

    public Optional<SettlementFacility> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<SettlementFacility> list() {
        return List.copyOf(byId.values());
    }

    public List<SettlementFacility> list(String territoryId, FacilityType type) {
        Objects.requireNonNull(type, "type");
        if (territoryId == null || territoryId.isBlank()) {
            return List.of();
        }
        String normalized = territoryId.trim();
        return byId.values().stream()
                .filter(facility -> facility.territoryId().equals(normalized)
                        && facility.type() == type)
                .toList();
    }

    public FacilityRegistry copy() {
        FacilityRegistry copy = new FacilityRegistry(territories);
        copy.replaceAll(byId.values());
        return copy;
    }

    public Optional<SettlementFacility> resolve(String worldId, int x, int y, int z) {
        if (worldId == null) {
            return Optional.empty();
        }
        String normalizedWorld = worldId.trim();
        for (SettlementFacility facility : byId.values()) {
            if (facility.isAt(normalizedWorld, x, y, z)) {
                return Optional.of(facility);
            }
        }
        return Optional.empty();
    }
    /**
     * Resolves a facility whose physical anchor is within the supplied bounded
     * block radius.  The nearest facility is returned, with ties broken by id
     * for deterministic event handling.
     */
    public Optional<SettlementFacility> resolveNearby(String worldId, int x, int y, int z, int radius) {
        if (worldId == null || radius < 0) {
            return Optional.empty();
        }
        SettlementFacility nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (SettlementFacility facility : byId.values()) {
            if (!facility.worldId().equals(worldId.trim())) {
                continue;
            }
            long dx = (long) facility.x() - x;
            long dy = (long) facility.y() - y;
            long dz = (long) facility.z() - z;
            if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) > radius) {
                continue;
            }
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < nearestDistance
                    || (distance == nearestDistance && (nearest == null || facility.id().compareTo(nearest.id()) < 0))) {
                nearest = facility;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }


    public synchronized void replaceAll(Collection<SettlementFacility> facilities) {
        Map<String, SettlementFacility> next = new LinkedHashMap<>();
        if (facilities != null) {
            for (SettlementFacility facility : facilities) {
                Objects.requireNonNull(facility, "facility");
                if (next.put(facility.id(), facility) != null) {
                    throw new IllegalArgumentException("duplicate facility id: " + facility.id());
                }
            }
        }
        validateAll(next.values());
        byId = immutable(next);
    }

    private void validateAll(Collection<SettlementFacility> facilities) {
        List<SettlementFacility> list = List.copyOf(facilities);
        for (SettlementFacility facility : list) {
            Territory territory = territories.get(facility.territoryId()).orElseThrow(
                    () -> new IllegalArgumentException("unknown territory: " + facility.territoryId()));
            if (!territory.worldId().equals(facility.worldId())
                    || !territories.resolve(facility.worldId(), facility.x(), facility.z())
                    .territoryId().filter(facility.territoryId()::equals).isPresent()) {
                throw new IllegalArgumentException(
                        "facility location is outside territory '" + facility.territoryId() + "': " + facility.id());
            }
        }
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (sameLocation(list.get(i), list.get(j))) {
                    throw new IllegalArgumentException(
                            "duplicate facility location: '" + list.get(i).id()
                                    + "' and '" + list.get(j).id() + "'");
                }
            }
        }
    }

    private static boolean sameLocation(SettlementFacility first, SettlementFacility second) {
        return first.isAt(second.worldId(), second.x(), second.y(), second.z());
    }

    private static Map<String, SettlementFacility> immutable(Map<String, SettlementFacility> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}

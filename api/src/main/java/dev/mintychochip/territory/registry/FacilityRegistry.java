package dev.mintychochip.territory.registry;

import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;

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

    /**
     * Creates a facility registry backed by the supplied territory registry.
     *
     * @param territories registry used to validate facility locations
     * @throws NullPointerException if {@code territories} is {@code null}
     */
    public FacilityRegistry(TerritoryRegistry territories) {
        this.territories = Objects.requireNonNull(territories, "territories");
    }

    /**
     * Registers a facility.
     *
     * @param facility facility to register
     * @throws NullPointerException if {@code facility} is {@code null}
     * @throws IllegalArgumentException if its id or location is duplicated, or its location is outside its territory
     */
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

    /**
     * Removes a facility by id.
     *
     * @param id facility id
     * @return {@code true} if a facility was removed
     */
    public synchronized boolean unregister(String id) {
        if (!byId.containsKey(id)) {
            return false;
        }
        Map<String, SettlementFacility> next = new LinkedHashMap<>(byId);
        next.remove(id);
        byId = immutable(next);
        return true;
    }

    /**
     * Finds a facility by id.
     *
     * @param id facility id
     * @return the matching facility, or empty if none is registered
     */
    public Optional<SettlementFacility> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Lists all registered facilities.
     *
     * @return an immutable snapshot of registered facilities
     */
    public List<SettlementFacility> list() {
        return List.copyOf(byId.values());
    }

    /**
     * Lists facilities of a type in a territory.
     *
     * @param territoryId territory id, or blank for no results
     * @param type facility type
     * @return matching facilities
     * @throws NullPointerException if {@code type} is {@code null}
     */
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

    /**
     * Copies this registry and its current facilities.
     *
     * @return an independent registry containing the same facilities
     */
    public FacilityRegistry copy() {
        FacilityRegistry copy = new FacilityRegistry(territories);
        copy.replaceAll(byId.values());
        return copy;
    }

    /**
     * Resolves a facility at a block location.
     *
     * @param worldId world identifier, or {@code null}
     * @param x block x-coordinate
     * @param y block y-coordinate
     * @param z block z-coordinate
     * @return the facility at the location, or empty if none matches
     */
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
     * Replaces all registered facilities after validating the complete set.
     *
     * @param facilities facilities to load, or {@code null} for an empty registry
     * @throws NullPointerException if an element is {@code null}
     * @throws IllegalArgumentException if ids or locations are duplicated, or a location is invalid
     */
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

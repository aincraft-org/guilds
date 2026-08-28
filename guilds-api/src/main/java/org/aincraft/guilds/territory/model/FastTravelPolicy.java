package org.aincraft.guilds.territory.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable per-territory fast-travel quota and boundary policy. */
public record FastTravelPolicy(
        Map<FacilityType, Integer> facilityQuotas,
        Set<FastTravelMode> crossTerritoryModes) {

    public static final int UNLIMITED_QUOTA = Integer.MAX_VALUE;

    public FastTravelPolicy {
        facilityQuotas = copyQuotas(facilityQuotas);
        crossTerritoryModes = copyCrossTerritoryModes(crossTerritoryModes);
    }

    /**
     * Creates the backward-compatible policy for territories that predate
     * fast-travel policy persistence.
     */
    public static FastTravelPolicy defaults() {
        Map<FacilityType, Integer> quotas = new LinkedHashMap<>();
        quotas.put(FacilityType.WAYSTONE, UNLIMITED_QUOTA);
        quotas.put(FacilityType.GUILD_CRYSTAL, UNLIMITED_QUOTA);
        quotas.put(FacilityType.TELEPORT_TERMINAL, UNLIMITED_QUOTA);
        quotas.put(FacilityType.BOAT, UNLIMITED_QUOTA);
        quotas.put(FacilityType.AIRSHIP, UNLIMITED_QUOTA);
        return new FastTravelPolicy(quotas, Set.of(
                FastTravelMode.CRYSTAL,
                FastTravelMode.BOAT,
                FastTravelMode.AIRSHIP));
    }

    /** Returns the configured quota, or unlimited when no quota is configured. */
    public int quotaFor(FacilityType type) {
        Objects.requireNonNull(type, "type");
        return facilityQuotas.getOrDefault(type, UNLIMITED_QUOTA);
    }

    /** Returns whether this policy permits the mode to cross its boundary. */
    public boolean allowsCrossTerritory(FastTravelMode mode) {
        Objects.requireNonNull(mode, "mode");
        return crossTerritoryModes.contains(mode);
    }

    private static Map<FacilityType, Integer> copyQuotas(Map<FacilityType, Integer> quotas) {
        Objects.requireNonNull(quotas, "facilityQuotas");
        Map<FacilityType, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<FacilityType, Integer> entry : quotas.entrySet()) {
            FacilityType type = Objects.requireNonNull(entry.getKey(), "facilityQuotas contains null key");
            Integer quota = Objects.requireNonNull(entry.getValue(), "facilityQuotas contains null value");
            if (quota < 0) {
                throw new IllegalArgumentException("facility quota must not be negative: " + type);
            }
            if (FastTravelMode.fromFacilityType(type).isEmpty()) {
                throw new IllegalArgumentException("facility type does not support fast travel: " + type);
            }
            copy.put(type, quota);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<FastTravelMode> copyCrossTerritoryModes(Set<FastTravelMode> modes) {
        Objects.requireNonNull(modes, "crossTerritoryModes");
        Set<FastTravelMode> copy = new LinkedHashSet<>();
        for (FastTravelMode mode : modes) {
            mode = Objects.requireNonNull(mode, "crossTerritoryModes contains null mode");
            if (mode == FastTravelMode.LOCAL_TERMINAL) {
                throw new IllegalArgumentException("LOCAL_TERMINAL cannot cross territory boundaries");
            }
            copy.add(mode);
        }
        return Collections.unmodifiableSet(copy);
    }
}

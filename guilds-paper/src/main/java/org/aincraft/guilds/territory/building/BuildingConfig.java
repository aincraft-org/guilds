package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.FacilityType;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record BuildingConfig(
        long placementTimeoutMillis,
        Map<FacilityType, Set<Material>> anchorMaterials,
        long waystoneWarmupTicks,
        long waystoneCooldownMillis,
        TransportGeometry transportGeometry
) {
    public BuildingConfig(long placementTimeoutMillis,
                           Map<FacilityType, Set<Material>> anchorMaterials,
                           long waystoneWarmupTicks,
                           long waystoneCooldownMillis) {
        this(placementTimeoutMillis, anchorMaterials, waystoneWarmupTicks,
                waystoneCooldownMillis, TransportGeometry.defaults());
    }

    public BuildingConfig {
        if (placementTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("placement timeout must be positive");
        }
        if (waystoneWarmupTicks < 0L || waystoneCooldownMillis < 0L) {
            throw new IllegalArgumentException("waystone timing cannot be negative");
        }
        Objects.requireNonNull(anchorMaterials, "anchorMaterials");
        Objects.requireNonNull(transportGeometry, "transportGeometry");
        EnumMap<FacilityType, Set<Material>> copy = new EnumMap<>(FacilityType.class);
        anchorMaterials.forEach((type, materials) -> {
            Objects.requireNonNull(type, "anchor material facility type");
            copy.put(type, Set.copyOf(Objects.requireNonNull(materials,
                    "anchor materials for " + type)));
        });
        anchorMaterials = Map.copyOf(copy);
    }

    public Set<Material> anchorMaterials(FacilityType type) {
        return anchorMaterials.getOrDefault(type, Set.of());
    }

    public boolean supports(FacilityType type) {
        return !anchorMaterials(type).isEmpty();
    }

    /** Immutable geometry limits used by transport endpoint validation and route search. */
    public record TransportGeometry(
            int boatEntryRadius,
            int boatEntryWidth,
            int clearBoatSpaceHeight,
            int searchChunkRadius,
            int searchChunkBudget,
            int airshipPlatformRadius,
            int airshipVerticalClearanceHeight
    ) {
        public TransportGeometry {
            if (boatEntryRadius <= 0 || boatEntryWidth <= 0 || clearBoatSpaceHeight <= 0
                    || searchChunkRadius <= 0 || searchChunkBudget <= 0
                    || airshipPlatformRadius <= 0 || airshipVerticalClearanceHeight <= 0) {
                throw new IllegalArgumentException("transport geometry values must be positive");
            }
        }

        public static TransportGeometry defaults() {
            return new TransportGeometry(2, 3, 2, 32, 256, 2, 16);
        }

    }
}

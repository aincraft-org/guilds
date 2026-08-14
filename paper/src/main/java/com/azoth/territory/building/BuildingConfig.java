package com.azoth.territory.building;

import com.azoth.territory.model.FacilityType;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record BuildingConfig(
        long placementTimeoutMillis,
        Map<FacilityType, Set<Material>> anchorMaterials,
        long waystoneWarmupTicks,
        long waystoneCooldownMillis
) {
    public BuildingConfig {
        if (placementTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("placement timeout must be positive");
        }
        if (waystoneWarmupTicks < 0L || waystoneCooldownMillis < 0L) {
            throw new IllegalArgumentException("waystone timing cannot be negative");
        }
        EnumMap<FacilityType, Set<Material>> copy = new EnumMap<>(FacilityType.class);
        anchorMaterials.forEach((type, materials) -> copy.put(type, Set.copyOf(materials)));
        anchorMaterials = Map.copyOf(copy);
    }

    public Set<Material> anchorMaterials(FacilityType type) {
        return anchorMaterials.getOrDefault(type, Set.of());
    }

    public boolean supports(FacilityType type) {
        return !anchorMaterials(type).isEmpty();
    }
}

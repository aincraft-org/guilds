package org.aincraft.subregion;

import java.util.Objects;

/**
 * Represents a volume limit for a specific subregion type.
 * Limits are applied per guild - each guild can have up to maxTotalVolume
 * blocks across all regions of this type.
 */
public record RegionTypeLimit(
        String typeId,
        long maxTotalVolume
) {
    public RegionTypeLimit {
        Objects.requireNonNull(typeId, "Type ID cannot be null");
        if (maxTotalVolume <= 0) {
            throw new IllegalArgumentException("Max total volume must be positive");
        }
    }
}

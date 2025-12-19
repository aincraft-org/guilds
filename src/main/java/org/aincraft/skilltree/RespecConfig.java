package org.aincraft.skilltree;

import org.bukkit.Material;
import java.util.Objects;

/**
 * Immutable configuration for skill tree respecs.
 * Loaded from config and specifies the cost to reset skill tree.
 * Single Responsibility: Represent respec configuration.
 *
 * @param enabled whether respecs are enabled
 * @param material the material cost (consumed from guild vault)
 * @param amount the quantity of material required
 */
public record RespecConfig(
    boolean enabled,
    Material material,
    int amount
) {
    /**
     * Compact constructor that validates the configuration.
     */
    public RespecConfig {
        if (enabled) {
            Objects.requireNonNull(material, "Material cannot be null when respec is enabled");
            if (amount <= 0) {
                throw new IllegalArgumentException("Respec amount must be positive when enabled");
            }
        }
    }
}

package org.aincraft.guilds.models;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Enum representing resource types used in the guild upgrade system
 * Consolidates resource type logic previously duplicated across ResourceContribution and GuildResource
 */
public enum ResourceType {

    DIAMOND("Diamond", "DIAMOND", 1.0),
    GOLD("Gold Ingot", "GOLD_INGOT", 0.5),
    IRON("Iron Ingot", "IRON_INGOT", 0.25),
    EMERALD("Emerald", "EMERALD", 2.0),
    EXPERIENCE("Experience Bottle", "EXPERIENCE_BOTTLE", 0.1);

    private final String displayName;
    private final String materialName;
    private final double diamondEquivalent;

    ResourceType(String displayName, String materialName, double diamondEquivalent) {
        this.displayName = displayName;
        this.materialName = materialName;
        this.diamondEquivalent = diamondEquivalent;
    }

    /**
     * Get the user-friendly display name for this resource
     * @return Display name (e.g., "Gold Ingot")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the Minecraft material name for this resource
     * @return Material name constant (e.g., "GOLD_INGOT")
     */
    public String getMaterialName() {
        return materialName;
    }

    /**
     * Get the diamond equivalent value of this resource
     * Used for comparing relative resource values
     * @return Diamond equivalent multiplier
     */
    public double getDiamondEquivalent() {
        return diamondEquivalent;
    }

    /**
     * Get the normalized lowercase name of this resource type
     * @return Lowercase enum name (e.g., "diamond", "gold")
     */
    public String getNormalizedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parse a string into a ResourceType enum
     * Case-insensitive matching on enum, material, and display names
     * @param type String representation of resource type
     * @return Optional containing the ResourceType, or empty if not found
     */
    public static Optional<ResourceType> fromString(String type) {
        if (type == null || type.trim().isEmpty()) {
            return Optional.empty();
        }

        String trimmed = type.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);

        return Arrays.stream(values())
                .filter(rt -> rt.name().equalsIgnoreCase(normalized)
                        || rt.getNormalizedName().equals(normalized)
                        || rt.materialName.equalsIgnoreCase(normalized)
                        || rt.displayName.equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Check if a string represents a valid resource type
     * @param type String to validate
     * @return True if valid resource type
     */
    public static boolean isValid(String type) {
        return fromString(type).isPresent();
    }

    /**
     * Calculate the diamond equivalent value for a given amount of this resource
     * @param amount Amount of this resource
     * @return Diamond equivalent value
     */
    public double calculateDiamondEquivalent(int amount) {
        return amount * diamondEquivalent;
    }

    /**
     * Get a formatted description of a resource amount
     * @param amount Amount of the resource
     * @return Formatted string (e.g., "50 Gold Ingot")
     */
    public String formatAmount(int amount) {
        return String.format("%d %s", amount, displayName);
    }

    /**
     * Get resource storage level description based on amount
     * @param amount Current amount
     * @return Storage level description ("Empty", "Low", "Well-stocked")
     */
    public String getStorageLevel(int amount) {
        if (amount <= 0) {
            return "Empty";
        }

        int threshold = getSignificantThreshold();
        if (amount >= threshold) {
            return "Well-stocked";
        }

        return "Low";
    }

    /**
     * Get the threshold for "significant amount" based on resource type
     * @return Threshold amount
     */
    public int getSignificantThreshold() {
        switch (this) {
            case DIAMOND:
            case EMERALD:
                return 50;
            case EXPERIENCE:
                return 200;
            default: // GOLD, IRON
                return 100;
        }
    }

    /**
     * Check if an amount is considered significant for this resource type
     * @param amount Amount to check
     * @return True if amount meets or exceeds the significant threshold
     */
    public boolean isSignificantAmount(int amount) {
        return amount >= getSignificantThreshold();
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - Diamond Equivalent: %.1fx",
                displayName, materialName, diamondEquivalent);
    }
}

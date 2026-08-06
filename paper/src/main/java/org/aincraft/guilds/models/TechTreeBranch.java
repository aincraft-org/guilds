package org.aincraft.guilds.models;

/**
 * Represents a branch in the tech tree.
 */
public enum TechTreeBranch {
    INFRASTRUCTURE("Infrastructure", "§b"),
    DEFENSE("Defense", "§c"),
    COMMERCE("Commerce", "§6"),
    CULTURE("Culture", "§d");

    private final String displayName;
    private final String colorCode;

    TechTreeBranch(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getColoredName() {
        return colorCode + displayName + "§r";
    }

    /**
     * Parse a string into a TechTreeBranch enum (case-insensitive)
     */
    public static TechTreeBranch fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

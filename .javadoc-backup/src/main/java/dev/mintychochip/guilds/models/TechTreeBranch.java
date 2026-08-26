package dev.mintychochip.guilds.models;

/**
 * Represents a branch in the tech tree.
 */
public enum TechTreeBranch {
    /** The infrastructure constant. */
    INFRASTRUCTURE("Infrastructure", "§b"),
    /** The defense constant. */
    DEFENSE("Defense", "§c"),
    /** The commerce constant. */
    COMMERCE("Commerce", "§6"),
    /** The culture constant. */
    CULTURE("Culture", "§d");

    /** The display name. */
    private final String displayName;
    /** The color code. */
    private final String colorCode;

    /**
     * Creates a new  instance.
     * @param displayName the display name
     * @param colorCode the color code
     */
    TechTreeBranch(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    /**
     * Returns the display name.
     * @return the result
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the color code.
     * @return the result
     */
    public String getColorCode() {
        return colorCode;
    }

    /**
     * Returns the colored name.
     * @return the result
     */
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

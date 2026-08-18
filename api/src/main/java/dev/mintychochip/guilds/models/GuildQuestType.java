package dev.mintychochip.guilds.models;

/** Defines the values of guild quest type. */
public enum GuildQuestType {
    /** The resource collection constant. */
    RESOURCE_COLLECTION("Resource Collection", "Collect specific resources for your guild"),
    /** The building constant. */
    BUILDING("Building", "Construct new buildings within your guild"),
    /** The population constant. */
    POPULATION("Population", "Grow your guild's population"),
    /** The economic constant. */
    ECONOMIC("Economic", "Generate economic value for your guild"),
    /** The social constant. */
    SOCIAL("Social", "Engage with other players and communities");

    /** The display name. */
    private final String displayName;
    /** The description. */
    private final String description;

    /**
     * Creates a new  instance.
     * @param displayName the display name
     * @param description the description
     */
    GuildQuestType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Returns the display name.
     * @return the result
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the description.
     * @return the result
     */
    public String getDescription() {
        return description;
    }
}
package org.aincraft.guilds.models;

public enum GuildQuestType {
    RESOURCE_COLLECTION("Resource Collection", "Collect specific resources for your guild"),
    BUILDING("Building", "Construct new buildings within your guild"),
    POPULATION("Population", "Grow your guild's population"),
    ECONOMIC("Economic", "Generate economic value for your guild"),
    SOCIAL("Social", "Engage with other players and communities");

    private final String displayName;
    private final String description;

    GuildQuestType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
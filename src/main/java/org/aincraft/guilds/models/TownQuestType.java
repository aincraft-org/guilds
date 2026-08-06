package org.aincraft.guilds.models;

public enum TownQuestType {
    RESOURCE_COLLECTION("Resource Collection", "Collect specific resources for your town"),
    BUILDING("Building", "Construct new buildings within your town"),
    POPULATION("Population", "Grow your town's population"),
    ECONOMIC("Economic", "Generate economic value for your town"),
    SOCIAL("Social", "Engage with other players and communities");

    private final String displayName;
    private final String description;

    TownQuestType(String displayName, String description) {
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
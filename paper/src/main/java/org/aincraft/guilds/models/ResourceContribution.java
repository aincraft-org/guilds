package org.aincraft.guilds.models;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a resource contribution made by a player to their guild's upgrade progress
 */
public class ResourceContribution {

    private String id;
    private String guildId;
    private UUID contributorUuid;
    private ResourceType resourceType;
    private int amount;
    private LocalDateTime contributionTime;

    /**
     * Default constructor for database mapping
     */
    public ResourceContribution() {
        this.contributionTime = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new resource contribution
     * @param guildId Guild ID
     * @param contributorUuid Contributor's UUID
     * @param resourceType Type of resource
     * @param amount Amount contributed
     */
    public ResourceContribution(String guildId, UUID contributorUuid, ResourceType resourceType, int amount) {
        this();
        this.id = UUID.randomUUID().toString();
        this.guildId = guildId;
        this.contributorUuid = contributorUuid;
        this.resourceType = resourceType;
        this.setAmount(amount);
    }

    /**
     * Constructor with ID for database reconstruction
     * @param id Contribution ID
     * @param guildId Guild ID
     * @param contributorUuid Contributor's UUID
     * @param resourceType Type of resource
     * @param amount Amount contributed
     * @param contributionTime When the contribution was made
     */
    public ResourceContribution(String id, String guildId, UUID contributorUuid,
                                ResourceType resourceType, int amount, LocalDateTime contributionTime) {
        this.id = id;
        this.guildId = guildId;
        this.contributorUuid = contributorUuid;
        this.resourceType = resourceType;
        this.setAmount(amount);
        this.contributionTime = contributionTime;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public UUID getContributorUuid() {
        return contributorUuid;
    }

    public void setContributorUuid(UUID contributorUuid) {
        this.contributorUuid = contributorUuid;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }

    /**
     * Set resource type from string (for database deserialization)
     * @param resourceTypeStr String representation of resource type
     */
    public void setResourceTypeFromString(String resourceTypeStr) {
        this.resourceType = ResourceType.fromString(resourceTypeStr).orElse(null);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    public LocalDateTime getContributionTime() {
        return contributionTime;
    }

    public void setContributionTime(LocalDateTime contributionTime) {
        this.contributionTime = contributionTime;
    }

    // Business methods

    /**
     * Get the display name for the resource type
     * @return Formatted resource name
     */
    public String getResourceDisplayName() {
        return resourceType != null ? resourceType.getDisplayName() : "Unknown";
    }

    /**
     * Get the Minecraft material name for this resource
     * @return Minecraft material name
     */
    public String getMaterialName() {
        return resourceType != null ? resourceType.getMaterialName() : null;
    }

    /**
     * Check if this contribution was made recently (within last 24 hours)
     * @return True if contribution is recent
     */
    public boolean isRecent() {
        return contributionTime.isAfter(LocalDateTime.now().minusDays(1));
    }

    /**
     * Check if this contribution was made within the last week
     * @return True if contribution is recent (within 7 days)
     */
    public boolean isThisWeek() {
        return contributionTime.isAfter(LocalDateTime.now().minusWeeks(1));
    }

    /**
     * Get a formatted description of the contribution
     * @return Formatted contribution description
     */
    public String getFormattedDescription() {
        return String.format("%d %s contributed by %s on %s",
                amount, getResourceDisplayName(), contributorUuid,
                contributionTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        );
    }

    /**
     * Get a short description for UI display
     * @return Short contribution description
     */
    public String getShortDescription() {
        return String.format("%d %s", amount, getResourceDisplayName());
    }

    /**
     * Calculate the "diamond equivalent" value of this contribution
     * Used for comparing different resource contributions
     * @return Diamond equivalent value
     */
    public double getDiamondEquivalent() {
        return resourceType != null ? resourceType.calculateDiamondEquivalent(amount) : 0.0;
    }

    /**
     * Check if this is a valid resource type
     * @return True if resource type is valid
     */
    public boolean isValidResourceType() {
        return resourceType != null;
    }

    /**
     * Validate this contribution object
     * @return True if the contribution is valid
     */
    public boolean isValid() {
        return id != null && !id.isEmpty() &&
               guildId != null && !guildId.isEmpty() &&
               contributorUuid != null &&
               isValidResourceType() &&
               amount > 0 &&
               contributionTime != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ResourceContribution that = (ResourceContribution) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ResourceContribution{" +
                "id='" + id + '\'' +
                ", townId='" + guildId + '\'' +
                ", contributorUuid=" + contributorUuid +
                ", resourceType=" + (resourceType != null ? resourceType.name() : "null") +
                ", amount=" + amount +
                ", contributionTime=" + contributionTime +
                '}';
    }
}
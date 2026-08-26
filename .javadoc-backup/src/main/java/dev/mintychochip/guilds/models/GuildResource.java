package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;

/**
 * Represents a guild's resource bank storage for guild upgrades
 */
public class GuildResource {

    /** The id. */
    private String id;
    /** The guild id. */
    private String guildId;
    /** The resource type. */
    private ResourceType resourceType;
    /** The amount. */
    private int amount;
    /** The last updated. */
    private LocalDateTime lastUpdated;

    /**
     * Default constructor for database mapping
     */
    public GuildResource() {
        this.amount = 0;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Constructor for creating a new guild resource entry
     * @param guildId Guild ID
     * @param resourceType Type of resource
     */
    public GuildResource(String guildId, ResourceType resourceType) {
        this();
        this.id = java.util.UUID.randomUUID().toString();
        this.guildId = guildId;
        this.resourceType = resourceType;
    }

    /**
     * Constructor with amount for database reconstruction
     * @param id Resource ID
     * @param guildId Guild ID
     * @param resourceType Type of resource
     * @param amount Current amount
     * @param lastUpdated Last update timestamp
     */
    public GuildResource(String id, String guildId, ResourceType resourceType, int amount, LocalDateTime lastUpdated) {
        this.id = id;
        this.guildId = guildId;
        this.resourceType = resourceType;
        this.amount = Math.max(0, amount);
        this.lastUpdated = lastUpdated != null ? lastUpdated : LocalDateTime.now();
    }

    // Getters and Setters
    /**
     * Returns the id.
     * @return the result
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id.
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the guild id.
     * @return the result
     */
    public String getGuildId() {
        return guildId;
    }

    /**
     * Sets the guild id.
     * @param guildId the guild id
     */
    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    /**
     * Returns the resource type.
     * @return the result
     */
    public ResourceType getResourceType() {
        return resourceType;
    }

    /**
     * Sets the resource type.
     * @param resourceType the resource type
     */
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

    /**
     * Returns the amount.
     * @return the result
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Sets the amount.
     * @param amount the amount
     */
    public void setAmount(int amount) {
        this.amount = Math.max(0, amount);
        updateLastModified();
    }

    /**
     * Returns the last updated.
     * @return the result
     */
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets the last updated.
     * @param lastUpdated the last updated
     */
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Business methods

    /**
     * Add resources to this resource bank
     * @param amountToAdd Amount to add (must be positive)
     * @return New total amount
     */
    public int addResources(int amountToAdd) {
        if (amountToAdd > 0) {
            this.amount += amountToAdd;
            updateLastModified();
        }
        return this.amount;
    }

    /**
     * Remove resources from this resource bank
     * @param amountToRemove Amount to remove (must be positive)
     * @return True if resources were removed, false if insufficient amount
     */
    public boolean removeResources(int amountToRemove) {
        if (amountToRemove <= 0) return false;

        if (this.amount >= amountToRemove) {
            this.amount -= amountToRemove;
            updateLastModified();
            return true;
        }
        return false;
    }

    /**
     * Check if the resource bank has sufficient resources
     * @param requiredAmount Required amount
     * @return True if sufficient resources are available
     */
    public boolean hasSufficientResources(int requiredAmount) {
        return this.amount >= requiredAmount;
    }

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
     * Check if this resource was updated recently (within last hour)
     * @return True if recently updated
     */
    public boolean isRecentlyUpdated() {
        return lastUpdated.isAfter(LocalDateTime.now().minusHours(1));
    }

    /**
     * Check if this resource was updated within the last day
     * @return True if updated today
     */
    public boolean isUpdatedToday() {
        return lastUpdated.toLocalDate().equals(LocalDateTime.now().toLocalDate());
    }

    /**
     * Get a formatted description of this resource
     * @return Formatted resource description
     */
    public String getFormattedDescription() {
        return String.format("%d %s (Last updated: %s)",
                amount, getResourceDisplayName(),
                lastUpdated.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
        );
    }

    /**
     * Get a short description for UI display
     * @return Short resource description
     */
    public String getShortDescription() {
        return String.format("%d %s", amount, getResourceDisplayName());
    }

    /**
     * Calculate the "diamond equivalent" value of this resource
     * Used for comparing different resource values
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
     * Check if this resource bank is empty
     * @return True if amount is 0
     */
    public boolean isEmpty() {
        return amount <= 0;
    }

    /**
     * Check if this resource bank has a significant amount
     * @return True if has significant amount
     */
    public boolean hasSignificantAmount() {
        return resourceType != null && resourceType.isSignificantAmount(amount);
    }

    /**
     * Validate this guild resource object
     * @return True if the resource is valid
     */
    public boolean isValid() {
        return id != null && !id.isEmpty() &&
               guildId != null && !guildId.isEmpty() &&
               isValidResourceType() &&
               amount >= 0 &&
               lastUpdated != null;
    }

    /**
     * Update the last modified timestamp to now
     */
    private void updateLastModified() {
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Get the resource storage level description
     * @return Storage level description
     */
    public String getStorageLevel() {
        return resourceType != null ? resourceType.getStorageLevel(amount) : "Unknown";
    }

    /**
     * Indicates whether another object is equal to this one.
     * @param obj the obj
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GuildResource that = (GuildResource) obj;
        return guildId.equals(that.guildId) &&
               resourceType == that.resourceType;
    }

    /** Returns a hash code for this object. */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(guildId, resourceType);
    }

    /** Returns a string representation of this object. */
    @Override
    public String toString() {
        return "GuildResource{" +
                "id='" + id + '\'' +
                ", guildId='" + guildId + '\'' +
                ", resourceType=" + (resourceType != null ? resourceType.name() : "null") +
                ", amount=" + amount +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
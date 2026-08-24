package org.aincraft.guilds.services;

import org.aincraft.guilds.models.ResourceContribution;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for guild resource management and contribution tracking
 */
public interface ResourceService {

    /**
     * Get a guild's resource bank for a specific resource type
     * @param guildId Guild ID
     * @param resourceType Resource type (diamond, gold, iron, emerald, experience)
     * @return Guild resource if found
     */
    Optional<GuildResource> getGuildResource(String guildId, String resourceType);

    /**
     * Get all resources in a guild's resource bank
     * @param guildId Guild ID
     * @return List of guild resources
     */
    List<GuildResource> getGuildResources(String guildId);

    /**
     * Get resources for a guild by a player
     * @param guild Guild to get resources for
     * @return Map of resource types to guild resources
     */
    Map<String, GuildResource> getGuildResourceMap(Guild guild);

    /**
     * Add resources to a guild's resource bank
     * @param guildId Guild ID
     * @param resourceType Resource type
     * @param amount Amount to add
     * @return True if resources were added successfully
     */
    boolean addGuildResources(String guildId, String resourceType, int amount);

    /**
     * Remove resources from a guild's resource bank
     * @param guildId Guild ID
     * @param resourceType Resource type
     * @param amount Amount to remove
     * @return True if resources were removed successfully
     */
    boolean removeGuildResources(String guildId, String resourceType, int amount);

    /**
     * Check if a guild has sufficient resources
     * @param guildId Guild ID
     * @param resourceType Resource type
     * @param requiredAmount Required amount
     * @return True if guild has sufficient resources
     */
    boolean hasSufficientResources(String guildId, String resourceType, int requiredAmount);

    /**
     * Get a specific resource contribution by ID
     * @param contributionId Contribution ID
     * @return Resource contribution if found
     */
    Optional<ResourceContribution> getResourceContribution(String contributionId);

    /**
     * Get all contributions for a guild
     * @param guildId Guild ID
     * @return List of resource contributions
     */
    List<ResourceContribution> getGuildContributions(String guildId);

    /**
     * Get contributions made by a specific player
     * @param contributorUuid Contributor's UUID
     * @return List of contributions by the player
     */
    List<ResourceContribution> getPlayerContributions(UUID contributorUuid);

    /**
     * Get contributions made by a player to a specific guild
     * @param guildId Guild ID
     * @param contributorUuid Contributor's UUID
     * @return List of contributions by the player to the guild
     */
    List<ResourceContribution> getPlayerContributionsToGuild(String guildId, UUID contributorUuid);

    /**
     * Record a resource contribution to a guild
     * @param guildId Guild ID
     * @param contributorUuid Contributor's UUID
     * @param resourceType Resource type
     * @param amount Amount contributed
     * @return Resource contribution record if successful
     */
    Optional<ResourceContribution> recordResourceContribution(String guildId, UUID contributorUuid, String resourceType, int amount);

    /**
     * Calculate total contributions for a guild by resource type
     * @param guildId Guild ID
     * @return Map of resource types to total contributed amounts
     */
    Map<String, Integer> calculateTotalContributionsByResource(String guildId);

    /**
     * Calculate total contributions by a player
     * @param contributorUuid Contributor's UUID
     * @return Map of resource types to total contributed amounts
     */
    Map<String, Integer> calculatePlayerContributions(UUID contributorUuid);

    /**
     * Get recent contributions for a guild (within last 24 hours)
     * @param guildId Guild ID
     * @return List of recent contributions
     */
    List<ResourceContribution> getRecentContributions(String guildId);

    /**
     * Get contribution statistics for a guild
     * @param guildId Guild ID
     * @return Contribution statistics
     */
    ContributionStatistics getContributionStatistics(String guildId);

    /**
     * Validate a resource contribution before recording it
     * @param guild Guild to contribute to
     * @param contributorUuid Contributor's UUID
     * @param resourceType Resource type
     * @param amount Amount to contribute
     * @return Validation result
     */
    ContributionValidation validateContribution(Guild guild, UUID contributorUuid, String resourceType, int amount);

    /**
     * Process a resource contribution (validation + recording)
     * @param guild Guild to contribute to
     * @param contributorUuid Contributor's UUID
     * @param resourceType Resource type
     * @param amount Amount to contribute
     * @return Contribution result
     */
    ContributionResult processContribution(Guild guild, UUID contributorUuid, String resourceType, int amount);

    /**
     * Get all supported resource types
     * @return List of supported resource types
     */
    List<String> getSupportedResourceTypes();

    /**
     * Check if a resource type is supported
     * @param resourceType Resource type to check
     * @return True if supported
     */
    boolean isSupportedResourceType(String resourceType);

    /**
     * Clear all resource data for a guild (for guild deletion)
     * @param guildId Guild ID
     * @return True if data was cleared successfully
     */
    boolean clearGuildResourceData(String guildId);

    /**
     * Reset all resource data (for testing purposes)
     */
    void resetAllResourceData();

    /**
     * Result of a contribution validation
     */
    class ContributionValidation {
        private final boolean valid;
        private final String reason;
        private final boolean canAfford;
        private final boolean canContribute;

        public ContributionValidation(boolean valid, String reason, boolean canAfford, boolean canContribute) {
            this.valid = valid;
            this.reason = reason;
            this.canAfford = canAfford;
            this.canContribute = canContribute;
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public boolean canAfford() { return canAfford; }
        public boolean canContribute() { return canContribute; }
    }

    /**
     * Result of a contribution operation
     */
    class ContributionResult {
        private final boolean successful;
        private final String message;
        private final ResourceContribution contribution;
        private final int amountContributed;

        public ContributionResult(boolean successful, String message, ResourceContribution contribution, int amountContributed) {
            this.successful = successful;
            this.message = message;
            this.contribution = contribution;
            this.amountContributed = amountContributed;
        }

        public boolean isSuccessful() { return successful; }
        public String getMessage() { return message; }
        public ResourceContribution getContribution() { return contribution; }
        public int getAmountContributed() { return amountContributed; }
    }

    /**
     * Contribution statistics for a guild
     */
    class ContributionStatistics {
        private final int totalContributors;
        private final int totalContributions;
        private final Map<String, Integer> resourceTotals;
        private final Map<String, Integer> topContributors;
        private final java.time.LocalDateTime lastContribution;

        public ContributionStatistics(int totalContributors, int totalContributions,
                                     Map<String, Integer> resourceTotals, Map<String, Integer> topContributors,
                                     java.time.LocalDateTime lastContribution) {
            this.totalContributors = totalContributors;
            this.totalContributions = totalContributions;
            this.resourceTotals = resourceTotals;
            this.topContributors = topContributors;
            this.lastContribution = lastContribution;
        }

        public int getTotalContributors() { return totalContributors; }
        public int getTotalContributions() { return totalContributions; }
        public Map<String, Integer> getResourceTotals() { return resourceTotals; }
        public Map<String, Integer> getTopContributors() { return topContributors; }
        public java.time.LocalDateTime getLastContribution() { return lastContribution; }
    }
}
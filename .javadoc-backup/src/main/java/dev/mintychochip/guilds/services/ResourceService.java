package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.ResourceContribution;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildResource;

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
        /** The valid. */
        private final boolean valid;
        /** The reason. */
        private final String reason;
        /** The can afford. */
        private final boolean canAfford;
        /** The can contribute. */
        private final boolean canContribute;

        /**
         * Creates a new contribution validation instance.
         * @param valid the valid
         * @param reason the reason
         * @param canAfford the can afford
         * @param canContribute the can contribute
         */
        public ContributionValidation(boolean valid, String reason, boolean canAfford, boolean canContribute) {
            this.valid = valid;
            this.reason = reason;
            this.canAfford = canAfford;
            this.canContribute = canContribute;
        }

        /**
         * Returns whether valid.
         * @return the result
         */
        public boolean isValid() { return valid; }
        /**
         * Returns the reason.
         * @return the result
         */
        public String getReason() { return reason; }
        /**
         * Returns whether afford.
         * @return the result
         */
        public boolean canAfford() { return canAfford; }
        /**
         * Returns whether contribute.
         * @return the result
         */
        public boolean canContribute() { return canContribute; }
    }

    /**
     * Result of a contribution operation
     */
    class ContributionResult {
        /** The successful. */
        private final boolean successful;
        /** The message. */
        private final String message;
        /** The contribution. */
        private final ResourceContribution contribution;
        /** The amount contributed. */
        private final int amountContributed;

        /**
         * Creates a new contribution result instance.
         * @param successful the successful
         * @param message the message
         * @param contribution the contribution
         * @param amountContributed the amount contributed
         */
        public ContributionResult(boolean successful, String message, ResourceContribution contribution, int amountContributed) {
            this.successful = successful;
            this.message = message;
            this.contribution = contribution;
            this.amountContributed = amountContributed;
        }

        /**
         * Returns whether successful.
         * @return the result
         */
        public boolean isSuccessful() { return successful; }
        /**
         * Returns the message.
         * @return the result
         */
        public String getMessage() { return message; }
        /**
         * Returns the contribution.
         * @return the result
         */
        public ResourceContribution getContribution() { return contribution; }
        /**
         * Returns the amount contributed.
         * @return the result
         */
        public int getAmountContributed() { return amountContributed; }
    }

    /**
     * Contribution statistics for a guild
     */
    class ContributionStatistics {
        /** The total contributors. */
        private final int totalContributors;
        /** The total contributions. */
        private final int totalContributions;
        /** The resource totals. */
        private final Map<String, Integer> resourceTotals;
        /** The top contributors. */
        private final Map<String, Integer> topContributors;
        /** The last contribution. */
        private final java.time.LocalDateTime lastContribution;

        /**
         * Creates a new contribution statistics instance.
         * @param totalContributors the total contributors
         * @param totalContributions the total contributions
         * @param resourceTotals the resource totals
         * @param topContributors the top contributors
         * @param lastContribution the last contribution
         */
        public ContributionStatistics(int totalContributors, int totalContributions,
                                     Map<String, Integer> resourceTotals, Map<String, Integer> topContributors,
                                     java.time.LocalDateTime lastContribution) {
            this.totalContributors = totalContributors;
            this.totalContributions = totalContributions;
            this.resourceTotals = resourceTotals;
            this.topContributors = topContributors;
            this.lastContribution = lastContribution;
        }

        /**
         * Returns the total contributors.
         * @return the result
         */
        public int getTotalContributors() { return totalContributors; }
        /**
         * Returns the total contributions.
         * @return the result
         */
        public int getTotalContributions() { return totalContributions; }
        /**
         * Returns the resource totals.
         * @return the result
         */
        public Map<String, Integer> getResourceTotals() { return resourceTotals; }
        /**
         * Returns the top contributors.
         * @return the result
         */
        public Map<String, Integer> getTopContributors() { return topContributors; }
        /**
         * Returns the last contribution.
         * @return the result
         */
        public java.time.LocalDateTime getLastContribution() { return lastContribution; }
    }
}
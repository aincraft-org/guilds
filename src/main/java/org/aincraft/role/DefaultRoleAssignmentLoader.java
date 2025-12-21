package org.aincraft.role;

import com.google.inject.Inject;
import org.aincraft.config.GuildsConfig;
import org.aincraft.subregion.SubjectType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Loader service for initializing default role assignments from config.
 * Responsible for setting up global role defaults when guilds are created.
 * Follows SOLID Single Responsibility: loads and applies config-defined defaults.
 * Follows SOLID Dependency Inversion: depends on abstractions (service, config, registry).
 */
public class DefaultRoleAssignmentLoader {
    private final GuildDefaultRoleAssignmentService assignmentService;
    private final GuildsConfig config;
    private final DefaultRoleRegistry roleRegistry;
    private final Logger logger;

    @Inject
    public DefaultRoleAssignmentLoader(GuildDefaultRoleAssignmentService assignmentService,
                                       GuildsConfig config,
                                       DefaultRoleRegistry roleRegistry) {
        this.assignmentService = Objects.requireNonNull(assignmentService, "Assignment service cannot be null");
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.roleRegistry = Objects.requireNonNull(roleRegistry, "Role registry cannot be null");
        this.logger = config.getPlugin().getLogger();
    }

    /**
     * Initialize default role assignments for a newly created guild.
     * Loads role assignments from config and applies them to the guild if the roles exist.
     * Gracefully skips invalid role names with a warning.
     * Handles null/missing config values by not assigning any default.
     *
     * @param guildId the guild ID to initialize
     */
    public void initializeDefaultsForGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        Map<SubjectType, String> defaults = config.getDefaultRoleAssignments();
        if (defaults.isEmpty()) {
            logger.fine("No default role assignments configured in config.yml");
            return;
        }

        int assignedCount = 0;
        for (Map.Entry<SubjectType, String> entry : defaults.entrySet()) {
            SubjectType subjectType = entry.getKey();
            String roleName = entry.getValue();

            if (roleName == null || roleName.isEmpty()) {
                logger.fine("Skipping null/empty default role for " + subjectType);
                continue;
            }

            // Validate that the role exists in the default role registry
            if (!roleRegistry.isDefaultRole(roleName)) {
                logger.warning("Invalid default role name '" + roleName + "' for subject type " +
                              subjectType + ". Available roles: " + roleRegistry.getDefaultRoleNames());
                continue;
            }

            // Convert role name to role ID format
            String roleId = "DEFAULT:" + roleName;

            // Assign the default role for this subject type
            boolean success = assignmentService.setDefaultRole(guildId, subjectType, roleId);
            if (success) {
                logger.fine("Initialized default role '" + roleName + "' for " + subjectType +
                           " in guild " + guildId);
                assignedCount++;
            } else {
                logger.warning("Failed to assign default role '" + roleName + "' for " +
                              subjectType + " in guild " + guildId);
            }
        }

        if (assignedCount > 0) {
            logger.info("Initialized " + assignedCount + " default role assignment(s) for new guild " + guildId);
        }
    }

    /**
     * Check if a guild already has default role assignments configured.
     * Used to avoid overwriting custom per-guild defaults.
     *
     * @param guildId the guild ID to check
     * @return true if the guild has any default role assignments set
     */
    public boolean guildHasAssignments(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return !assignmentService.getGuildAssignments(guildId).isEmpty();
    }
}

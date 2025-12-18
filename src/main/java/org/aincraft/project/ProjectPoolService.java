package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.project.storage.GuildProjectRepository;

import java.util.*;

/**
 * Service for managing the pool of available projects for guilds.
 * Handles both procedurally generated projects and projects from the registry.
 */
@Singleton
public class ProjectPoolService {

    private final ProjectRegistry registry;
    private final ProjectGenerator generator;
    private final GuildProjectRepository projectRepository;

    @Inject
    public ProjectPoolService(
            ProjectRegistry registry,
            ProjectGenerator generator,
            GuildProjectRepository projectRepository) {
        this.registry = Objects.requireNonNull(registry, "Registry cannot be null");
        this.generator = Objects.requireNonNull(generator, "Generator cannot be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "Repository cannot be null");
    }

    /**
     * Gets the available projects for a guild.
     * Procedurally generates 27 projects per day (seeded by guild ID + date).
     *
     * @param guildId the guild ID
     * @param guildLevel the guild's current level
     * @return list of available project definitions
     */
    public List<ProjectDefinition> getAvailableProjects(String guildId, int guildLevel) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        // Check if 24 hours have passed since last refresh
        Long lastRefresh = projectRepository.getLastRefreshTime(guildId);
        long refreshIntervalMs = (long) registry.getRefreshIntervalHours() * 3600000L;
        if (lastRefresh == null || System.currentTimeMillis() - lastRefresh >= refreshIntervalMs) {
            refreshPool(guildId);
        }

        // Generate 27 projects procedurally based on guild ID + current day
        int projectCount = 27;

        return generator.generateProjects(guildId, guildLevel, projectCount);
    }

    /**
     * Checks if a project is currently available for a guild.
     *
     * @param guildId the guild ID
     * @param guildLevel the guild's current level
     * @param projectDefinitionId the project definition ID to check
     * @return true if the project is available
     */
    public boolean isProjectAvailable(String guildId, int guildLevel, String projectDefinitionId) {
        return getAvailableProjects(guildId, guildLevel).stream()
                .anyMatch(p -> p.id().equals(projectDefinitionId));
    }

    /**
     * Refreshes the project pool for a guild.
     * This typically happens once per day (24 hours).
     *
     * @param guildId the guild ID
     */
    public void refreshPool(String guildId) {
        projectRepository.incrementPoolSeed(guildId);
    }
}

package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.project.storage.GuildProjectRepository;
import org.aincraft.project.storage.GuildProjectPoolRepository;

import java.util.*;
import java.util.logging.Logger;

/**
 * Service for managing the pool of available projects for guilds.
 * Persists project pools to database with 24h refresh cycle anchored to guild creation time.
 * Implements Single Responsibility: manages only pool availability and refresh logic.
 */
@Singleton
public class ProjectPoolService {

    private final ProjectRegistry registry;
    private final ProjectGenerator generator;
    private final GuildProjectRepository projectRepository;
    private final GuildProjectPoolRepository poolRepository;
    private final Logger logger;

    @Inject
    public ProjectPoolService(
            ProjectRegistry registry,
            ProjectGenerator generator,
            GuildProjectRepository projectRepository,
            GuildProjectPoolRepository poolRepository,
            @com.google.inject.name.Named("guilds") Logger logger) {
        this.registry = Objects.requireNonNull(registry, "Registry cannot be null");
        this.generator = Objects.requireNonNull(generator, "Generator cannot be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "Project repository cannot be null");
        this.poolRepository = Objects.requireNonNull(poolRepository, "Pool repository cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
    }

    /**
     * Gets the available projects for a guild.
     * Checks if pool needs refresh based on 24h cycle anchored to guild creation time.
     * Returns persisted pool if available, otherwise generates and persists new pool.
     *
     * @param guildId the guild ID
     * @param guildLevel the guild's current level
     * @return list of available project definitions filtered by guild level
     */
    public List<ProjectDefinition> getAvailableProjects(String guildId, int guildLevel) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        // 1. Get or initialize guild_created_at
        Optional<Long> guildCreatedAtOpt = poolRepository.getGuildCreatedAt(guildId);
        long guildCreatedAt;
        if (guildCreatedAtOpt.isEmpty()) {
            guildCreatedAt = System.currentTimeMillis();
            poolRepository.setGuildCreatedAt(guildId, guildCreatedAt);
            logger.fine("Initialized guild_created_at for guild " + guildId);
        } else {
            guildCreatedAt = guildCreatedAtOpt.get();
        }

        // 2. Calculate current pool generation time
        long now = System.currentTimeMillis();
        long hoursSinceCreation = (now - guildCreatedAt) / 3600000L;
        long refreshIntervalHours = registry.getRefreshIntervalHours();
        long poolPeriod = hoursSinceCreation / refreshIntervalHours;
        long poolGenerationTime = guildCreatedAt + (poolPeriod * refreshIntervalHours * 3600000L);

        // 3. Check if pool needs refresh
        Optional<Long> lastPoolTimeOpt = poolRepository.getLastPoolGenerationTime(guildId);

        if (lastPoolTimeOpt.isEmpty() || lastPoolTimeOpt.get() < poolGenerationTime) {
            // Generate new pool
            int poolSize = registry.getPoolSize();
            List<ProjectDefinition> newPool = generator.generateProjects(guildId, guildLevel, poolSize, poolGenerationTime);

            // Delete old pool and save new one
            poolRepository.deletePoolByGuildId(guildId);
            poolRepository.savePool(guildId, newPool, poolGenerationTime);

            logger.info("Generated new project pool for guild " + guildId + " with " + poolSize + " projects");
            return filterByLevel(newPool, guildLevel);
        }

        // 4. Load existing pool from database
        List<ProjectDefinition> pool = poolRepository.getPool(guildId);
        return filterByLevel(pool, guildLevel);
    }

    /**
     * Filters a list of projects by guild level.
     * Only returns projects where requiredLevel <= guildLevel.
     *
     * @param projects the complete list of projects
     * @param guildLevel the guild's level
     * @return filtered list of available projects
     */
    private List<ProjectDefinition> filterByLevel(List<ProjectDefinition> projects, int guildLevel) {
        return projects.stream()
            .filter(p -> p.requiredLevel() <= guildLevel)
            .toList();
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
}

package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.project.storage.GuildProjectRepository;

import java.util.*;

@Singleton
public class ProjectPoolService {

    private final ProjectRegistry registry;
    private final GuildProjectRepository projectRepository;

    @Inject
    public ProjectPoolService(ProjectRegistry registry, GuildProjectRepository projectRepository) {
        this.registry = Objects.requireNonNull(registry);
        this.projectRepository = Objects.requireNonNull(projectRepository);
    }

    public List<ProjectDefinition> getAvailableProjects(String guildId, int guildLevel) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        List<ProjectDefinition> eligible = registry.getProjectsForLevel(guildLevel);
        if (eligible.isEmpty()) {
            return Collections.emptyList();
        }

        int poolSeed = projectRepository.getPoolSeed(guildId);
        long seed = guildId.hashCode() + poolSeed;
        Random random = new Random(seed);

        List<ProjectDefinition> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled, random);

        int poolSize = Math.min(registry.getPoolSize(), shuffled.size());
        return shuffled.subList(0, poolSize);
    }

    public boolean isProjectAvailable(String guildId, int guildLevel, String projectDefinitionId) {
        return getAvailableProjects(guildId, guildLevel).stream()
                .anyMatch(p -> p.id().equals(projectDefinitionId));
    }

    public void refreshPool(String guildId) {
        projectRepository.incrementPoolSeed(guildId);
    }
}

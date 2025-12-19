package org.aincraft.project.storage;

import org.aincraft.project.GuildProject;
import org.aincraft.project.ProjectStatus;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.UUID;

public interface GuildProjectRepository {

    void save(GuildProject project);

    Optional<GuildProject> findById(String projectId);

    Optional<GuildProject> findActiveByGuildId(UUID guildId);

    List<GuildProject> findByGuildId(UUID guildId);

    void delete(String projectId);

    void deleteByGuildId(UUID guildId);

    void updateQuestProgress(String projectId, String questId, long newCount);

    /**
     * @deprecated Material contributions are no longer tracked. This method is kept for
     *             backward compatibility but should not be used by new code.
     */
    @Deprecated
    void updateMaterialContribution(String projectId, Material material, int newAmount);

    void updateStatus(String projectId, ProjectStatus status, Long completedAt);

    int getPoolSeed(UUID guildId);

    void incrementPoolSeed(UUID guildId);

    Long getLastRefreshTime(UUID guildId);
}

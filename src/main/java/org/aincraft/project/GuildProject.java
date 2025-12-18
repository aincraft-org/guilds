package org.aincraft.project;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public final class GuildProject {

    private final String id;
    private final String guildId;
    private final String projectDefinitionId;
    private ProjectStatus status;
    private final Map<String, Long> questProgress;
    private final Map<Material, Integer> materialContributed;
    private final long startedAt;
    private Long completedAt;

    public GuildProject(
            String id,
            String guildId,
            String projectDefinitionId,
            ProjectStatus status,
            Map<String, Long> questProgress,
            Map<Material, Integer> materialContributed,
            long startedAt,
            Long completedAt
    ) {
        this.id = id;
        this.guildId = guildId;
        this.projectDefinitionId = projectDefinitionId;
        this.status = status;
        this.questProgress = new HashMap<>(questProgress);
        this.materialContributed = new HashMap<>(materialContributed);
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public String getId() {
        return id;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getProjectDefinitionId() {
        return projectDefinitionId;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public Map<String, Long> getQuestProgress() {
        return questProgress;
    }

    public long getQuestProgress(String questId) {
        return questProgress.getOrDefault(questId, 0L);
    }

    public void setQuestProgress(String questId, long count) {
        questProgress.put(questId, count);
    }

    public Map<Material, Integer> getMaterialContributed() {
        return materialContributed;
    }

    public int getMaterialContributed(Material material) {
        return materialContributed.getOrDefault(material, 0);
    }

    public void setMaterialContributed(Material material, int amount) {
        materialContributed.put(material, amount);
    }

    public long getStartedAt() {
        return startedAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }
}

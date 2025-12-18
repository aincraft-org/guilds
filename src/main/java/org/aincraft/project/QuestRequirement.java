package org.aincraft.project;

public record QuestRequirement(
        String id,
        QuestType type,
        String targetId,
        long targetCount,
        String description
) {
}

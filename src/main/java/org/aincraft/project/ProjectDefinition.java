package org.aincraft.project;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record ProjectDefinition(
        String id,
        String name,
        String description,
        int requiredLevel,
        BuffType buffType,
        BuffDefinition buff,
        List<QuestRequirement> quests,
        Map<Material, Integer> materials,
        long buffDurationMillis
) {
}

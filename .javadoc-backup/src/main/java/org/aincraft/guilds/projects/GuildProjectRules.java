package org.aincraft.guilds.projects;

import org.aincraft.guilds.models.TechTreeNode;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Pure start/clear checks for guild projects (tech-tree nodes). */
public final class GuildProjectRules {

    public enum StartStatus {
        STARTED,
        UNKNOWN_NODE,
        ALREADY_ACTIVE,
        ALREADY_UNLOCKED,
        UNMET_REQUIREMENTS,
        INSUFFICIENT_POINTS
    }

    private GuildProjectRules() {
    }

    public static StartStatus evaluateStart(
            TechTreeNode node,
            String activeProjectId,
            Set<String> unlockedNodeIds,
            int unspentPoints
    ) {
        if (node == null || node.getId() == null || node.getId().isBlank()) {
            return StartStatus.UNKNOWN_NODE;
        }
        if (hasActiveProject(activeProjectId)) {
            return StartStatus.ALREADY_ACTIVE;
        }
        Set<String> unlocked = unlockedNodeIds == null ? Set.of() : unlockedNodeIds;
        if (unlocked.contains(node.getId())) {
            return StartStatus.ALREADY_UNLOCKED;
        }
        if (unspentPoints < Math.max(0, node.getCost())) {
            return StartStatus.INSUFFICIENT_POINTS;
        }
        List<String> prerequisites = node.getPrerequisites();
        if (prerequisites != null) {
            for (String prerequisite : prerequisites) {
                if (prerequisite != null && !prerequisite.isBlank() && !unlocked.contains(prerequisite)) {
                    return StartStatus.UNMET_REQUIREMENTS;
                }
            }
        }
        return StartStatus.STARTED;
    }

    public static boolean hasActiveProject(String activeProjectId) {
        return activeProjectId != null && !activeProjectId.isBlank();
    }

    public static boolean canClear(String activeProjectId) {
        return hasActiveProject(activeProjectId);
    }

    public static int unspentAfterStart(int unspentPoints, int cost) {
        return Math.max(0, unspentPoints - Math.max(0, cost));
    }

    public static boolean prerequisitesMet(Collection<String> prerequisites, Set<String> unlockedNodeIds) {
        if (prerequisites == null || prerequisites.isEmpty()) {
            return true;
        }
        Set<String> unlocked = unlockedNodeIds == null ? Set.of() : unlockedNodeIds;
        for (String prerequisite : prerequisites) {
            if (prerequisite != null && !prerequisite.isBlank() && !unlocked.contains(prerequisite)) {
                return false;
            }
        }
        return true;
    }
}

package dev.mintychochip.guilds.projects;

import dev.mintychochip.guilds.models.TechTreeNode;
import dev.mintychochip.guilds.services.GuildProjectService;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Pure start/clear checks for guild projects (tech-tree nodes). */
public final class GuildProjectRules {

    /** Creates a new guild project rules instance. */
    private GuildProjectRules() {
    }

    /**
     * Performs the evaluate start operation.
     * @param node the node
     * @param activeProjectId the active project id
     * @param unlockedNodeIds the unlocked node ids
     * @param unspentPoints the unspent points
     * @return the result
     */
    public static GuildProjectService.StartStatus evaluateStart(
            TechTreeNode node,
            String activeProjectId,
            Set<String> unlockedNodeIds,
            int unspentPoints
    ) {
        if (node == null || node.getId() == null || node.getId().isBlank()) {
            return GuildProjectService.StartStatus.UNKNOWN_NODE;
        }
        if (hasActiveProject(activeProjectId)) {
            return GuildProjectService.StartStatus.ALREADY_ACTIVE;
        }
        Set<String> unlocked = unlockedNodeIds == null ? Set.of() : unlockedNodeIds;
        if (unlocked.contains(node.getId())) {
            return GuildProjectService.StartStatus.ALREADY_UNLOCKED;
        }
        if (unspentPoints < Math.max(0, node.getCost())) {
            return GuildProjectService.StartStatus.INSUFFICIENT_POINTS;
        }
        List<String> prerequisites = node.getPrerequisites();
        if (prerequisites != null) {
            for (String prerequisite : prerequisites) {
                if (prerequisite != null && !prerequisite.isBlank() && !unlocked.contains(prerequisite)) {
                    return GuildProjectService.StartStatus.UNMET_REQUIREMENTS;
                }
            }
        }
        return GuildProjectService.StartStatus.STARTED;
    }

    /**
     * Returns whether active project.
     * @param activeProjectId the active project id
     * @return the result
     */
    public static boolean hasActiveProject(String activeProjectId) {
        return activeProjectId != null && !activeProjectId.isBlank();
    }

    /**
     * Returns whether clear.
     * @param activeProjectId the active project id
     * @return the result
     */
    public static boolean canClear(String activeProjectId) {
        return hasActiveProject(activeProjectId);
    }

    /**
     * Performs the unspent after start operation.
     * @param unspentPoints the unspent points
     * @param cost the cost
     * @return the result
     */
    public static int unspentAfterStart(int unspentPoints, int cost) {
        return Math.max(0, unspentPoints - Math.max(0, cost));
    }

    /**
     * Performs the prerequisites met operation.
     * @param prerequisites the prerequisites
     * @param unlockedNodeIds the unlocked node ids
     * @return the result
     */
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

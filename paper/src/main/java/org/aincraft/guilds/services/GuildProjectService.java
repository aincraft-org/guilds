package org.aincraft.guilds.services;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.TechTreeNode;
import org.aincraft.guilds.projects.GuildProjectRules;

import java.util.Optional;

/** Starts and clears guild projects backed by the tech-tree node catalog. */
public interface GuildProjectService {

    ProjectStartResult startProject(Guild guild, String nodeId);

    boolean clearActiveProject(Guild guild);

    Optional<String> getActiveProjectId(Guild guild);

    Optional<TechTreeNode> getProject(String nodeId);

    class ProjectStartResult {
        private final boolean successful;
        private final GuildProjectRules.StartStatus status;
        private final String activeProjectId;
        private final int unspentPoints;

        public ProjectStartResult(
                boolean successful,
                GuildProjectRules.StartStatus status,
                String activeProjectId,
                int unspentPoints
        ) {
            this.successful = successful;
            this.status = status;
            this.activeProjectId = activeProjectId;
            this.unspentPoints = unspentPoints;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public GuildProjectRules.StartStatus getStatus() {
            return status;
        }

        public String getActiveProjectId() {
            return activeProjectId;
        }

        public int getUnspentPoints() {
            return unspentPoints;
        }
    }
}

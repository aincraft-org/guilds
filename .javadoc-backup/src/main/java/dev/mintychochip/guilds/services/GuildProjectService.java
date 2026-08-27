package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.TechTreeNode;

import java.util.Optional;

/** Starts and clears guild projects backed by the tech-tree node catalog. */
public interface GuildProjectService {

    /** Describes the outcome of attempting to start a guild project. */
    enum StartStatus {
        /** The project started successfully. */
        STARTED,
        /** The requested node does not exist. */
        UNKNOWN_NODE,
        /** The guild already has an active project. */
        ALREADY_ACTIVE,
        /** The guild has already unlocked the node. */
        ALREADY_UNLOCKED,
        /** The guild does not meet the node's requirements. */
        UNMET_REQUIREMENTS,
        /** The guild lacks enough points to start the project. */
        INSUFFICIENT_POINTS
    }

    /**
     * Attempts to start a project for a guild.
     *
     * @param guild guild for which to start the project
     * @param nodeId identifier of the node to start
     * @return the outcome of the start attempt
     */
    ProjectStartResult startProject(Guild guild, String nodeId);

    /**
     * Completes the guild's active project.
     *
     * @param guild guild whose project should be completed
     * @return {@code true} if an active project was completed
     */
    boolean completeActiveProject(Guild guild);

    /**
     * Clears the guild's active project without completing it.
     *
     * @param guild guild whose project should be cleared
     * @return {@code true} if an active project was cleared
     */
    boolean clearActiveProject(Guild guild);

    /**
     * Returns the identifier of the guild's active project, if any.
     *
     * @param guild guild whose active project should be queried
     * @return the active project identifier, or empty if none is active
     */
    Optional<String> getActiveProjectId(Guild guild);

    /**
     * Finds a project node by identifier.
     *
     * @param nodeId identifier of the project node
     * @return the matching node, or empty if none exists
     */
    Optional<TechTreeNode> getProject(String nodeId);

    /** Contains the outcome and resulting state of a project-start attempt. */
    class ProjectStartResult {
        /** Whether the project-start attempt succeeded. */
        private final boolean successful;
        /** Status describing the project-start outcome. */
        private final StartStatus status;
        /** Identifier of the active project, if applicable. */
        private final String activeProjectId;
        /** Number of unspent points remaining after the attempt. */
        private final int unspentPoints;

        /**
         * Creates a project-start result.
         *
         * @param successful whether the attempt succeeded
         * @param status status describing the outcome
         * @param activeProjectId identifier of the active project, if applicable
         * @param unspentPoints number of unspent points remaining
         */
        public ProjectStartResult(
                boolean successful,
                StartStatus status,
                String activeProjectId,
                int unspentPoints
        ) {
            this.successful = successful;
            this.status = status;
            this.activeProjectId = activeProjectId;
            this.unspentPoints = unspentPoints;
        }

        /**
         * Indicates whether the project-start attempt succeeded.
         *
         * @return {@code true} if the attempt succeeded
         */
        public boolean isSuccessful() {
            return successful;
        }

        /**
         * Returns the status of the project-start attempt.
         *
         * @return the attempt status
         */
        public StartStatus getStatus() {
            return status;
        }

        /**
         * Returns the identifier of the active project.
         *
         * @return the active project identifier, or {@code null} if none applies
         */
        public String getActiveProjectId() {
            return activeProjectId;
        }

        /**
         * Returns the number of unspent points.
         *
         * @return the remaining unspent points
         */
        public int getUnspentPoints() {
            return unspentPoints;
        }
    }
}

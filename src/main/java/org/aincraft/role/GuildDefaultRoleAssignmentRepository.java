package org.aincraft.role;

import org.aincraft.subregion.SubjectType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing guild default role assignments.
 * Follows SOLID Interface Segregation: focused on a single data access concern.
 * Follows SOLID Dependency Inversion: depends on abstraction, not implementation.
 */
public interface GuildDefaultRoleAssignmentRepository {

    /**
     * Saves or updates a default role assignment for a guild subject type.
     *
     * @param assignment the assignment to save
     * @return true if successful
     */
    boolean save(GuildDefaultRoleAssignment assignment);

    /**
     * Retrieves the default role assignment for a guild and subject type.
     *
     * @param guildId the guild ID
     * @param subjectType the subject type
     * @return the assignment if exists, or empty if not set
     */
    Optional<GuildDefaultRoleAssignment> findByGuildAndSubjectType(UUID guildId, SubjectType subjectType);

    /**
     * Retrieves all default role assignments for a guild.
     *
     * @param guildId the guild ID
     * @return list of all assignments for the guild
     */
    List<GuildDefaultRoleAssignment> findByGuild(UUID guildId);

    /**
     * Deletes the default role assignment for a guild and subject type.
     *
     * @param guildId the guild ID
     * @param subjectType the subject type
     * @return true if successful
     */
    boolean delete(UUID guildId, SubjectType subjectType);
}

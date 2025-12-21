package org.aincraft.role;

import com.google.inject.Inject;
import org.aincraft.GuildRole;
import org.aincraft.subregion.SubjectType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing guild default role assignments.
 * Orchestrates default role operations between repository and role access.
 * Follows SOLID Single Responsibility: manages default role business logic.
 * Follows SOLID Dependency Inversion: depends on repository abstraction.
 */
public class GuildDefaultRoleAssignmentService {
    private final GuildDefaultRoleAssignmentRepository assignmentRepository;
    private final CompositeGuildRoleRepository roleRepository;

    @Inject
    public GuildDefaultRoleAssignmentService(
            GuildDefaultRoleAssignmentRepository assignmentRepository,
            CompositeGuildRoleRepository roleRepository) {
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository, "Assignment repository cannot be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "Role repository cannot be null");
    }

    /**
     * Sets the default role for a subject type in a guild.
     * Validates that the role exists before assignment.
     *
     * @param guildId the guild ID
     * @param subjectType the subject type (outsider, ally, member)
     * @param roleId the role ID to assign, or null to clear
     * @return true if successful
     */
    public boolean setDefaultRole(UUID guildId, SubjectType subjectType, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        // Validate role exists if not clearing
        if (roleId != null && !roleId.isEmpty()) {
            Optional<GuildRole> role = roleRepository.findByIdAndGuild(roleId, guildId);
            if (role.isEmpty() || !role.get().getScopeId().equals(guildId)) {
                return false;  // Role doesn't exist or doesn't belong to this guild
            }
        }

        GuildDefaultRoleAssignment assignment = new GuildDefaultRoleAssignment(guildId, subjectType, roleId);
        return assignmentRepository.save(assignment);
    }

    /**
     * Gets the default role for a subject type in a guild.
     *
     * @param guildId the guild ID
     * @param subjectType the subject type
     * @return the role if assigned, or empty
     */
    public Optional<GuildRole> getDefaultRole(UUID guildId, SubjectType subjectType) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        Optional<GuildDefaultRoleAssignment> assignment = assignmentRepository.findByGuildAndSubjectType(guildId, subjectType);
        if (assignment.isEmpty() || !assignment.get().hasDefaultRole()) {
            return Optional.empty();
        }

        return roleRepository.findByIdAndGuild(assignment.get().getRoleId(), guildId);
    }

    /**
     * Gets all default role assignments for a guild.
     *
     * @param guildId the guild ID
     * @return list of all assignments
     */
    public List<GuildDefaultRoleAssignment> getGuildAssignments(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        return assignmentRepository.findByGuild(guildId);
    }

    /**
     * Clears the default role for a subject type in a guild.
     *
     * @param guildId the guild ID
     * @param subjectType the subject type
     * @return true if successful
     */
    public boolean clearDefaultRole(UUID guildId, SubjectType subjectType) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        return assignmentRepository.delete(guildId, subjectType);
    }

    /**
     * Checks if a default role is set for a subject type in a guild.
     *
     * @param guildId the guild ID
     * @param subjectType the subject type
     * @return true if a default role is configured
     */
    public boolean hasDefaultRole(UUID guildId, SubjectType subjectType) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        Optional<GuildDefaultRoleAssignment> assignment = assignmentRepository.findByGuildAndSubjectType(guildId, subjectType);
        return assignment.isPresent() && assignment.get().hasDefaultRole();
    }
}

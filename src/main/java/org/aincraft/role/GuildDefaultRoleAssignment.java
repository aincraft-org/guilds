package org.aincraft.role;

import org.aincraft.subregion.SubjectType;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the default role assignment for a subject type in a guild.
 * Maps a guild + subject type to a specific role name (from default roles).
 * Follows SOLID Single Responsibility: manages default role configuration data.
 */
public class GuildDefaultRoleAssignment {
    private final UUID guildId;
    private final SubjectType subjectType;
    private final String roleId;  // Can be null to indicate no default role assigned

    public GuildDefaultRoleAssignment(UUID guildId, SubjectType subjectType, String roleId) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.subjectType = Objects.requireNonNull(subjectType, "Subject type cannot be null");
        this.roleId = roleId;  // Can be null
    }

    public UUID getGuildId() {
        return guildId;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public String getRoleId() {
        return roleId;
    }

    public boolean hasDefaultRole() {
        return roleId != null && !roleId.isEmpty();
    }

    @Override
    public String toString() {
        return "GuildDefaultRoleAssignment{" +
                "guildId=" + guildId +
                ", subjectType=" + subjectType +
                ", roleId='" + roleId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuildDefaultRoleAssignment that = (GuildDefaultRoleAssignment) o;
        return Objects.equals(guildId, that.guildId) &&
                subjectType == that.subjectType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(guildId, subjectType);
    }
}

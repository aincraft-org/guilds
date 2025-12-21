package org.aincraft.database.repository;

import com.google.inject.Inject;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.Sql;
import org.aincraft.role.GuildDefaultRoleAssignment;
import org.aincraft.role.GuildDefaultRoleAssignmentRepository;
import org.aincraft.subregion.SubjectType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of GuildDefaultRoleAssignmentRepository.
 * Handles all database operations for guild default role assignments.
 * Follows SOLID Dependency Inversion: injected ConnectionProvider abstraction.
 */
public class JdbcGuildDefaultRoleAssignmentRepository implements GuildDefaultRoleAssignmentRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildDefaultRoleAssignmentRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "ConnectionProvider cannot be null");
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public boolean save(GuildDefaultRoleAssignment assignment) {
        Objects.requireNonNull(assignment, "Assignment cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(Sql.upsertGuildDefaultRoleAssignment(dbType))) {

            stmt.setString(1, assignment.getGuildId().toString());
            stmt.setString(2, assignment.getSubjectType().name());
            stmt.setString(3, assignment.getRoleId());

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save default role assignment", e);
        }
    }

    @Override
    public Optional<GuildDefaultRoleAssignment> findByGuildAndSubjectType(UUID guildId, SubjectType subjectType) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(Sql.getGuildDefaultRoleAssignment(dbType))) {

            stmt.setString(1, guildId.toString());
            stmt.setString(2, subjectType.name());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find default role assignment", e);
        }

        return Optional.empty();
    }

    @Override
    public List<GuildDefaultRoleAssignment> findByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        List<GuildDefaultRoleAssignment> assignments = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(Sql.getGuildDefaultRoleAssignmentsByGuild(dbType))) {

            stmt.setString(1, guildId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    assignments.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild default role assignments", e);
        }

        return assignments;
    }

    @Override
    public boolean delete(UUID guildId, SubjectType subjectType) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement stmt = conn.prepareStatement(Sql.deleteGuildDefaultRoleAssignment(dbType))) {

            stmt.setString(1, guildId.toString());
            stmt.setString(2, subjectType.name());

            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete default role assignment", e);
        }
    }

    private GuildDefaultRoleAssignment mapResultSet(ResultSet rs) throws SQLException {
        UUID guildId = UUID.fromString(rs.getString("guild_id"));
        SubjectType subjectType = SubjectType.valueOf(rs.getString("subject_type"));
        String roleId = rs.getString("role_id");

        return new GuildDefaultRoleAssignment(guildId, subjectType, roleId);
    }
}

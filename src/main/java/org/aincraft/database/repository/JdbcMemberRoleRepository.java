package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.storage.MemberRoleRepository;

/**
 * JDBC-based implementation of MemberRoleRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcMemberRoleRepository implements MemberRoleRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcMemberRoleRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void assignRole(UUID guildId, UUID playerId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = getInsertIgnoreSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.setString(2, playerId.toString());
            ps.setString(3, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign role", e);
        }
    }

    private String getInsertIgnoreSql() {
        return switch (dbType) {
            case SQLITE -> "INSERT OR IGNORE INTO member_roles (guild_id, player_id, role_id) VALUES (?, ?, ?)";
            case MYSQL, MARIADB -> "INSERT IGNORE INTO member_roles (guild_id, player_id, role_id) VALUES (?, ?, ?)";
            case POSTGRESQL -> """
                INSERT INTO member_roles (guild_id, player_id, role_id) VALUES (?, ?, ?)
                ON CONFLICT (guild_id, player_id, role_id) DO NOTHING
                """;
            case H2 -> "MERGE INTO member_roles (guild_id, player_id, role_id) KEY (guild_id, player_id, role_id) VALUES (?, ?, ?)";
        };
    }

    @Override
    public void unassignRole(UUID guildId, UUID playerId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM member_roles WHERE guild_id = ? AND player_id = ? AND role_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.setString(2, playerId.toString());
            ps.setString(3, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unassign role", e);
        }
    }

    @Override
    public List<String> getMemberRoleIds(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        List<String> roleIds = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT role_id FROM member_roles WHERE guild_id = ? AND player_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.setString(2, playerId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                roleIds.add(rs.getString("role_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get member role IDs", e);
        }

        return roleIds;
    }

    @Override
    public List<UUID> getMembersWithRole(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        List<UUID> members = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT player_id FROM member_roles WHERE role_id = ?")) {
            ps.setString(1, roleId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                members.add(UUID.fromString(rs.getString("player_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get members with role", e);
        }

        return members;
    }

    @Override
    public boolean hasMemberRole(UUID guildId, UUID playerId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM member_roles WHERE guild_id = ? AND player_id = ? AND role_id = ? LIMIT 1")) {
            ps.setString(1, guildId.toString());
            ps.setString(2, playerId.toString());
            ps.setString(3, roleId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check member role", e);
        }
    }

    @Override
    public void removeAllMemberRoles(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM member_roles WHERE guild_id = ? AND player_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all member roles", e);
        }
    }

    @Override
    public void removeAllByRole(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM member_roles WHERE role_id = ?")) {
            ps.setString(1, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all by role", e);
        }
    }

    @Override
    public void removeAllByGuild(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM member_roles WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all by guild", e);
        }
    }
}

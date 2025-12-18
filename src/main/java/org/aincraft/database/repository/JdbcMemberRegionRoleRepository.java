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
import org.aincraft.subregion.MemberRegionRoleRepository;

/**
 * JDBC-based implementation of MemberRegionRoleRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcMemberRegionRoleRepository implements MemberRegionRoleRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcMemberRegionRoleRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void assignRole(String regionId, UUID playerId, String roleId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = getInsertIgnoreSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId);
            ps.setString(2, playerId.toString());
            ps.setString(3, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign region role", e);
        }
    }

    private String getInsertIgnoreSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR IGNORE INTO member_region_roles (region_id, player_id, role_id)
                VALUES (?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT IGNORE INTO member_region_roles (region_id, player_id, role_id)
                VALUES (?, ?, ?)
                """;
            case POSTGRESQL -> """
                INSERT INTO member_region_roles (region_id, player_id, role_id)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """;
            case H2 -> """
                MERGE INTO member_region_roles (region_id, player_id, role_id)
                KEY (region_id, player_id, role_id) VALUES (?, ?, ?)
                """;
        };
    }

    @Override
    public void unassignRole(String regionId, UUID playerId, String roleId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE region_id = ? AND player_id = ? AND role_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId);
            ps.setString(2, playerId.toString());
            ps.setString(3, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unassign region role", e);
        }
    }

    @Override
    public List<String> getMemberRoleIds(String regionId, UUID playerId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String sql = "SELECT role_id FROM member_region_roles WHERE region_id = ? AND player_id = ?";
        List<String> roleIds = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId);
            ps.setString(2, playerId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                roleIds.add(rs.getString("role_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get member region roles", e);
        }

        return roleIds;
    }

    @Override
    public List<UUID> getMembersWithRole(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "SELECT player_id FROM member_region_roles WHERE role_id = ?";
        List<UUID> members = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                members.add(UUID.fromString(rs.getString("player_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get members with region role", e);
        }

        return members;
    }

    @Override
    public boolean hasMemberRole(String regionId, UUID playerId, String roleId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "SELECT 1 FROM member_region_roles WHERE region_id = ? AND player_id = ? AND role_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId);
            ps.setString(2, playerId.toString());
            ps.setString(3, roleId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check member region role", e);
        }
    }

    @Override
    public void removeAllMemberRoles(String regionId, UUID playerId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE region_id = ? AND player_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all member region roles", e);
        }
    }

    @Override
    public void removeAllByRole(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE role_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all assignments for role", e);
        }
    }

    @Override
    public void removeAllByRegion(String regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE region_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all role assignments for region", e);
        }
    }
}

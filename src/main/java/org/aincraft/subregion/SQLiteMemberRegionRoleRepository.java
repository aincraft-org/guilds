package org.aincraft.subregion;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite implementation of MemberRegionRoleRepository.
 */
public class SQLiteMemberRegionRoleRepository implements MemberRegionRoleRepository {
    private final String connectionString;

    @Inject
    public SQLiteMemberRegionRoleRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS member_region_roles (
                region_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                role_id TEXT NOT NULL,
                PRIMARY KEY (region_id, player_id, role_id)
            )
            """;

        String createIndexSQL1 = """
            CREATE INDEX IF NOT EXISTS idx_member_region_roles_region
            ON member_region_roles(region_id)
            """;

        String createIndexSQL2 = """
            CREATE INDEX IF NOT EXISTS idx_member_region_roles_role
            ON member_region_roles(role_id)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            stmt.execute(createIndexSQL1);
            stmt.execute(createIndexSQL2);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize member region roles database", e);
        }
    }

    @Override
    public void assignRole(UUID regionId, UUID playerId, String roleId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "INSERT OR IGNORE INTO member_region_roles (region_id, player_id, role_id) VALUES (?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, playerId.toString());
            pstmt.setString(3, roleId);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign region role", e);
        }
    }

    @Override
    public void unassignRole(UUID regionId, UUID playerId, String roleId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE region_id = ? AND player_id = ? AND role_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, playerId.toString());
            pstmt.setString(3, roleId);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unassign region role", e);
        }
    }

    @Override
    public List<String> getMemberRoleIds(UUID regionId, UUID playerId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String sql = "SELECT role_id FROM member_region_roles WHERE region_id = ? AND player_id = ?";

        List<String> roleIds = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, playerId.toString());

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                roleIds.add(rs.getString("role_id"));
            }

            return roleIds;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get member region roles", e);
        }
    }

    @Override
    public List<UUID> getMembersWithRole(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "SELECT player_id FROM member_region_roles WHERE role_id = ?";

        List<UUID> members = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, roleId);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                members.add(UUID.fromString(rs.getString("player_id")));
            }

            return members;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get members with region role", e);
        }
    }

    @Override
    public boolean hasMemberRole(UUID regionId, UUID playerId, String roleId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "SELECT 1 FROM member_region_roles WHERE region_id = ? AND player_id = ? AND role_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, playerId.toString());
            pstmt.setString(3, roleId);

            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check member region role", e);
        }
    }

    @Override
    public void removeAllMemberRoles(UUID regionId, UUID playerId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE region_id = ? AND player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, playerId.toString());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all member region roles", e);
        }
    }

    @Override
    public void removeAllByRole(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE role_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, roleId);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all assignments for role", e);
        }
    }

    @Override
    public void removeAllByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "DELETE FROM member_region_roles WHERE region_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all role assignments for region", e);
        }
    }
}

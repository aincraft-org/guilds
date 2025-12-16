package org.aincraft.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.sql.*;
import java.util.*;

/**
 * SQLite-based implementation of MemberRoleRepository.
 */
public final class SQLiteMemberRoleRepository implements MemberRoleRepository {
    private final String connectionString;

    @Inject
    public SQLiteMemberRoleRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS member_roles (
                guild_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                role_id TEXT NOT NULL,
                PRIMARY KEY (guild_id, player_id, role_id)
            );
            CREATE INDEX IF NOT EXISTS idx_member_roles_role ON member_roles(role_id);
            CREATE INDEX IF NOT EXISTS idx_member_roles_guild ON member_roles(guild_id);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize member_roles table", e);
        }
    }

    @Override
    public void assignRole(String guildId, UUID playerId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String insertSQL = """
            INSERT OR IGNORE INTO member_roles (guild_id, player_id, role_id)
            VALUES (?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            pstmt.setString(3, roleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign role", e);
        }
    }

    @Override
    public void unassignRole(String guildId, UUID playerId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String deleteSQL = "DELETE FROM member_roles WHERE guild_id = ? AND player_id = ? AND role_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            pstmt.setString(3, roleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unassign role", e);
        }
    }

    @Override
    public List<String> getMemberRoleIds(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String selectSQL = "SELECT role_id FROM member_roles WHERE guild_id = ? AND player_id = ?";
        List<String> roleIds = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT player_id FROM member_roles WHERE role_id = ?";
        List<UUID> members = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, roleId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                members.add(UUID.fromString(rs.getString("player_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get members with role", e);
        }

        return members;
    }

    @Override
    public boolean hasMemberRole(String guildId, UUID playerId, String roleId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String selectSQL = "SELECT 1 FROM member_roles WHERE guild_id = ? AND player_id = ? AND role_id = ? LIMIT 1";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            pstmt.setString(3, roleId);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check member role", e);
        }
    }

    @Override
    public void removeAllMemberRoles(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String deleteSQL = "DELETE FROM member_roles WHERE guild_id = ? AND player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all member roles", e);
        }
    }

    @Override
    public void removeAllByRole(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String deleteSQL = "DELETE FROM member_roles WHERE role_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, roleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all by role", e);
        }
    }

    @Override
    public void removeAllByGuild(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String deleteSQL = "DELETE FROM member_roles WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all by guild", e);
        }
    }
}

package org.aincraft.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.GuildPermission;
import org.aincraft.MemberPermissions;

import java.sql.*;
import java.util.*;

/**
 * SQLite-based implementation of GuildMemberRepository.
 * Persists guild member permissions to SQLite database.
 */
public class SQLiteGuildMemberRepository implements GuildMemberRepository {
    private final String connectionString;

    @Inject
    public SQLiteGuildMemberRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS guild_members (
                guild_id TEXT NOT NULL,
                player_id TEXT NOT NULL,
                permissions INTEGER NOT NULL DEFAULT 7,
                joined_at INTEGER,
                PRIMARY KEY (guild_id, player_id)
            );
            CREATE INDEX IF NOT EXISTS idx_guild_members_guild ON guild_members(guild_id);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }

            // Migration: Add joined_at column to existing tables
            String[] migrations = {
                "ALTER TABLE guild_members ADD COLUMN joined_at INTEGER"
            };

            for (String migration : migrations) {
                try {
                    stmt.execute(migration);
                } catch (SQLException e) {
                    // Column likely already exists, ignore
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize guild_members table", e);
        }
    }

    @Override
    public void addMember(String guildId, UUID playerId, MemberPermissions permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(permissions, "Permissions cannot be null");

        String insertSQL = """
            INSERT OR REPLACE INTO guild_members (guild_id, player_id, permissions, joined_at)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            pstmt.setInt(3, permissions.getBitfield());
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add guild member", e);
        }
    }

    @Override
    public void removeMember(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String deleteSQL = "DELETE FROM guild_members WHERE guild_id = ? AND player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove guild member", e);
        }
    }

    @Override
    public void removeAllMembers(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String deleteSQL = "DELETE FROM guild_members WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all guild members", e);
        }
    }

    @Override
    public Optional<MemberPermissions> getPermissions(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String selectSQL = "SELECT permissions FROM guild_members WHERE guild_id = ? AND player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(MemberPermissions.fromBitfield(rs.getInt("permissions")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get member permissions", e);
        }

        return Optional.empty();
    }

    @Override
    public void setPermissions(String guildId, UUID playerId, MemberPermissions permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(permissions, "Permissions cannot be null");

        String updateSQL = "UPDATE guild_members SET permissions = ? WHERE guild_id = ? AND player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
            pstmt.setInt(1, permissions.getBitfield());
            pstmt.setString(2, guildId);
            pstmt.setString(3, playerId.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set member permissions", e);
        }
    }

    @Override
    public List<UUID> getMembersWithPermission(String guildId, GuildPermission permission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        String selectSQL = "SELECT player_id FROM guild_members WHERE guild_id = ? AND (permissions & ?) != 0";

        List<UUID> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, permission.getBit());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                result.add(UUID.fromString(rs.getString("player_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get members with permission", e);
        }

        return result;
    }

    @Override
    public Optional<Long> getMemberJoinDate(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String selectSQL = "SELECT joined_at FROM guild_members WHERE guild_id = ? AND player_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                long joinedAt = rs.getLong("joined_at");
                return rs.wasNull() ? Optional.empty() : Optional.of(joinedAt);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get member join date", e);
        }

        return Optional.empty();
    }
}

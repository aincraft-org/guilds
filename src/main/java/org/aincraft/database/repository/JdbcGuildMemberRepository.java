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
import java.util.Optional;
import java.util.UUID;
import org.aincraft.GuildPermission;
import org.aincraft.MemberPermissions;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.Sql;
import org.aincraft.storage.GuildMemberRepository;

/**
 * JDBC-based implementation of GuildMemberRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildMemberRepository implements GuildMemberRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildMemberRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void addMember(String guildId, UUID playerId, MemberPermissions permissions) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(permissions, "Permissions cannot be null");

        String sql = Sql.upsertGuildMember(dbType);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, playerId.toString());
            ps.setInt(3, permissions.getBitfield());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add guild member", e);
        }
    }

    @Override
    public void removeMember(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM guild_members WHERE guild_id = ? AND player_id = ?")) {
            ps.setString(1, guildId);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove guild member", e);
        }
    }

    @Override
    public void removeAllMembers(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_members WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove all guild members", e);
        }
    }

    @Override
    public Optional<MemberPermissions> getPermissions(String guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT permissions FROM guild_members WHERE guild_id = ? AND player_id = ?")) {
            ps.setString(1, guildId);
            ps.setString(2, playerId.toString());
            ResultSet rs = ps.executeQuery();

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

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE guild_members SET permissions = ? WHERE guild_id = ? AND player_id = ?")) {
            ps.setInt(1, permissions.getBitfield());
            ps.setString(2, guildId);
            ps.setString(3, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set member permissions", e);
        }
    }

    @Override
    public List<UUID> getMembersWithPermission(String guildId, GuildPermission permission) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(permission, "Permission cannot be null");

        List<UUID> result = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT player_id FROM guild_members WHERE guild_id = ? AND (permissions & ?) != 0")) {
            ps.setString(1, guildId);
            ps.setInt(2, permission.getBit());
            ResultSet rs = ps.executeQuery();

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

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT joined_at FROM guild_members WHERE guild_id = ? AND player_id = ?")) {
            ps.setString(1, guildId);
            ps.setString(2, playerId.toString());
            ResultSet rs = ps.executeQuery();

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

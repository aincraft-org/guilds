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
import org.aincraft.GuildInvite;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.storage.InviteRepository;

/**
 * JDBC-based implementation of InviteRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcInviteRepository implements InviteRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcInviteRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildInvite invite) {
        Objects.requireNonNull(invite, "Invite cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, invite.id());
            ps.setString(2, invite.guildId());
            ps.setString(3, invite.inviterId().toString());
            ps.setString(4, invite.inviteeId().toString());
            ps.setLong(5, invite.createdAt());
            ps.setLong(6, invite.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save invite", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_invites
                (id, guild_id, inviter_id, invitee_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_invites
                (id, guild_id, inviter_id, invitee_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                inviter_id = VALUES(inviter_id), expires_at = VALUES(expires_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_invites
                (id, guild_id, inviter_id, invitee_id, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                inviter_id = EXCLUDED.inviter_id, expires_at = EXCLUDED.expires_at
                """;
            case H2 -> """
                MERGE INTO guild_invites
                (id, guild_id, inviter_id, invitee_id, created_at, expires_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public Optional<GuildInvite> findById(String inviteId) {
        Objects.requireNonNull(inviteId, "Invite ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_invites WHERE id = ?")) {
            ps.setString(1, inviteId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToInvite(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find invite by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public List<GuildInvite> findByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        List<GuildInvite> invites = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM guild_invites WHERE guild_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                invites.add(mapRowToInvite(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find invites by guild ID", e);
        }

        return invites;
    }

    @Override
    public List<GuildInvite> findByInviteeId(UUID inviteeId) {
        Objects.requireNonNull(inviteeId, "Invitee ID cannot be null");

        List<GuildInvite> invites = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM guild_invites WHERE invitee_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, inviteeId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                invites.add(mapRowToInvite(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find invites by invitee ID", e);
        }

        return invites;
    }

    @Override
    public Optional<GuildInvite> findActiveInvite(String guildId, UUID inviteeId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(inviteeId, "Invitee ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM guild_invites WHERE guild_id = ? AND invitee_id = ?")) {
            ps.setString(1, guildId);
            ps.setString(2, inviteeId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToInvite(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active invite", e);
        }

        return Optional.empty();
    }

    @Override
    public int countPendingInvites(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT COUNT(*) FROM guild_invites WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count pending invites", e);
        }

        return 0;
    }

    @Override
    public void delete(String inviteId) {
        Objects.requireNonNull(inviteId, "Invite ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_invites WHERE id = ?")) {
            ps.setString(1, inviteId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete invite", e);
        }
    }

    @Override
    public void deleteExpired() {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_invites WHERE expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete expired invites", e);
        }
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_invites WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete invites by guild ID", e);
        }
    }

    private GuildInvite mapRowToInvite(ResultSet rs) throws SQLException {
        return new GuildInvite(
            rs.getString("id"),
            rs.getString("guild_id"),
            UUID.fromString(rs.getString("inviter_id")),
            UUID.fromString(rs.getString("invitee_id")),
            rs.getLong("created_at"),
            rs.getLong("expires_at")
        );
    }
}

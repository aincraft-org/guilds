package org.aincraft.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.GuildInvite;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of InviteRepository.
 */
public class SQLiteInviteRepository implements InviteRepository {

    private final String connectionString;

    @Inject
    public SQLiteInviteRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS guild_invites (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                inviter_id TEXT NOT NULL,
                invitee_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            );
            """;

        String[] indexes = {
            "CREATE INDEX IF NOT EXISTS idx_invite_guild ON guild_invites(guild_id)",
            "CREATE INDEX IF NOT EXISTS idx_invite_invitee ON guild_invites(invitee_id)",
            "CREATE INDEX IF NOT EXISTS idx_invite_expires ON guild_invites(expires_at)",
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_invite_unique ON guild_invites(guild_id, invitee_id)"
        };

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSQL);

            for (String indexSQL : indexes) {
                stmt.execute(indexSQL);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize guild invites database", e);
        }
    }

    @Override
    public void save(GuildInvite invite) {
        Objects.requireNonNull(invite, "Invite cannot be null");

        String insertSQL = """
            INSERT OR REPLACE INTO guild_invites
            (id, guild_id, inviter_id, invitee_id, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, invite.id());
            pstmt.setString(2, invite.guildId());
            pstmt.setString(3, invite.inviterId().toString());
            pstmt.setString(4, invite.inviteeId().toString());
            pstmt.setLong(5, invite.createdAt());
            pstmt.setLong(6, invite.expiresAt());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save invite", e);
        }
    }

    @Override
    public Optional<GuildInvite> findById(String inviteId) {
        Objects.requireNonNull(inviteId, "Invite ID cannot be null");

        String selectSQL = "SELECT * FROM guild_invites WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, inviteId);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM guild_invites WHERE guild_id = ? ORDER BY created_at DESC";
        List<GuildInvite> invites = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM guild_invites WHERE invitee_id = ? ORDER BY created_at DESC";
        List<GuildInvite> invites = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, inviteeId.toString());
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM guild_invites WHERE guild_id = ? AND invitee_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {

            pstmt.setString(1, guildId);
            pstmt.setString(2, inviteeId.toString());
            ResultSet rs = pstmt.executeQuery();

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

        String countSQL = "SELECT COUNT(*) FROM guild_invites WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(countSQL)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        String deleteSQL = "DELETE FROM guild_invites WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            pstmt.setString(1, inviteId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete invite", e);
        }
    }

    @Override
    public void deleteExpired() {
        String deleteSQL = "DELETE FROM guild_invites WHERE expires_at < ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete expired invites", e);
        }
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String deleteSQL = "DELETE FROM guild_invites WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {

            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
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

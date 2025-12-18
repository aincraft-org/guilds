package org.aincraft.project.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.project.ActiveBuff;
import org.aincraft.project.BuffCategory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SQLiteActiveBuffRepository implements ActiveBuffRepository {

    private final String connectionString;

    @Inject
    public SQLiteActiveBuffRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createBuffsTableSQL = """
            CREATE TABLE IF NOT EXISTS active_buffs (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                project_definition_id TEXT NOT NULL,
                buff_category TEXT NOT NULL,
                buff_value REAL NOT NULL,
                activated_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
            """;

        String createIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_buffs_guild ON active_buffs(guild_id);
            CREATE INDEX IF NOT EXISTS idx_buffs_expires ON active_buffs(expires_at);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {

            stmt.execute(createBuffsTableSQL);

            for (String sql : createIndexSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize buff database", e);
        }
    }

    @Override
    public void save(ActiveBuff buff) {
        Objects.requireNonNull(buff, "Buff cannot be null");

        String sql = """
            INSERT OR REPLACE INTO active_buffs
            (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, buff.id());
            pstmt.setString(2, buff.guildId());
            pstmt.setString(3, buff.projectDefinitionId());
            pstmt.setString(4, buff.category().name());
            pstmt.setDouble(5, buff.value());
            pstmt.setLong(6, buff.activatedAt());
            pstmt.setLong(7, buff.expiresAt());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save active buff", e);
        }
    }

    @Override
    public Optional<ActiveBuff> findById(String buffId) {
        Objects.requireNonNull(buffId, "Buff ID cannot be null");

        String sql = "SELECT * FROM active_buffs WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, buffId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToBuff(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find buff by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public List<ActiveBuff> findByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT * FROM active_buffs WHERE guild_id = ? ORDER BY activated_at DESC";
        List<ActiveBuff> buffs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                buffs.add(mapRowToBuff(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find buffs by guild", e);
        }

        return buffs;
    }

    @Override
    public Optional<ActiveBuff> findActiveByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT * FROM active_buffs WHERE guild_id = ? AND expires_at > ? LIMIT 1";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            pstmt.setLong(2, System.currentTimeMillis());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToBuff(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active buff", e);
        }

        return Optional.empty();
    }

    @Override
    public void delete(String buffId) {
        Objects.requireNonNull(buffId, "Buff ID cannot be null");

        String sql = "DELETE FROM active_buffs WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, buffId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete buff", e);
        }
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "DELETE FROM active_buffs WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete buffs by guild", e);
        }
    }

    @Override
    public void deleteExpired() {
        String sql = "DELETE FROM active_buffs WHERE expires_at <= ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete expired buffs", e);
        }
    }

    private ActiveBuff mapRowToBuff(ResultSet rs) throws SQLException {
        return new ActiveBuff(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("project_definition_id"),
                BuffCategory.valueOf(rs.getString("buff_category")),
                rs.getDouble("buff_value"),
                rs.getLong("activated_at"),
                rs.getLong("expires_at")
        );
    }
}

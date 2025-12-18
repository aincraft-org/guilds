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
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.project.ActiveBuff;
import org.aincraft.project.BuffCategory;
import org.aincraft.project.storage.ActiveBuffRepository;

/**
 * JDBC-based implementation of ActiveBuffRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcActiveBuffRepository implements ActiveBuffRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcActiveBuffRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(ActiveBuff buff) {
        Objects.requireNonNull(buff, "Buff cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buff.id());
            ps.setString(2, buff.guildId());
            ps.setString(3, buff.projectDefinitionId());
            ps.setString(4, buff.category().name());
            ps.setDouble(5, buff.value());
            ps.setLong(6, buff.activatedAt());
            ps.setLong(7, buff.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save active buff", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                buff_value = VALUES(buff_value), activated_at = VALUES(activated_at), expires_at = VALUES(expires_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                buff_value = EXCLUDED.buff_value, activated_at = EXCLUDED.activated_at, expires_at = EXCLUDED.expires_at
                """;
            case H2 -> """
                MERGE INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public Optional<ActiveBuff> findById(String buffId) {
        Objects.requireNonNull(buffId, "Buff ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM active_buffs WHERE id = ?")) {
            ps.setString(1, buffId);
            ResultSet rs = ps.executeQuery();

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

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

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

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setLong(2, System.currentTimeMillis());
            ResultSet rs = ps.executeQuery();

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

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM active_buffs WHERE id = ?")) {
            ps.setString(1, buffId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete buff", e);
        }
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM active_buffs WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete buffs by guild", e);
        }
    }

    @Override
    public void deleteExpired() {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM active_buffs WHERE expires_at <= ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
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

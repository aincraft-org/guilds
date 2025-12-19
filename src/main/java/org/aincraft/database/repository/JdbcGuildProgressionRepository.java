package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.progression.GuildProgression;
import org.aincraft.progression.storage.GuildProgressionRepository;

/**
 * JDBC-based implementation of GuildProgressionRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildProgressionRepository implements GuildProgressionRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildProgressionRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildProgression progression) {
        Objects.requireNonNull(progression, "Progression cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, progression.getGuildId());
            ps.setInt(2, progression.getLevel());
            ps.setLong(3, progression.getCurrentXp());
            ps.setLong(4, progression.getTotalXpEarned());

            if (progression.getLastLevelupTime() != null) {
                ps.setLong(5, progression.getLastLevelupTime());
            } else {
                ps.setNull(5, Types.BIGINT);
            }

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild progression", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                VALUES (?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                level = VALUES(level), current_xp = VALUES(current_xp),
                total_xp_earned = VALUES(total_xp_earned), last_levelup_time = VALUES(last_levelup_time)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (guild_id) DO UPDATE SET
                level = EXCLUDED.level, current_xp = EXCLUDED.current_xp,
                total_xp_earned = EXCLUDED.total_xp_earned, last_levelup_time = EXCLUDED.last_levelup_time
                """;
            case H2 -> """
                MERGE INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                KEY (guild_id) VALUES (?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public Optional<GuildProgression> findByGuildId(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_progression WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToProgression(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild progression", e);
        }

        return Optional.empty();
    }

    @Override
    public void delete(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_progression WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild progression", e);
        }
    }

    @Override
    public void recordContribution(UUID guildId, UUID playerId, long xpAmount) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        if (xpAmount <= 0) {
            return; // Don't record zero or negative contributions
        }

        String sql = getRecordContributionSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.setString(2, playerId.toString());
            ps.setLong(3, xpAmount);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record XP contribution", e);
        }
    }

    private String getRecordContributionSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT INTO guild_xp_contributions (guild_id, player_id, total_xp_contributed, last_contribution_time)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(guild_id, player_id) DO UPDATE SET
                    total_xp_contributed = total_xp_contributed + excluded.total_xp_contributed,
                    last_contribution_time = excluded.last_contribution_time
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_xp_contributions (guild_id, player_id, total_xp_contributed, last_contribution_time)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_xp_contributed = total_xp_contributed + VALUES(total_xp_contributed),
                    last_contribution_time = VALUES(last_contribution_time)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_xp_contributions (guild_id, player_id, total_xp_contributed, last_contribution_time)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (guild_id, player_id) DO UPDATE SET
                    total_xp_contributed = guild_xp_contributions.total_xp_contributed + EXCLUDED.total_xp_contributed,
                    last_contribution_time = EXCLUDED.last_contribution_time
                """;
            case H2 -> """
                MERGE INTO guild_xp_contributions AS t
                USING (VALUES (?, ?, ?, ?)) AS s(guild_id, player_id, total_xp_contributed, last_contribution_time)
                ON t.guild_id = s.guild_id AND t.player_id = s.player_id
                WHEN MATCHED THEN UPDATE SET
                    total_xp_contributed = t.total_xp_contributed + s.total_xp_contributed,
                    last_contribution_time = s.last_contribution_time
                WHEN NOT MATCHED THEN INSERT (guild_id, player_id, total_xp_contributed, last_contribution_time)
                    VALUES (s.guild_id, s.player_id, s.total_xp_contributed, s.last_contribution_time)
                """;
        };
    }

    @Override
    public Map<UUID, Long> getTopContributors(UUID guildId, int limit) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = """
            SELECT player_id, total_xp_contributed
            FROM guild_xp_contributions
            WHERE guild_id = ?
            ORDER BY total_xp_contributed DESC
            LIMIT ?
            """;

        Map<UUID, Long> contributors = new LinkedHashMap<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_id"));
                long xp = rs.getLong("total_xp_contributed");
                contributors.put(playerId, xp);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get top contributors", e);
        }

        return contributors;
    }

    @Override
    public long getPlayerContribution(UUID guildId, UUID playerId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String sql = """
            SELECT total_xp_contributed
            FROM guild_xp_contributions
            WHERE guild_id = ? AND player_id = ?
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.setString(2, playerId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getLong("total_xp_contributed");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get player contribution", e);
        }

        return 0L;
    }

    @Override
    public void deleteContributions(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_xp_contributions WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete contributions", e);
        }
    }

    private GuildProgression mapRowToProgression(ResultSet rs) throws SQLException {
        UUID guildId = UUID.fromString(rs.getString("guild_id"));
        int level = rs.getInt("level");
        long currentXp = rs.getLong("current_xp");
        long totalXpEarned = rs.getLong("total_xp_earned");
        long lastLevelupTime = rs.getLong("last_levelup_time");
        Long lastLevelup = rs.wasNull() ? null : lastLevelupTime;

        return new GuildProgression(guildId, level, currentXp, totalXpEarned, lastLevelup);
    }
}

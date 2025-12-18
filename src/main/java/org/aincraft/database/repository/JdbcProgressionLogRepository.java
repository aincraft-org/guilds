package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.progression.ProgressionLog;
import org.aincraft.progression.storage.ProgressionLogRepository;

/**
 * JDBC-based implementation of ProgressionLogRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcProgressionLogRepository implements ProgressionLogRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcProgressionLogRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void log(ProgressionLog entry) {
        Objects.requireNonNull(entry, "Log entry cannot be null");

        String sql = """
            INSERT INTO progression_logs
            (guild_id, player_id, action, amount, details, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.guildId());

            if (entry.playerId() != null) {
                ps.setString(2, entry.playerId().toString());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }

            ps.setString(3, entry.action().name());
            ps.setLong(4, entry.amount());
            ps.setString(5, entry.details());
            ps.setLong(6, entry.timestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log progression action", e);
        }
    }

    @Override
    public List<ProgressionLog> findByGuild(String guildId, int limit) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = """
            SELECT * FROM progression_logs
            WHERE guild_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<ProgressionLog> logs = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                logs.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find progression logs by guild ID", e);
        }

        return logs;
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM progression_logs WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete progression logs", e);
        }
    }

    private ProgressionLog mapResultSet(ResultSet rs) throws SQLException {
        String playerIdStr = rs.getString("player_id");
        UUID playerId = playerIdStr != null ? UUID.fromString(playerIdStr) : null;

        return new ProgressionLog(
            rs.getLong("id"),
            rs.getString("guild_id"),
            playerId,
            ProgressionLog.ActionType.valueOf(rs.getString("action")),
            rs.getLong("amount"),
            rs.getString("details"),
            rs.getLong("timestamp")
        );
    }
}

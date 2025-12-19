package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.Sql;
import org.aincraft.storage.PlayerGuildMapping;

/**
 * JDBC-based implementation of PlayerGuildMapping.
 * Works with all supported database types.
 */
@Singleton
public class JdbcPlayerGuildMapping implements PlayerGuildMapping {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcPlayerGuildMapping(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void addPlayerToGuild(UUID playerId, UUID guildId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = Sql.upsertPlayerGuild(dbType);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add player to guild", e);
        }
    }

    @Override
    public void removePlayerFromGuild(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM player_guilds WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove player from guild", e);
        }
    }

    @Override
    public Optional<UUID> getPlayerGuildId(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT guild_id FROM player_guilds WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(UUID.fromString(rs.getString("guild_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get player guild ID", e);
        }

        return Optional.empty();
    }

    @Override
    public boolean isPlayerInGuild(UUID playerId) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM player_guilds WHERE player_id = ? LIMIT 1")) {
            ps.setString(1, playerId.toString());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check if player is in guild", e);
        }
    }
}

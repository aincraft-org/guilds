package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.GuildDefaultPermissions;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.storage.GuildDefaultPermissionsRepository;
import org.aincraft.subregion.SubjectType;

/**
 * JDBC-based implementation of GuildDefaultPermissionsRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildDefaultPermissionsRepository implements GuildDefaultPermissionsRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    private static final int DEFAULT_ALLY_PERMISSIONS = 4;
    private static final int DEFAULT_ENEMY_PERMISSIONS = 0;
    private static final int DEFAULT_OUTSIDER_PERMISSIONS = 0;

    @Inject
    public JdbcGuildDefaultPermissionsRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildDefaultPermissions permissions) {
        Objects.requireNonNull(permissions, "Permissions cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, permissions.getGuildId());
            ps.setInt(2, permissions.getAllyPermissions());
            ps.setInt(3, permissions.getEnemyPermissions());
            ps.setInt(4, permissions.getOutsiderPermissions());
            ps.setLong(5, permissions.getCreatedAt());
            ps.setLong(6, permissions.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild default permissions", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_default_permissions
                (guild_id, ally_permissions, enemy_permissions, outsider_permissions, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_default_permissions
                (guild_id, ally_permissions, enemy_permissions, outsider_permissions, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                ally_permissions = VALUES(ally_permissions), enemy_permissions = VALUES(enemy_permissions),
                outsider_permissions = VALUES(outsider_permissions), updated_at = VALUES(updated_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_default_permissions
                (guild_id, ally_permissions, enemy_permissions, outsider_permissions, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (guild_id) DO UPDATE SET
                ally_permissions = EXCLUDED.ally_permissions, enemy_permissions = EXCLUDED.enemy_permissions,
                outsider_permissions = EXCLUDED.outsider_permissions, updated_at = EXCLUDED.updated_at
                """;
            case H2 -> """
                MERGE INTO guild_default_permissions
                (guild_id, ally_permissions, enemy_permissions, outsider_permissions, created_at, updated_at)
                KEY (guild_id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public Optional<GuildDefaultPermissions> findByGuildId(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM guild_default_permissions WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild default permissions", e);
        }
    }

    @Override
    public void delete(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM guild_default_permissions WHERE guild_id = ?")) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild default permissions", e);
        }
    }

    @Override
    public int getPermissions(UUID guildId, SubjectType subjectType) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        Optional<GuildDefaultPermissions> perms = findByGuildId(guildId);

        if (perms.isEmpty()) {
            return getDefaultPermissions(subjectType);
        }

        return switch (subjectType) {
            case GUILD_ALLY -> perms.get().getAllyPermissions();
            case GUILD_ENEMY -> perms.get().getEnemyPermissions();
            case GUILD_OUTSIDER -> perms.get().getOutsiderPermissions();
            default -> throw new IllegalArgumentException("Invalid relationship subject type: " + subjectType);
        };
    }

    private int getDefaultPermissions(SubjectType subjectType) {
        return switch (subjectType) {
            case GUILD_ALLY -> DEFAULT_ALLY_PERMISSIONS;
            case GUILD_ENEMY -> DEFAULT_ENEMY_PERMISSIONS;
            case GUILD_OUTSIDER -> DEFAULT_OUTSIDER_PERMISSIONS;
            default -> 0;
        };
    }

    private GuildDefaultPermissions mapResultSet(ResultSet rs) throws SQLException {
        return new GuildDefaultPermissions(
            UUID.fromString(rs.getString("guild_id")),
            rs.getInt("ally_permissions"),
            rs.getInt("enemy_permissions"),
            rs.getInt("outsider_permissions")
        );
    }
}

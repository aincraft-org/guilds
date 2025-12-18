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
import org.aincraft.GuildRole;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.storage.GuildRoleRepository;

/**
 * JDBC-based implementation of GuildRoleRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildRoleRepository implements GuildRoleRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildRoleRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildRole role) {
        Objects.requireNonNull(role, "Role cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.getId());
            ps.setString(2, role.getGuildId());
            ps.setString(3, role.getName());
            ps.setInt(4, role.getPermissions());
            ps.setInt(5, role.getPriority());
            ps.setString(6, null); // prefix - unused for now
            ps.setString(7, null); // color - unused for now
            ps.setString(8, role.getCreatedBy() != null ? role.getCreatedBy().toString() : null);
            ps.setObject(9, role.getCreatedAt()); // handles null
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild role", e);
        }
    }

    private String getUpsertSql() {
        return org.aincraft.database.Sql.upsertGuildRole(dbType);
    }

    @Override
    public void delete(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_roles WHERE id = ?")) {
            ps.setString(1, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild role", e);
        }
    }

    @Override
    public Optional<GuildRole> findById(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, guild_id, name, permissions, priority, created_by, created_at FROM guild_roles WHERE id = ?")) {
            ps.setString(1, roleId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild role by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public List<GuildRole> findByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        List<GuildRole> roles = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, guild_id, name, permissions, priority, created_by, created_at FROM guild_roles WHERE guild_id = ? ORDER BY priority DESC")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                roles.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild roles", e);
        }

        return roles;
    }

    @Override
    public Optional<GuildRole> findByGuildAndName(String guildId, String name) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, guild_id, name, permissions, priority, created_by, created_at FROM guild_roles WHERE guild_id = ? AND name = ?")) {
            ps.setString(1, guildId);
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild role by name", e);
        }

        return Optional.empty();
    }

    @Override
    public void deleteAllByGuild(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_roles WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all guild roles", e);
        }
    }

    private GuildRole mapResultSet(ResultSet rs) throws SQLException {
        UUID createdBy = null;
        String createdByStr = rs.getString("created_by");
        if (createdByStr != null) {
            createdBy = UUID.fromString(createdByStr);
        }

        Long createdAt = null;
        long createdAtValue = rs.getLong("created_at");
        if (!rs.wasNull()) {
            createdAt = createdAtValue;
        }

        return new GuildRole(
            rs.getString("id"),
            rs.getString("guild_id"),
            rs.getString("name"),
            rs.getInt("permissions"),
            rs.getInt("priority"),
            createdBy,
            createdAt
        );
    }
}

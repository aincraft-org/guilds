package org.aincraft.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.GuildRole;

import java.sql.*;
import java.util.*;

/**
 * SQLite-based implementation of GuildRoleRepository.
 */
public final class SQLiteGuildRoleRepository implements GuildRoleRepository {
    private final String connectionString;

    @Inject
    public SQLiteGuildRoleRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS guild_roles (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                name TEXT NOT NULL,
                permissions INTEGER NOT NULL DEFAULT 0,
                UNIQUE(guild_id, name)
            );
            CREATE INDEX IF NOT EXISTS idx_guild_roles_guild ON guild_roles(guild_id);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize guild_roles table", e);
        }
    }

    @Override
    public void save(GuildRole role) {
        Objects.requireNonNull(role, "Role cannot be null");

        String upsertSQL = """
            INSERT OR REPLACE INTO guild_roles (id, guild_id, name, permissions)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {
            pstmt.setString(1, role.getId());
            pstmt.setString(2, role.getGuildId());
            pstmt.setString(3, role.getName());
            pstmt.setInt(4, role.getPermissions());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild role", e);
        }
    }

    @Override
    public void delete(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String deleteSQL = "DELETE FROM guild_roles WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, roleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild role", e);
        }
    }

    @Override
    public Optional<GuildRole> findById(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String selectSQL = "SELECT id, guild_id, name, permissions FROM guild_roles WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, roleId);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT id, guild_id, name, permissions FROM guild_roles WHERE guild_id = ?";
        List<GuildRole> roles = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT id, guild_id, name, permissions FROM guild_roles WHERE guild_id = ? AND name = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, name);
            ResultSet rs = pstmt.executeQuery();

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

        String deleteSQL = "DELETE FROM guild_roles WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all guild roles", e);
        }
    }

    private GuildRole mapResultSet(ResultSet rs) throws SQLException {
        return new GuildRole(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("name"),
                rs.getInt("permissions")
        );
    }
}

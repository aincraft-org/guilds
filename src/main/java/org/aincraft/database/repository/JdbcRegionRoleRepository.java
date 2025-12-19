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
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.subregion.RegionRole;
import org.aincraft.subregion.RegionRoleRepository;

/**
 * JDBC-based implementation of RegionRoleRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcRegionRoleRepository implements RegionRoleRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcRegionRoleRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(RegionRole role) {
        Objects.requireNonNull(role, "Role cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.getId());
            ps.setString(2, role.getRegionId().toString());
            ps.setString(3, role.getName());
            ps.setInt(4, role.getPermissions());
            ps.setLong(5, role.getCreatedAt());
            ps.setString(6, role.getCreatedBy().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save region role", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE name = VALUES(name), permissions = VALUES(permissions)
                """;
            case POSTGRESQL -> """
                INSERT INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, permissions = EXCLUDED.permissions
                """;
            case H2 -> """
                MERGE INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public void delete(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM region_roles WHERE id = ?")) {
            ps.setString(1, roleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete region role", e);
        }
    }

    @Override
    public Optional<RegionRole> findById(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM region_roles WHERE id = ?")) {
            ps.setString(1, roleId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find region role by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public List<RegionRole> findByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        List<RegionRole> roles = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM region_roles WHERE region_id = ?")) {
            ps.setString(1, regionId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                roles.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find roles for region", e);
        }

        return roles;
    }

    @Override
    public Optional<RegionRole> findByRegionAndName(UUID regionId, String name) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM region_roles WHERE region_id = ? AND name = ?")) {
            ps.setString(1, regionId.toString());
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find region role by region and name", e);
        }

        return Optional.empty();
    }

    @Override
    public void deleteAllByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM region_roles WHERE region_id = ?")) {
            ps.setString(1, regionId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all roles for region", e);
        }
    }

    private RegionRole mapResultSet(ResultSet rs) throws SQLException {
        return new RegionRole(
            rs.getString("id"),
            rs.getString("region_id"),
            rs.getString("name"),
            rs.getInt("permissions"),
            rs.getLong("created_at"),
            UUID.fromString(rs.getString("created_by"))
        );
    }

    // Note: RegionRole constructor is updated to convert String to UUID internally
}

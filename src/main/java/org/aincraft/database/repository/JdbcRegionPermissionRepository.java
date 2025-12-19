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
import org.aincraft.subregion.RegionPermission;
import org.aincraft.subregion.RegionPermissionRepository;
import org.aincraft.subregion.SubjectType;

/**
 * JDBC-based implementation of RegionPermissionRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcRegionPermissionRepository implements RegionPermissionRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcRegionPermissionRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(RegionPermission permission) {
        Objects.requireNonNull(permission, "Permission cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, permission.getId());
            ps.setString(2, permission.getRegionId().toString());
            ps.setString(3, permission.getSubjectId());
            ps.setString(4, permission.getSubjectType().name());
            ps.setInt(5, permission.getPermissions());
            ps.setLong(6, permission.getCreatedAt());
            ps.setString(7, permission.getCreatedBy().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save region permission", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE permissions = VALUES(permissions)
                """;
            case POSTGRESQL -> """
                INSERT INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET permissions = EXCLUDED.permissions
                """;
            case H2 -> """
                MERGE INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public void delete(String permissionId) {
        Objects.requireNonNull(permissionId, "Permission ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM region_permissions WHERE id = ?")) {
            ps.setString(1, permissionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete region permission", e);
        }
    }

    @Override
    public Optional<RegionPermission> findById(String id) {
        Objects.requireNonNull(id, "ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM region_permissions WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find region permission by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public List<RegionPermission> findByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        List<RegionPermission> permissions = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM region_permissions WHERE region_id = ?")) {
            ps.setString(1, regionId.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                permissions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find permissions for region", e);
        }

        return permissions;
    }

    @Override
    public Optional<RegionPermission> findByRegionAndSubject(UUID regionId, String subjectId, SubjectType subjectType) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(subjectId, "Subject ID cannot be null");
        Objects.requireNonNull(subjectType, "Subject type cannot be null");

        String sql = """
            SELECT * FROM region_permissions
            WHERE region_id = ? AND subject_id = ? AND subject_type = ?
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId.toString());
            ps.setString(2, subjectId);
            ps.setString(3, subjectType.name());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find permission by region and subject", e);
        }

        return Optional.empty();
    }

    @Override
    public List<RegionPermission> findPlayerPermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "SELECT * FROM region_permissions WHERE region_id = ? AND subject_type = ?";
        List<RegionPermission> permissions = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId.toString());
            ps.setString(2, SubjectType.PLAYER.name());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                permissions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find player permissions", e);
        }

        return permissions;
    }

    @Override
    public List<RegionPermission> findRolePermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "SELECT * FROM region_permissions WHERE region_id = ? AND subject_type = ?";
        List<RegionPermission> permissions = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, regionId.toString());
            ps.setString(2, SubjectType.ROLE.name());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                permissions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find role permissions", e);
        }

        return permissions;
    }

    @Override
    public void deleteAllByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM region_permissions WHERE region_id = ?")) {
            ps.setString(1, regionId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all permissions for region", e);
        }
    }

    private RegionPermission mapResultSet(ResultSet rs) throws SQLException {
        return new RegionPermission(
            rs.getString("id"),
            UUID.fromString(rs.getString("region_id")),
            rs.getString("subject_id"),
            SubjectType.valueOf(rs.getString("subject_type")),
            rs.getInt("permissions"),
            rs.getLong("created_at"),
            UUID.fromString(rs.getString("created_by"))
        );
    }
}

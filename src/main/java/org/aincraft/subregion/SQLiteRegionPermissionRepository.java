package org.aincraft.subregion;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of RegionPermissionRepository.
 * Single Responsibility: Region permission persistence using SQLite.
 */
public class SQLiteRegionPermissionRepository implements RegionPermissionRepository {
    private final String connectionString;

    @Inject
    public SQLiteRegionPermissionRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS region_permissions (
                id TEXT PRIMARY KEY,
                region_id TEXT NOT NULL,
                subject_id TEXT NOT NULL,
                subject_type TEXT NOT NULL,
                permissions INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                created_by TEXT NOT NULL,
                UNIQUE(region_id, subject_id, subject_type)
            )
            """;

        String createIndexSQL1 = """
            CREATE INDEX IF NOT EXISTS idx_region_permissions_region
            ON region_permissions(region_id)
            """;

        String createIndexSQL2 = """
            CREATE INDEX IF NOT EXISTS idx_region_permissions_subject
            ON region_permissions(subject_id, subject_type)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            stmt.execute(createIndexSQL1);
            stmt.execute(createIndexSQL2);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize region permissions database", e);
        }
    }

    @Override
    public void save(RegionPermission permission) {
        Objects.requireNonNull(permission, "Permission cannot be null");

        String sql = """
            INSERT OR REPLACE INTO region_permissions
            (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, permission.getId());
            pstmt.setString(2, permission.getRegionId().toString());
            pstmt.setString(3, permission.getSubjectId());
            pstmt.setString(4, permission.getSubjectType().name());
            pstmt.setInt(5, permission.getPermissions());
            pstmt.setLong(6, permission.getCreatedAt());
            pstmt.setString(7, permission.getCreatedBy().toString());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save region permission", e);
        }
    }

    @Override
    public void delete(String permissionId) {
        Objects.requireNonNull(permissionId, "Permission ID cannot be null");

        String sql = "DELETE FROM region_permissions WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, permissionId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete region permission", e);
        }
    }

    @Override
    public Optional<RegionPermission> findById(String id) {
        Objects.requireNonNull(id, "ID cannot be null");

        String sql = "SELECT * FROM region_permissions WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find region permission by ID", e);
        }
    }

    @Override
    public List<RegionPermission> findByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "SELECT * FROM region_permissions WHERE region_id = ?";

        List<RegionPermission> permissions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                permissions.add(mapResultSet(rs));
            }

            return permissions;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find permissions for region", e);
        }
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

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, subjectId);
            pstmt.setString(3, subjectType.name());

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find permission by region and subject", e);
        }
    }

    @Override
    public List<RegionPermission> findPlayerPermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = """
            SELECT * FROM region_permissions
            WHERE region_id = ? AND subject_type = ?
            """;

        List<RegionPermission> permissions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, SubjectType.PLAYER.name());

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                permissions.add(mapResultSet(rs));
            }

            return permissions;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find player permissions", e);
        }
    }

    @Override
    public List<RegionPermission> findRolePermissions(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = """
            SELECT * FROM region_permissions
            WHERE region_id = ? AND subject_type = ?
            """;

        List<RegionPermission> permissions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, SubjectType.ROLE.name());

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                permissions.add(mapResultSet(rs));
            }

            return permissions;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find role permissions", e);
        }
    }

    @Override
    public void deleteAllByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "DELETE FROM region_permissions WHERE region_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.executeUpdate();
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

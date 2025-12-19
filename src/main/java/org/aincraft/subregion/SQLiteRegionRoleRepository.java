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
 * SQLite implementation of RegionRoleRepository.
 */
public class SQLiteRegionRoleRepository implements RegionRoleRepository {
    private final String connectionString;

    @Inject
    public SQLiteRegionRoleRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS region_roles (
                id TEXT PRIMARY KEY,
                region_id TEXT NOT NULL,
                name TEXT NOT NULL,
                permissions INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                created_by TEXT NOT NULL,
                UNIQUE(region_id, name)
            )
            """;

        String createIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_region_roles_region
            ON region_roles(region_id)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            stmt.execute(createIndexSQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize region roles database", e);
        }
    }

    @Override
    public void save(RegionRole role) {
        Objects.requireNonNull(role, "Role cannot be null");

        String sql = """
            INSERT OR REPLACE INTO region_roles
            (id, region_id, name, permissions, created_at, created_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, role.getId());
            pstmt.setString(2, role.getRegionId().toString());
            pstmt.setString(3, role.getName());
            pstmt.setInt(4, role.getPermissions());
            pstmt.setLong(5, role.getCreatedAt());
            pstmt.setString(6, role.getCreatedBy().toString());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save region role", e);
        }
    }

    @Override
    public void delete(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "DELETE FROM region_roles WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, roleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete region role", e);
        }
    }

    @Override
    public Optional<RegionRole> findById(String roleId) {
        Objects.requireNonNull(roleId, "Role ID cannot be null");

        String sql = "SELECT * FROM region_roles WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, roleId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find region role by ID", e);
        }
    }

    @Override
    public List<RegionRole> findByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "SELECT * FROM region_roles WHERE region_id = ?";

        List<RegionRole> roles = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                roles.add(mapResultSet(rs));
            }

            return roles;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find roles for region", e);
        }
    }

    @Override
    public Optional<RegionRole> findByRegionAndName(UUID regionId, String name) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");
        Objects.requireNonNull(name, "Name cannot be null");

        String sql = "SELECT * FROM region_roles WHERE region_id = ? AND name = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.setString(2, name);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find region role by region and name", e);
        }
    }

    @Override
    public void deleteAllByRegion(UUID regionId) {
        Objects.requireNonNull(regionId, "Region ID cannot be null");

        String sql = "DELETE FROM region_roles WHERE region_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, regionId.toString());
            pstmt.executeUpdate();
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
}

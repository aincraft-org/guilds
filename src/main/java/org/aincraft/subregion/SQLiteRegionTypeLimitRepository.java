package org.aincraft.subregion;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite implementation of RegionTypeLimitRepository.
 */
public class SQLiteRegionTypeLimitRepository implements RegionTypeLimitRepository {

    private final String connectionString;

    @Inject
    public SQLiteRegionTypeLimitRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS region_type_limits (
                type_id TEXT PRIMARY KEY,
                max_total_volume INTEGER NOT NULL
            )
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize region_type_limits table", e);
        }
    }

    @Override
    public void save(RegionTypeLimit limit) {
        Objects.requireNonNull(limit, "Limit cannot be null");

        String upsertSQL = """
            INSERT OR REPLACE INTO region_type_limits (type_id, max_total_volume)
            VALUES (?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {
            pstmt.setString(1, limit.typeId());
            pstmt.setLong(2, limit.maxTotalVolume());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save region type limit", e);
        }
    }

    @Override
    public void delete(String typeId) {
        Objects.requireNonNull(typeId, "Type ID cannot be null");

        String deleteSQL = "DELETE FROM region_type_limits WHERE type_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, typeId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete region type limit", e);
        }
    }

    @Override
    public Optional<RegionTypeLimit> findByTypeId(String typeId) {
        if (typeId == null) {
            return Optional.empty();
        }

        String selectSQL = "SELECT * FROM region_type_limits WHERE type_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, typeId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find region type limit", e);
        }

        return Optional.empty();
    }

    @Override
    public List<RegionTypeLimit> findAll() {
        String selectSQL = "SELECT * FROM region_type_limits ORDER BY type_id";
        List<RegionTypeLimit> limits = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSQL)) {

            while (rs.next()) {
                limits.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all region type limits", e);
        }

        return limits;
    }

    private RegionTypeLimit mapResultSet(ResultSet rs) throws SQLException {
        return new RegionTypeLimit(
                rs.getString("type_id"),
                rs.getLong("max_total_volume")
        );
    }
}

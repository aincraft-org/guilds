package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.subregion.RegionTypeLimit;
import org.aincraft.subregion.RegionTypeLimitRepository;

/**
 * JDBC-based implementation of RegionTypeLimitRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcRegionTypeLimitRepository implements RegionTypeLimitRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcRegionTypeLimitRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(RegionTypeLimit limit) {
        Objects.requireNonNull(limit, "Limit cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, limit.typeId());
            ps.setLong(2, limit.maxTotalVolume());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save region type limit", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO region_type_limits (type_id, max_total_volume)
                VALUES (?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO region_type_limits (type_id, max_total_volume)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE max_total_volume = VALUES(max_total_volume)
                """;
            case POSTGRESQL -> """
                INSERT INTO region_type_limits (type_id, max_total_volume)
                VALUES (?, ?)
                ON CONFLICT (type_id) DO UPDATE SET max_total_volume = EXCLUDED.max_total_volume
                """;
            case H2 -> """
                MERGE INTO region_type_limits (type_id, max_total_volume)
                KEY (type_id) VALUES (?, ?)
                """;
        };
    }

    @Override
    public void delete(String typeId) {
        Objects.requireNonNull(typeId, "Type ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM region_type_limits WHERE type_id = ?")) {
            ps.setString(1, typeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete region type limit", e);
        }
    }

    @Override
    public Optional<RegionTypeLimit> findByTypeId(String typeId) {
        if (typeId == null) {
            return Optional.empty();
        }

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM region_type_limits WHERE type_id = ?")) {
            ps.setString(1, typeId);
            ResultSet rs = ps.executeQuery();

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
        List<RegionTypeLimit> limits = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM region_type_limits ORDER BY type_id")) {

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

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
import org.aincraft.GuildRelationship;
import org.aincraft.RelationStatus;
import org.aincraft.RelationType;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.storage.GuildRelationshipRepository;

/**
 * JDBC-based implementation of GuildRelationshipRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildRelationshipRepository implements GuildRelationshipRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildRelationshipRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildRelationship relationship) {
        Objects.requireNonNull(relationship, "Relationship cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, relationship.getId());
            ps.setString(2, relationship.getSourceGuildId());
            ps.setString(3, relationship.getTargetGuildId());
            ps.setString(4, relationship.getRelationType().name());
            ps.setString(5, relationship.getStatus().name());
            ps.setLong(6, relationship.getCreatedAt());
            ps.setString(7, relationship.getCreatedBy().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild relationship", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_relationships
                (id, source_guild_id, target_guild_id, relation_type, status, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_relationships
                (id, source_guild_id, target_guild_id, relation_type, status, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                relation_type = VALUES(relation_type), status = VALUES(status)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_relationships
                (id, source_guild_id, target_guild_id, relation_type, status, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                relation_type = EXCLUDED.relation_type, status = EXCLUDED.status
                """;
            case H2 -> """
                MERGE INTO guild_relationships
                (id, source_guild_id, target_guild_id, relation_type, status, created_at, created_by)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public void delete(String relationshipId) {
        Objects.requireNonNull(relationshipId, "Relationship ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_relationships WHERE id = ?")) {
            ps.setString(1, relationshipId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild relationship", e);
        }
    }

    @Override
    public Optional<GuildRelationship> findById(String id) {
        Objects.requireNonNull(id, "ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_relationships WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild relationship by ID", e);
        }
    }

    @Override
    public Optional<GuildRelationship> findRelationship(String guildId1, String guildId2) {
        Objects.requireNonNull(guildId1, "Guild ID 1 cannot be null");
        Objects.requireNonNull(guildId2, "Guild ID 2 cannot be null");

        String sql = """
            SELECT * FROM guild_relationships
            WHERE ((source_guild_id = ? AND target_guild_id = ?)
               OR (source_guild_id = ? AND target_guild_id = ?))
            AND status = ?
            LIMIT 1
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId1);
            ps.setString(2, guildId2);
            ps.setString(3, guildId2);
            ps.setString(4, guildId1);
            ps.setString(5, RelationStatus.ACTIVE.name());

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find relationship between guilds", e);
        }
    }

    @Override
    public List<GuildRelationship> findAllByGuild(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = """
            SELECT * FROM guild_relationships
            WHERE source_guild_id = ? OR target_guild_id = ?
            """;

        List<GuildRelationship> relationships = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, guildId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                relationships.add(mapResultSet(rs));
            }

            return relationships;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find relationships for guild", e);
        }
    }

    @Override
    public List<GuildRelationship> findByType(String guildId, RelationType type, RelationStatus status) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(type, "Relation type cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");

        String sql = """
            SELECT * FROM guild_relationships
            WHERE (source_guild_id = ? OR target_guild_id = ?)
            AND relation_type = ?
            AND status = ?
            """;

        List<GuildRelationship> relationships = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, guildId);
            ps.setString(3, type.name());
            ps.setString(4, status.name());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                relationships.add(mapResultSet(rs));
            }

            return relationships;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find relationships by type", e);
        }
    }

    @Override
    public void deleteAllByGuild(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "DELETE FROM guild_relationships WHERE source_guild_id = ? OR target_guild_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all relationships for guild", e);
        }
    }

    private GuildRelationship mapResultSet(ResultSet rs) throws SQLException {
        return new GuildRelationship(
            rs.getString("id"),
            rs.getString("source_guild_id"),
            rs.getString("target_guild_id"),
            RelationType.valueOf(rs.getString("relation_type")),
            RelationStatus.valueOf(rs.getString("status")),
            rs.getLong("created_at"),
            UUID.fromString(rs.getString("created_by"))
        );
    }
}

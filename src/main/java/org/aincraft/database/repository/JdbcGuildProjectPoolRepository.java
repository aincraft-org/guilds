package org.aincraft.database.repository;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.Sql;
import org.aincraft.project.BuffDefinition;
import org.aincraft.project.BuffType;
import org.aincraft.project.ProjectDefinition;
import org.aincraft.project.storage.GuildProjectPoolRepository;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of GuildProjectPoolRepository.
 * Persists project pools to database for recovery after server restarts.
 */
@Singleton
public class JdbcGuildProjectPoolRepository implements GuildProjectPoolRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;
    private final Gson gson;

    @Inject
    public JdbcGuildProjectPoolRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "Connection provider cannot be null");
        this.dbType = connectionProvider.getDatabaseType();
        this.gson = new Gson();
    }

    @Override
    public void savePool(String guildId, List<ProjectDefinition> projects, long poolGenerationTime) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(projects, "Projects cannot be null");

        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete old pool entries for this guild
                try (PreparedStatement ps = conn.prepareStatement(Sql.deleteGuildProjectPool(dbType))) {
                    ps.setString(1, guildId);
                    ps.executeUpdate();
                }

                // Insert new pool entries (batch insert)
                String insertSql = Sql.insertGuildProjectPool(dbType);
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    long createdAt = System.currentTimeMillis();

                    for (ProjectDefinition project : projects) {
                        ps.setString(1, UUID.randomUUID().toString()); // Unique ID for pool entry
                        ps.setString(2, guildId);
                        ps.setString(3, project.id());
                        ps.setString(4, project.name());
                        ps.setString(5, project.description());
                        ps.setString(6, project.buffType().name());
                        ps.setString(7, project.buff().categoryId());
                        ps.setDouble(8, project.buff().value());
                        ps.setString(9, project.buff().displayName());
                        ps.setLong(10, project.buffDurationMillis());
                        ps.setString(11, serializeMaterials(project.materials()));
                        ps.setInt(12, project.requiredLevel());
                        ps.setLong(13, createdAt);
                        ps.setLong(14, poolGenerationTime);

                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new RuntimeException("Failed to save project pool for guild " + guildId, e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save project pool", e);
        }
    }

    @Override
    public List<ProjectDefinition> getPool(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(Sql.selectGuildProjectPool(dbType))) {
            ps.setString(1, guildId);

            List<ProjectDefinition> projects = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProjectDefinition project = parseProjectFromResultSet(rs);
                    projects.add(project);
                }
            }

            return projects;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load project pool for guild " + guildId, e);
        }
    }

    @Override
    public void deletePoolByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(Sql.deleteGuildProjectPool(dbType))) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete project pool for guild " + guildId, e);
        }
    }

    @Override
    public Optional<Long> getLastPoolGenerationTime(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(Sql.selectLastPoolGenerationTime(dbType))) {
            ps.setString(1, guildId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long lastPoolTime = rs.getLong("last_pool_time");
                    if (rs.wasNull()) {
                        return Optional.empty();
                    }
                    return Optional.of(lastPoolTime);
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get last pool generation time for guild " + guildId, e);
        }
    }

    @Override
    public void setGuildCreatedAt(String guildId, long timestamp) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection()) {
            String sql = Sql.updateGuildCreatedAt(dbType);

            if (dbType == DatabaseType.H2) {
                // H2 requires special handling
                sql = """
                    MERGE INTO guild_project_pool_seed AS t
                    USING (VALUES (?, ?, 0)) AS s(guild_id, guild_created_at, seed)
                    ON t.guild_id = s.guild_id
                    WHEN MATCHED THEN UPDATE SET guild_created_at = s.guild_created_at
                    WHEN NOT MATCHED THEN INSERT (guild_id, guild_created_at, seed) VALUES (s.guild_id, s.guild_created_at, s.seed)
                    """;
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, guildId);
                ps.setLong(2, timestamp);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set guild created_at for guild " + guildId, e);
        }
    }

    @Override
    public Optional<Long> getGuildCreatedAt(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(Sql.selectGuildCreatedAt(dbType))) {
            ps.setString(1, guildId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long createdAt = rs.getLong("guild_created_at");
                    if (rs.wasNull()) {
                        return Optional.empty();
                    }
                    return Optional.of(createdAt);
                }
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get guild created_at for guild " + guildId, e);
        }
    }

    /**
     * Parses a ProjectDefinition from a ResultSet row.
     */
    private ProjectDefinition parseProjectFromResultSet(ResultSet rs) throws SQLException {
        String id = rs.getString("project_definition_id");
        String name = rs.getString("project_name");
        String description = rs.getString("description");
        int requiredLevel = rs.getInt("required_level");
        BuffType buffType = BuffType.valueOf(rs.getString("buff_type"));
        String buffCategory = rs.getString("buff_category");
        double buffValue = rs.getDouble("buff_value");
        String buffDisplayName = rs.getString("buff_display_name");
        long buffDurationMillis = rs.getLong("buff_duration_millis");

        BuffDefinition buff = new BuffDefinition(buffCategory, buffValue, buffDisplayName);
        Map<Material, Integer> materials = deserializeMaterials(rs.getString("materials"));

        return new ProjectDefinition(
            id,
            name,
            description,
            requiredLevel,
            buffType,
            buff,
            List.of(), // Quests not stored in pool (regenerated on demand)
            materials,
            buffDurationMillis
        );
    }

    /**
     * Serializes a material map to JSON for storage.
     */
    private String serializeMaterials(Map<Material, Integer> materials) {
        if (materials == null || materials.isEmpty()) {
            return "{}";
        }

        Map<String, Integer> serializable = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : materials.entrySet()) {
            serializable.put(entry.getKey().name(), entry.getValue());
        }

        return gson.toJson(serializable);
    }

    /**
     * Deserializes a material map from JSON storage.
     */
    private Map<Material, Integer> deserializeMaterials(String json) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return new HashMap<>();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Integer> serialized = gson.fromJson(json, Map.class);
            Map<Material, Integer> materials = new HashMap<>();

            for (Map.Entry<String, Integer> entry : serialized.entrySet()) {
                try {
                    Material material = Material.valueOf(entry.getKey());
                    materials.put(material, entry.getValue().intValue());
                } catch (IllegalArgumentException e) {
                    // Skip invalid materials (might be from older versions)
                }
            }

            return materials;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize materials JSON: " + json, e);
        }
    }
}

package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.project.GuildProject;
import org.aincraft.project.ProjectStatus;
import org.aincraft.project.storage.GuildProjectRepository;
import org.bukkit.Material;

/**
 * JDBC-based implementation of GuildProjectRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildProjectRepository implements GuildProjectRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildProjectRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildProject project) {
        Objects.requireNonNull(project, "Project cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, project.getId());
                ps.setString(2, project.getGuildId());
                ps.setString(3, project.getProjectDefinitionId());
                ps.setString(4, project.getStatus().name());
                ps.setLong(5, project.getStartedAt());

                if (project.getCompletedAt() != null) {
                    ps.setLong(6, project.getCompletedAt());
                } else {
                    ps.setNull(6, Types.BIGINT);
                }

                ps.executeUpdate();
            }

            saveQuestProgress(conn, project);
            saveMaterialContributions(conn, project);

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild project", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                status = VALUES(status), completed_at = VALUES(completed_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status, completed_at = EXCLUDED.completed_at
                """;
            case H2 -> """
                MERGE INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        };
    }

    private void saveQuestProgress(Connection conn, GuildProject project) throws SQLException {
        // Delete existing quest progress
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM project_quest_progress WHERE project_id = ?")) {
            ps.setString(1, project.getId());
            ps.executeUpdate();
        }

        if (!project.getQuestProgress().isEmpty()) {
            String insertSql = "INSERT INTO project_quest_progress (project_id, quest_id, current_count) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Map.Entry<String, Long> entry : project.getQuestProgress().entrySet()) {
                    ps.setString(1, project.getId());
                    ps.setString(2, entry.getKey());
                    ps.setLong(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private void saveMaterialContributions(Connection conn, GuildProject project) throws SQLException {
        // Delete existing material contributions
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM project_material_contributions WHERE project_id = ?")) {
            ps.setString(1, project.getId());
            ps.executeUpdate();
        }

        if (!project.getMaterialContributed().isEmpty()) {
            String insertSql = "INSERT INTO project_material_contributions (project_id, material, amount) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Map.Entry<Material, Integer> entry : project.getMaterialContributed().entrySet()) {
                    ps.setString(1, project.getId());
                    ps.setString(2, entry.getKey().name());
                    ps.setInt(3, entry.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @Override
    public Optional<GuildProject> findById(String projectId) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_projects WHERE id = ?")) {
            ps.setString(1, projectId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToProject(conn, rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find project by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<GuildProject> findActiveByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT * FROM guild_projects WHERE guild_id = ? AND status = 'IN_PROGRESS' LIMIT 1";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToProject(conn, rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active project", e);
        }

        return Optional.empty();
    }

    @Override
    public List<GuildProject> findByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT * FROM guild_projects WHERE guild_id = ? ORDER BY started_at DESC";
        List<GuildProject> projects = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                projects.add(mapRowToProject(conn, rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find projects by guild", e);
        }

        return projects;
    }

    @Override
    public void delete(String projectId) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_projects WHERE id = ?")) {
            ps.setString(1, projectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete project", e);
        }
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_projects WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete projects by guild", e);
        }
    }

    @Override
    public void updateQuestProgress(String projectId, String questId, long newCount) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");
        Objects.requireNonNull(questId, "Quest ID cannot be null");

        String sql = getUpsertQuestProgressSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, questId);
            ps.setLong(3, newCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update quest progress", e);
        }
    }

    private String getUpsertQuestProgressSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT INTO project_quest_progress (project_id, quest_id, current_count)
                VALUES (?, ?, ?)
                ON CONFLICT(project_id, quest_id) DO UPDATE SET current_count = excluded.current_count
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO project_quest_progress (project_id, quest_id, current_count)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE current_count = VALUES(current_count)
                """;
            case POSTGRESQL -> """
                INSERT INTO project_quest_progress (project_id, quest_id, current_count)
                VALUES (?, ?, ?)
                ON CONFLICT (project_id, quest_id) DO UPDATE SET current_count = EXCLUDED.current_count
                """;
            case H2 -> """
                MERGE INTO project_quest_progress (project_id, quest_id, current_count)
                KEY (project_id, quest_id) VALUES (?, ?, ?)
                """;
        };
    }

    @Override
    public void updateMaterialContribution(String projectId, Material material, int newAmount) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");
        Objects.requireNonNull(material, "Material cannot be null");

        String sql = getUpsertMaterialContributionSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ps.setString(2, material.name());
            ps.setInt(3, newAmount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update material contribution", e);
        }
    }

    private String getUpsertMaterialContributionSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT INTO project_material_contributions (project_id, material, amount)
                VALUES (?, ?, ?)
                ON CONFLICT(project_id, material) DO UPDATE SET amount = excluded.amount
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO project_material_contributions (project_id, material, amount)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE amount = VALUES(amount)
                """;
            case POSTGRESQL -> """
                INSERT INTO project_material_contributions (project_id, material, amount)
                VALUES (?, ?, ?)
                ON CONFLICT (project_id, material) DO UPDATE SET amount = EXCLUDED.amount
                """;
            case H2 -> """
                MERGE INTO project_material_contributions (project_id, material, amount)
                KEY (project_id, material) VALUES (?, ?, ?)
                """;
        };
    }

    @Override
    public void updateStatus(String projectId, ProjectStatus status, Long completedAt) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");

        String sql = "UPDATE guild_projects SET status = ?, completed_at = ? WHERE id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            if (completedAt != null) {
                ps.setLong(2, completedAt);
            } else {
                ps.setNull(2, Types.BIGINT);
            }
            ps.setString(3, projectId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update project status", e);
        }
    }

    @Override
    public int getPoolSeed(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT seed FROM guild_project_pool_seed WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("seed");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get pool seed", e);
        }

        return 0;
    }

    @Override
    public void incrementPoolSeed(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = getIncrementPoolSeedSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to increment pool seed", e);
        }
    }

    private String getIncrementPoolSeedSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT INTO guild_project_pool_seed (guild_id, seed)
                VALUES (?, 1)
                ON CONFLICT(guild_id) DO UPDATE SET seed = seed + 1
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_project_pool_seed (guild_id, seed)
                VALUES (?, 1)
                ON DUPLICATE KEY UPDATE seed = seed + 1
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_project_pool_seed (guild_id, seed)
                VALUES (?, 1)
                ON CONFLICT (guild_id) DO UPDATE SET seed = guild_project_pool_seed.seed + 1
                """;
            case H2 -> """
                MERGE INTO guild_project_pool_seed AS t
                USING (VALUES (?)) AS s(guild_id)
                ON t.guild_id = s.guild_id
                WHEN MATCHED THEN UPDATE SET seed = t.seed + 1
                WHEN NOT MATCHED THEN INSERT (guild_id, seed) VALUES (s.guild_id, 1)
                """;
        };
    }

    private GuildProject mapRowToProject(Connection conn, ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String guildId = rs.getString("guild_id");
        String projectDefinitionId = rs.getString("project_definition_id");
        ProjectStatus status = ProjectStatus.valueOf(rs.getString("status"));
        long startedAt = rs.getLong("started_at");
        long completedAtRaw = rs.getLong("completed_at");
        Long completedAt = rs.wasNull() ? null : completedAtRaw;

        Map<String, Long> questProgress = loadQuestProgress(conn, id);
        Map<Material, Integer> materialContributed = loadMaterialContributions(conn, id);

        return new GuildProject(id, guildId, projectDefinitionId, status,
                questProgress, materialContributed, startedAt, completedAt);
    }

    private Map<String, Long> loadQuestProgress(Connection conn, String projectId) throws SQLException {
        Map<String, Long> progress = new HashMap<>();
        String sql = "SELECT quest_id, current_count FROM project_quest_progress WHERE project_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                progress.put(rs.getString("quest_id"), rs.getLong("current_count"));
            }
        }

        return progress;
    }

    private Map<Material, Integer> loadMaterialContributions(Connection conn, String projectId) throws SQLException {
        Map<Material, Integer> contributions = new HashMap<>();
        String sql = "SELECT material, amount FROM project_material_contributions WHERE project_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try {
                    Material material = Material.valueOf(rs.getString("material"));
                    contributions.put(material, rs.getInt("amount"));
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid materials
                }
            }
        }

        return contributions;
    }
}

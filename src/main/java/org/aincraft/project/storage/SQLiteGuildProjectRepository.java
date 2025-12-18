package org.aincraft.project.storage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.aincraft.project.GuildProject;
import org.aincraft.project.ProjectStatus;
import org.bukkit.Material;

import java.sql.*;
import java.util.*;

public class SQLiteGuildProjectRepository implements GuildProjectRepository {

    private final String connectionString;

    @Inject
    public SQLiteGuildProjectRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createProjectsTableSQL = """
            CREATE TABLE IF NOT EXISTS guild_projects (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL,
                project_definition_id TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
                started_at INTEGER NOT NULL,
                completed_at INTEGER,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
            """;

        String createQuestProgressTableSQL = """
            CREATE TABLE IF NOT EXISTS project_quest_progress (
                project_id TEXT NOT NULL,
                quest_id TEXT NOT NULL,
                current_count INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (project_id, quest_id),
                FOREIGN KEY (project_id) REFERENCES guild_projects(id) ON DELETE CASCADE
            );
            """;

        String createMaterialContributionsTableSQL = """
            CREATE TABLE IF NOT EXISTS project_material_contributions (
                project_id TEXT NOT NULL,
                material TEXT NOT NULL,
                amount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (project_id, material),
                FOREIGN KEY (project_id) REFERENCES guild_projects(id) ON DELETE CASCADE
            );
            """;

        String createPoolSeedTableSQL = """
            CREATE TABLE IF NOT EXISTS guild_project_pool_seed (
                guild_id TEXT PRIMARY KEY,
                seed INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
            );
            """;

        String createIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_projects_guild ON guild_projects(guild_id);
            CREATE INDEX IF NOT EXISTS idx_projects_status ON guild_projects(guild_id, status);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {

            stmt.execute(createProjectsTableSQL);
            stmt.execute(createQuestProgressTableSQL);
            stmt.execute(createMaterialContributionsTableSQL);
            stmt.execute(createPoolSeedTableSQL);

            for (String sql : createIndexSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize project database", e);
        }
    }

    @Override
    public void save(GuildProject project) {
        Objects.requireNonNull(project, "Project cannot be null");

        String sql = """
            INSERT OR REPLACE INTO guild_projects
            (id, guild_id, project_definition_id, status, started_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, project.getId());
                pstmt.setString(2, project.getGuildId());
                pstmt.setString(3, project.getProjectDefinitionId());
                pstmt.setString(4, project.getStatus().name());
                pstmt.setLong(5, project.getStartedAt());

                if (project.getCompletedAt() != null) {
                    pstmt.setLong(6, project.getCompletedAt());
                } else {
                    pstmt.setNull(6, Types.BIGINT);
                }

                pstmt.executeUpdate();
            }

            saveQuestProgress(conn, project);
            saveMaterialContributions(conn, project);

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild project", e);
        }
    }

    private void saveQuestProgress(Connection conn, GuildProject project) throws SQLException {
        String deleteSQL = "DELETE FROM project_quest_progress WHERE project_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, project.getId());
            pstmt.executeUpdate();
        }

        if (!project.getQuestProgress().isEmpty()) {
            String insertSQL = "INSERT INTO project_quest_progress (project_id, quest_id, current_count) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                for (Map.Entry<String, Long> entry : project.getQuestProgress().entrySet()) {
                    pstmt.setString(1, project.getId());
                    pstmt.setString(2, entry.getKey());
                    pstmt.setLong(3, entry.getValue());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    private void saveMaterialContributions(Connection conn, GuildProject project) throws SQLException {
        String deleteSQL = "DELETE FROM project_material_contributions WHERE project_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, project.getId());
            pstmt.executeUpdate();
        }

        if (!project.getMaterialContributed().isEmpty()) {
            String insertSQL = "INSERT INTO project_material_contributions (project_id, material, amount) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                for (Map.Entry<Material, Integer> entry : project.getMaterialContributed().entrySet()) {
                    pstmt.setString(1, project.getId());
                    pstmt.setString(2, entry.getKey().name());
                    pstmt.setInt(3, entry.getValue());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    @Override
    public Optional<GuildProject> findById(String projectId) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");

        String sql = "SELECT * FROM guild_projects WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, projectId);
            ResultSet rs = pstmt.executeQuery();

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

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        String sql = "DELETE FROM guild_projects WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, projectId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete project", e);
        }
    }

    @Override
    public void deleteByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "DELETE FROM guild_projects WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete projects by guild", e);
        }
    }

    @Override
    public void updateQuestProgress(String projectId, String questId, long newCount) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");
        Objects.requireNonNull(questId, "Quest ID cannot be null");

        String sql = """
            INSERT INTO project_quest_progress (project_id, quest_id, current_count)
            VALUES (?, ?, ?)
            ON CONFLICT(project_id, quest_id) DO UPDATE SET current_count = excluded.current_count
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, projectId);
            pstmt.setString(2, questId);
            pstmt.setLong(3, newCount);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update quest progress", e);
        }
    }

    /**
     * @deprecated Material contributions are no longer tracked. This method is a no-op.
     */
    @Deprecated
    @Override
    public void updateMaterialContribution(String projectId, Material material, int newAmount) {
        // No-op - material contributions are no longer tracked
        // Materials are taken from vault atomically when project is completed
    }

    @Override
    public void updateStatus(String projectId, ProjectStatus status, Long completedAt) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");

        String sql = "UPDATE guild_projects SET status = ?, completed_at = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status.name());
            if (completedAt != null) {
                pstmt.setLong(2, completedAt);
            } else {
                pstmt.setNull(2, Types.BIGINT);
            }
            pstmt.setString(3, projectId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update project status", e);
        }
    }

    @Override
    public int getPoolSeed(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT seed FROM guild_project_pool_seed WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        long currentTime = System.currentTimeMillis();
        String sql = """
            INSERT INTO guild_project_pool_seed (guild_id, seed, last_refresh_time)
            VALUES (?, 1, ?)
            ON CONFLICT(guild_id) DO UPDATE SET seed = seed + 1, last_refresh_time = excluded.last_refresh_time
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            pstmt.setLong(2, currentTime);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to increment pool seed", e);
        }
    }

    @Override
    public Long getLastRefreshTime(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT last_refresh_time FROM guild_project_pool_seed WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                long value = rs.getLong("last_refresh_time");
                return rs.wasNull() ? null : value;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get last refresh time", e);
        }

        return null;
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

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                progress.put(rs.getString("quest_id"), rs.getLong("current_count"));
            }
        }

        return progress;
    }

    private Map<Material, Integer> loadMaterialContributions(Connection conn, String projectId) throws SQLException {
        Map<Material, Integer> contributions = new HashMap<>();
        String sql = "SELECT material, amount FROM project_material_contributions WHERE project_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectId);
            ResultSet rs = pstmt.executeQuery();

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

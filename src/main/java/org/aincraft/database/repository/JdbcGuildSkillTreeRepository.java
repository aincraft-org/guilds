package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.skilltree.GuildSkillTree;
import org.aincraft.skilltree.storage.GuildSkillTreeRepository;

/**
 * JDBC-based implementation of GuildSkillTreeRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcGuildSkillTreeRepository implements GuildSkillTreeRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildSkillTreeRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildSkillTree skillTree) {
        Objects.requireNonNull(skillTree, "Skill tree cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skillTree.getGuildId());
            ps.setInt(2, skillTree.getAvailableSkillPoints());
            ps.setInt(3, skillTree.getTotalSkillPointsEarned());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild skill tree", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_skill_tree (guild_id, available_sp, total_sp_earned)
                VALUES (?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_skill_tree (guild_id, available_sp, total_sp_earned)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                available_sp = VALUES(available_sp), total_sp_earned = VALUES(total_sp_earned)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_skill_tree (guild_id, available_sp, total_sp_earned)
                VALUES (?, ?, ?)
                ON CONFLICT (guild_id) DO UPDATE SET
                available_sp = EXCLUDED.available_sp, total_sp_earned = EXCLUDED.total_sp_earned
                """;
            case H2 -> """
                MERGE INTO guild_skill_tree (guild_id, available_sp, total_sp_earned)
                KEY (guild_id) VALUES (?, ?, ?)
                """;
        };
    }

    @Override
    public Optional<GuildSkillTree> findByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_skill_tree WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                int availableSp = rs.getInt("available_sp");
                int totalSpEarned = rs.getInt("total_sp_earned");
                Set<String> unlockedSkills = getUnlockedSkills(guildId);

                return Optional.of(new GuildSkillTree(guildId, availableSp, totalSpEarned, unlockedSkills));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find guild skill tree", e);
        }

        return Optional.empty();
    }

    @Override
    public void delete(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        // Delete unlocked skills first (foreign key constraint)
        deleteAllSkills(guildId);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_skill_tree WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild skill tree", e);
        }
    }

    @Override
    public void unlockSkill(String guildId, String skillId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(skillId, "Skill ID cannot be null");

        String sql = getInsertUnlockedSkillSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, skillId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unlock skill", e);
        }
    }

    private String getInsertUnlockedSkillSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR IGNORE INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                VALUES (?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT IGNORE INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                VALUES (?, ?, ?)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                VALUES (?, ?, ?)
                ON CONFLICT (guild_id, skill_id) DO NOTHING
                """;
            case H2 -> """
                MERGE INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                KEY (guild_id, skill_id) VALUES (?, ?, ?)
                """;
        };
    }

    @Override
    public Set<String> getUnlockedSkills(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = """
            SELECT skill_id
            FROM guild_unlocked_skills
            WHERE guild_id = ?
            """;

        Set<String> skills = new HashSet<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                skills.add(rs.getString("skill_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get unlocked skills", e);
        }

        return skills;
    }

    @Override
    public void deleteAllSkills(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_unlocked_skills WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete unlocked skills", e);
        }
    }
}

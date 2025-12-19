package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.Sql;
import org.aincraft.skilltree.GuildSkillTree;
import org.aincraft.skilltree.storage.GuildSkillTreeRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC-based implementation of GuildSkillTreeRepository.
 * Works with all supported database types (SQLite, MySQL, PostgreSQL, H2, MariaDB).
 * Single Responsibility: Skill tree data access via JDBC.
 */
@Singleton
public class JdbcGuildSkillTreeRepository implements GuildSkillTreeRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcGuildSkillTreeRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "Connection provider cannot be null");
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(GuildSkillTree tree) {
        Objects.requireNonNull(tree, "Guild skill tree cannot be null");

        String sql = Sql.upsertGuildSkillTree(dbType);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tree.getGuildId().toString());
            ps.setInt(2, tree.getAvailableSp());
            ps.setInt(3, tree.getTotalSpEarned());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save guild skill tree for guild: " + tree.getGuildId(), e);
        }
    }

    @Override
    public Optional<GuildSkillTree> findByGuildId(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT available_sp, total_sp_earned FROM guild_skill_trees WHERE guild_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int availableSp = rs.getInt("available_sp");
                    int totalSpEarned = rs.getInt("total_sp_earned");

                    // Load unlocked skills
                    Set<String> unlockedSkills = getUnlockedSkills(guildId);

                    GuildSkillTree tree = new GuildSkillTree(guildId, availableSp, totalSpEarned, unlockedSkills);
                    return Optional.of(tree);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve guild skill tree for guild: " + guildId, e);
        }

        return Optional.empty();
    }

    @Override
    public void delete(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        // Clear unlocked skills first (cascade would handle this, but being explicit)
        clearUnlockedSkills(guildId);

        String sql = "DELETE FROM guild_skill_trees WHERE guild_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete guild skill tree for guild: " + guildId, e);
        }
    }

    @Override
    public void unlockSkill(UUID guildId, String skillId, long unlockedAt) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(skillId, "Skill ID cannot be null");

        String sql = switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                VALUES (?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE unlocked_at = VALUES(unlocked_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                VALUES (?, ?, ?)
                ON CONFLICT (guild_id, skill_id) DO UPDATE SET unlocked_at = EXCLUDED.unlocked_at
                """;
            case H2 -> """
                MERGE INTO guild_unlocked_skills (guild_id, skill_id, unlocked_at)
                KEY (guild_id, skill_id) VALUES (?, ?, ?)
                """;
        };

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.setString(2, skillId);
            ps.setLong(3, unlockedAt);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record unlocked skill " + skillId + " for guild " + guildId, e);
        }
    }

    @Override
    public void clearUnlockedSkills(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "DELETE FROM guild_unlocked_skills WHERE guild_id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear unlocked skills for guild: " + guildId, e);
        }
    }

    @Override
    public Set<String> getUnlockedSkills(UUID guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String sql = "SELECT skill_id FROM guild_unlocked_skills WHERE guild_id = ? ORDER BY unlocked_at ASC";
        Set<String> unlockedSkills = new HashSet<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, guildId.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    unlockedSkills.add(rs.getString("skill_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve unlocked skills for guild: " + guildId, e);
        }

        return Collections.unmodifiableSet(unlockedSkills);
    }
}

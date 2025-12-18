package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.database.Sql;
import org.aincraft.project.BuffType;
import org.aincraft.project.llm.LLMProjectTextRepository;
import org.aincraft.project.llm.ProjectText;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of LLMProjectTextRepository.
 * Supports all database types (SQLite, MySQL, PostgreSQL, H2).
 */
@Singleton
public class JdbcLLMProjectTextRepository implements LLMProjectTextRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcLLMProjectTextRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public Optional<ProjectText> getRandomCachedText(BuffType buffType) {
        Objects.requireNonNull(buffType, "BuffType cannot be null");

        String sql = Sql.getRandomGeneratedProjectText(dbType);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buffType.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String name = rs.getString("project_name");
                String description = rs.getString("description");
                return Optional.of(new ProjectText(name, description));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve random cached text for " + buffType, e);
        }

        return Optional.empty();
    }

    @Override
    public int getCacheSize(BuffType buffType) {
        Objects.requireNonNull(buffType, "BuffType cannot be null");

        String sql = Sql.countGeneratedProjectTexts(dbType);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buffType.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count cached texts for " + buffType, e);
        }

        return 0;
    }

    @Override
    public void saveAll(BuffType buffType, List<ProjectText> texts) {
        Objects.requireNonNull(buffType, "BuffType cannot be null");
        Objects.requireNonNull(texts, "Texts cannot be null");

        if (texts.isEmpty()) {
            return;
        }

        String sql = Sql.insertGeneratedProjectText(dbType);
        long createdAt = System.currentTimeMillis();

        try (Connection conn = connectionProvider.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (ProjectText text : texts) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, buffType.toString());
                    ps.setString(3, text.name());
                    ps.setString(4, text.description());
                    ps.setLong(5, createdAt);
                    ps.addBatch();
                }

                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save project texts for " + buffType, e);
        }
    }

    @Override
    public void deleteOldEntries(BuffType buffType, int keepCount) {
        Objects.requireNonNull(buffType, "BuffType cannot be null");
        if (keepCount < 0) {
            throw new IllegalArgumentException("Keep count must be non-negative");
        }

        String sql = Sql.deleteOldGeneratedProjectTexts(dbType);

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, buffType.toString());
            ps.setString(2, buffType.toString());
            ps.setInt(3, keepCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete old entries for " + buffType, e);
        }
    }
}

package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.territory.persist.SqlSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** Adds the single active guild-project slot and starts new guilds with 1 skill point. */
public class AddGuildProjectsMigration implements DatabaseMigration {
    private static final int VERSION = 22;

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return "Add active guild project slot and default project skill points";
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        SqlSupport.addColumnIfAbsent(connection, "guilds", "active_project_id",
                SqlSupport.stringType(SqlSupport.mysql(connection)));
        SqlSupport.setColumnDefault(connection, "guilds", "tech_points", "1");
    }

    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        String sql = "SELECT 1 FROM schema_migrations WHERE version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, VERSION);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public void markAsApplied(Connection connection) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, VERSION);
            statement.setString(2, getDescription());
            statement.setString(3, LocalDateTime.now(java.time.ZoneOffset.UTC).toString());
            statement.executeUpdate();
        }
    }
}

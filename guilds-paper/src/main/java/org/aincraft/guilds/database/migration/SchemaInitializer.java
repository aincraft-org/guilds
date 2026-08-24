package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.territory.persist.DatabaseType;
import org.aincraft.guilds.territory.persist.SqlMigrationHook;
import org.aincraft.guilds.territory.persist.SqlMigrationRunner;
import org.aincraft.guilds.territory.persist.SqlStatements;
import org.aincraft.guilds.territory.persist.SqlSupport;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Applies versioned Guilds SQL from {@code /sql/migrations/guilds}.
 */
public class SchemaInitializer {

    private final JavaPlugin plugin;
    private final SqlMigrationRunner runner = new SqlMigrationRunner();

    public SchemaInitializer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize(Connection connection) {
        try {
            DatabaseType type = SqlSupport.mysql(connection) ? DatabaseType.MYSQL : DatabaseType.POSTGRESQL;
            plugin.getLogger().info("Applying Guilds SQL migrations for " + type);
            runner.apply(connection, "guilds", type, hooks());
            plugin.getLogger().info("Database schema initialization completed. Current version: "
                    + SqlMigrationRunner.currentVersion(connection, "guilds"));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database schema: " + e.getMessage(), e);
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    private static Map<Integer, SqlMigrationHook> hooks() {
        Map<Integer, SqlMigrationHook> hooks = new LinkedHashMap<>();
        hooks.put(7, AddPlotTypeSystemMigration::seedDefaults);
        hooks.put(16, new AddGuildRenameMigration()::migrate);
        hooks.put(18, new AddAllianceRenameMigration()::migrate);
        hooks.put(20, new AlterResidentLastOnlineMigration()::migrate);
        return Map.copyOf(hooks);
    }

    public boolean isMigrationApplied(Connection connection, int version) throws SQLException {
        String sql = SqlStatements.load("migrations/select-schema-version.sql");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    public List<MigrationInfo> getAppliedMigrations(Connection connection) throws SQLException {
        List<MigrationInfo> appliedMigrations = new ArrayList<>();
        String sql = SqlStatements.load("migrations/select-applied-schema-migrations.sql");
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                appliedMigrations.add(new MigrationInfo(
                        resultSet.getInt("version"),
                        resultSet.getString("description"),
                        resultSet.getString("applied_at"),
                        resultSet.getString("checksum")));
            }
        }
        return appliedMigrations;
    }

    public static class MigrationInfo {
        private final int version;
        private final String description;
        private final String appliedAt;
        private final String checksum;

        public MigrationInfo(int version, String description, String appliedAt, String checksum) {
            this.version = version;
            this.description = description;
            this.appliedAt = appliedAt;
            this.checksum = checksum;
        }

        public int getVersion() { return version; }
        public String getDescription() { return description; }
        public String getAppliedAt() { return appliedAt; }
        public String getChecksum() { return checksum; }
    }
}

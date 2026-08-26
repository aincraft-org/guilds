package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Migration to add plot type system with extensible registry
 * Creates table for storing plot type definitions and extends guild_blocks
 */
public class AddPlotTypeSystemMigration implements DatabaseMigration {

    @Override
    public int getVersion() {
        return 7;
    }

    @Override
    public String getDescription() {
        return "Add extensible plot type system with registry support";
    }

    @Override
    public void migrate(Connection connection) throws java.sql.SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS plot_type_definitions (
                    type_name TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    description TEXT,
                    plugin_name TEXT,
                    metadata TEXT,
                    permissions TEXT,
                    is_enabled BOOLEAN DEFAULT TRUE,
                    created_at TEXT NOT NULL
                )
            """);
            statement.execute("""
                ALTER TABLE guild_blocks ADD COLUMN IF NOT EXISTS plot_type_definition TEXT DEFAULT NULL
            """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_plot_type_definitions_plugin "
                    + "ON plot_type_definitions(plugin_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_plot_type_definitions_enabled "
                    + "ON plot_type_definitions(is_enabled)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_guild_blocks_plot_type_def "
                    + "ON guild_blocks(plot_type_definition)");
        }

        String currentTime = java.time.LocalDateTime.now().toString();
        seed(connection, "default", "Default", "Standard residential plot", "{}", "[]", currentTime);
        seed(connection, "shop", "Shop", "Commercial plot for shops and markets",
                "{\"shop_type\": \"commercial\"}", "[]", currentTime);
        seed(connection, "farm", "Farm", "Agricultural plot for farming",
                "{\"crop_growth_bonus\": 1.2}", "[]", currentTime);
        seed(connection, "wilderness", "Wilderness", "Unclaimed territory",
                "{\"unclaimable\": true}", "[]", currentTime);
        seed(connection, "bank", "Bank", "Financial services plot",
                "{\"bank_services\": [\"deposit\", \"withdraw\", \"exchange\"]}",
                "[\"guilds.admin.bank\"]", currentTime);
        seed(connection, "inn", "Inn", "Hospitality and accommodation plot",
                "{\"bed_healing\": true}", "[]", currentTime);
        seed(connection, "embassy", "Embassy", "Diplomatic representation plot",
                "{\"diplomatic_immunity\": true}", "[\"guilds.admin.embassy\"]", currentTime);
        seed(connection, "jail", "Jail", "Law enforcement and detention plot",
                "{\"prison_effect\": true}", "[\"guilds.admin.jail\"]", currentTime);
        seed(connection, "arena", "Arena", "Combat and entertainment plot",
                "{\"pvp_enabled\": true}", "[]", currentTime);
    }

    private static void seed(Connection connection, String typeName, String displayName, String description,
                             String metadata, String permissions, String createdAt) throws java.sql.SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (?, ?, ?, NULL, ?, ?, TRUE, ?)
                ON CONFLICT (type_name) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    description = EXCLUDED.description,
                    metadata = EXCLUDED.metadata,
                    permissions = EXCLUDED.permissions,
                    is_enabled = EXCLUDED.is_enabled,
                    created_at = EXCLUDED.created_at
                """)) {
            statement.setString(1, typeName);
            statement.setString(2, displayName);
            statement.setString(3, description);
            statement.setString(4, metadata);
            statement.setString(5, permissions);
            statement.setString(6, createdAt);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean isApplied(Connection connection) throws java.sql.SQLException {
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables "
                             + "WHERE table_schema = current_schema() AND table_name = 'plot_type_definitions'")) {
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (java.sql.SQLException e) {
            return false;
        }
        return false;
    }

    @Override
    public void markAsApplied(Connection connection) throws java.sql.SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";

        try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, getVersion());
            statement.setString(2, getDescription());
            statement.setString(3, java.time.LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }
}
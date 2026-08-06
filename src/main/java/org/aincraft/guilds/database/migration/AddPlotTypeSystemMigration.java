package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Migration to add plot type system with extensible registry
 * Creates table for storing plot type definitions and extends town_blocks
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
            // Create plot type definitions table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS plot_type_definitions (
                    type_name TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    description TEXT,
                    plugin_name TEXT,
                    metadata TEXT,
                    permissions TEXT,
                    is_enabled BOOLEAN DEFAULT 1,
                    created_at TEXT NOT NULL
                )
            """);

            // Extend town_blocks table with optional plot_type_definition reference
            statement.execute("""
                ALTER TABLE town_blocks ADD COLUMN plot_type_definition TEXT DEFAULT NULL
            """);

            // Insert built-in plot type definitions
            String currentTime = java.time.LocalDateTime.now().toString();

            // Default plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'default', 'Default', 'Standard residential plot', NULL, '{}',
                    '[]', 1, '%s'
                )
            """, currentTime));

            // Shop plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'shop', 'Shop', 'Commercial plot for shops and markets', NULL,
                    '{"shop_type": "commercial"}', '[]', 1, '%s'
                )
            """, currentTime));

            // Farm plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'farm', 'Farm', 'Agricultural plot for farming', NULL,
                    '{"crop_growth_bonus": 1.2}', '[]', 1, '%s'
                )
            """, currentTime));

            // Wilderness plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'wilderness', 'Wilderness', 'Unclaimed territory', NULL,
                    '{"unclaimable": true}', '[]', 1, '%s'
                )
            """, currentTime));

            // Bank plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'bank', 'Bank', 'Financial services plot', NULL,
                    '{"bank_services": ["deposit", "withdraw", "exchange"]}',
                    '["guilds.admin.bank"]', 1, '%s'
                )
            """, currentTime));

            // Inn plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'inn', 'Inn', 'Hospitality and accommodation plot', NULL,
                    '{"bed_healing": true}', '[]', 1, '%s'
                )
            """, currentTime));

            // Embassy plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'embassy', 'Embassy', 'Diplomatic representation plot', NULL,
                    '{"diplomatic_immunity": true}', '["guilds.admin.embassy"]', 1, '%s'
                )
            """, currentTime));

            // Jail plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'jail', 'Jail', 'Law enforcement and detention plot', NULL,
                    '{"prison_effect": true}', '["guilds.admin.jail"]', 1, '%s'
                )
            """, currentTime));

            // Arena plot type
            statement.execute(String.format("""
                INSERT OR REPLACE INTO plot_type_definitions (
                    type_name, display_name, description, plugin_name, metadata,
                    permissions, is_enabled, created_at
                ) VALUES (
                    'arena', 'Arena', 'Combat and entertainment plot', NULL,
                    '{"pvp_enabled": true}', '[]', 1, '%s'
                )
            """, currentTime));

            // Create indexes for performance
            statement.execute("CREATE INDEX IF NOT EXISTS idx_plot_type_definitions_plugin ON plot_type_definitions(plugin_name)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_plot_type_definitions_enabled ON plot_type_definitions(is_enabled)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_town_blocks_plot_type_def ON town_blocks(plot_type_definition)");
        }
    }

    @Override
    public boolean isApplied(Connection connection) throws java.sql.SQLException {
        try (Statement statement = connection.createStatement()) {
            // Check if plot_type_definitions table exists
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='plot_type_definitions'")) {

                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        } catch (java.sql.SQLException e) {
            // Table doesn't exist yet
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
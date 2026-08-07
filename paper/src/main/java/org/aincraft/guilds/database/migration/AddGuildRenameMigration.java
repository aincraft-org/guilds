package org.aincraft.guilds.database.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Renames the legacy {@code town*} schema objects to {@code guild*} naming.
 * <p>
 * Version 16 — after AddGovernanceFormMigration (v15).
 * <p>
 * The guilds subsystem models towns as guilds; schema objects kept the old
 * "town" vocabulary. Fresh installs now create {@code guild*} names directly
 * (migrations v1–v15 use the new names), so this migration no-ops on them.
 * Existing databases (created before v16) get their tables, columns, and
 * indexes renamed in place, preserving all rows.
 * <p>
 * Idempotency and safety:
 * <ul>
 *   <li>Each rename is guarded by existence checks — missing legacy objects
 *       are skipped (older servers may lack some subsystems).</li>
 *   <li>If both the old and the new name exist, the migration fails loudly
 *       instead of guessing (that state means a partial rename already ran
 *       and data may be split).</li>
 *   <li>The whole sequence runs in a transaction; any failure rolls back so
 *       no half-renamed schema is left behind.</li>
 * </ul>
 * Legacy foreign-key declarations are intentionally left untouched; this
 * migration renames the active tables and columns only.
 */
public class AddGuildRenameMigration implements DatabaseMigration {

    private static final int VERSION = 16;
    private static final String DESCRIPTION = "Rename legacy town schema objects to guild naming";

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void migrate(Connection connection) throws SQLException {
        boolean wasAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            renameTable(connection, "towns", "guilds");
            renameTable(connection, "town_residents", "guild_residents");
            renameTable(connection, "town_blocks", "guild_blocks");
            renameTable(connection, "town_levels", "guild_levels");
            renameTable(connection, "town_resources", "guild_resources");
            renameTable(connection, "town_level_benefits", "guild_level_benefits");
            renameTable(connection, "town_specializations", "guild_specializations");
            renameTable(connection, "town_quests", "guild_quests");
            renameTable(connection, "town_unlocked_nodes", "guild_unlocked_nodes");

            // Columns (tables already renamed where applicable)
            renameColumn(connection, "residents", "town_name", "guild_name");
            renameColumn(connection, "guilds", "town_level", "guild_level");
            renameColumn(connection, "guild_residents", "town_id", "guild_id");
            renameColumn(connection, "guild_blocks", "town_id", "guild_id");
            renameColumn(connection, "guild_resources", "town_id", "guild_id");
            renameColumn(connection, "resource_contributions", "town_id", "guild_id");
            renameColumn(connection, "guild_level_benefits", "town_id", "guild_id");
            renameColumn(connection, "guild_specializations", "town_id", "guild_id");
            renameColumn(connection, "guild_quests", "town_id", "guild_id");
            renameColumn(connection, "guild_unlocked_nodes", "town_id", "guild_id");
            renameColumn(connection, "nations", "capital_town_id", "capital_guild_id");
            renameColumn(connection, "nation_members", "town_id", "guild_id");
            renameColumn(connection, "blueprints", "town_id", "guild_id");
            renameColumn(connection, "broadcast_messages", "town_id", "guild_id");
            renameColumn(connection, "economy_transactions", "town_id", "guild_id");

            // Renaming a table does not rename its existing indexes, so the old
            // names must be dropped explicitly and recreated under new names.
            recreateIndex(connection, "idx_residents_town", "idx_residents_guild", "residents", "guild_name");
            recreateIndex(connection, "idx_towns_name", "idx_guilds_name", "guilds", "name");
            recreateIndex(connection, "idx_town_blocks_location", "idx_guild_blocks_location", "guild_blocks", "x, z, world");
            recreateIndex(connection, "idx_town_blocks_town", "idx_guild_blocks_guild", "guild_blocks", "guild_id");
            recreateIndex(connection, "idx_town_blocks_owner", "idx_guild_blocks_owner", "guild_blocks", "owner_uuid");
            recreateIndex(connection, "idx_town_blocks_plot_type_def", "idx_guild_blocks_plot_type_def", "guild_blocks", "plot_type_definition");
            recreateIndex(connection, "idx_town_resources_town", "idx_guild_resources_guild", "guild_resources", "guild_id");
            recreateIndex(connection, "idx_town_resources_type", "idx_guild_resources_type", "guild_resources", "resource_type");
            recreateIndex(connection, "idx_resource_contributions_town", "idx_resource_contributions_guild", "resource_contributions", "guild_id");
            recreateIndex(connection, "idx_town_level_benefits_town", "idx_guild_level_benefits_guild", "guild_level_benefits", "guild_id");
            recreateIndex(connection, "idx_town_level_benefits_level", "idx_guild_level_benefits_level", "guild_level_benefits", "level");
            recreateIndex(connection, "idx_town_quests_town_id", "idx_guild_quests_guild_id", "guild_quests", "guild_id");
            recreateIndex(connection, "idx_town_specialization", "idx_guild_specialization", "guild_specializations", "specialization");
            recreateIndex(connection, "idx_town_unlocked_town", "idx_guild_unlocked_guild", "guild_unlocked_nodes", "guild_id");
            recreateIndex(connection, "idx_town_unlocked_node", "idx_guild_unlocked_node", "guild_unlocked_nodes", "node_id");
            recreateIndex(connection, "idx_broadcast_messages_town", "idx_broadcast_messages_guild", "broadcast_messages", "guild_id");
            recreateIndex(connection, "idx_economy_tx_town", "idx_economy_tx_guild", "economy_transactions", "guild_id");
            recreateIndex(connection, "idx_nation_members_town", "idx_nation_members_guild", "nation_members", "guild_id");
            recreateIndex(connection, "idx_blueprints_town", "idx_blueprints_guild", "blueprints", "guild_id");
            // Same index name, but the column it covers was renamed
            recreateIndex(connection, "idx_nations_capital", "idx_nations_capital", "nations", "capital_guild_id");

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(wasAutoCommit);
        }
    }

    private static void renameTable(Connection connection, String oldName, String newName) throws SQLException {
        boolean oldExists = tableExists(connection, oldName);
        boolean newExists = tableExists(connection, newName);
        if (oldExists && newExists) {
            throw new SQLException("Ambiguous schema state: both '" + oldName + "' and '" + newName
                    + "' exist — refusing to rename (data may be split).");
        }
        if (oldExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + oldName + " RENAME TO " + newName);
            }
        }
    }

    private static void renameColumn(Connection connection, String table, String oldColumn, String newColumn)
            throws SQLException {
        if (!tableExists(connection, table)) {
            return; // older servers may lack this subsystem table entirely
        }
        boolean oldExists = columnExists(connection, table, oldColumn);
        boolean newExists = columnExists(connection, table, newColumn);
        if (oldExists && newExists) {
            throw new SQLException("Ambiguous schema state: columns '" + table + "." + oldColumn + "' and '"
                    + table + "." + newColumn + "' both exist — refusing to rename.");
        }
        if (oldExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " RENAME COLUMN " + oldColumn + " TO " + newColumn);
            }
        }
    }

    private static void recreateIndex(Connection connection, String oldName, String newName, String table, String columns)
            throws SQLException {
        if (!tableExists(connection, table)) {
            return; // older servers may lack this subsystem table entirely
        }
        if (indexExists(connection, oldName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP INDEX " + oldName);
            }
        }
        if (!indexExists(connection, newName)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE INDEX " + newName + " ON " + table + " (" + columns + ")");
            }
        }
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean indexExists(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean isApplied(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM schema_migrations WHERE version = " + VERSION)) {
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            // Table doesn't exist yet, so migration is not applied
            return false;
        }
        return false;
    }

    @Override
    public void markAsApplied(Connection connection) throws SQLException {
        String sql = "INSERT INTO schema_migrations (version, description, applied_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, getVersion());
            statement.setString(2, getDescription());
            statement.setString(3, java.time.LocalDateTime.now().toString());
            statement.executeUpdate();
        }
    }
}

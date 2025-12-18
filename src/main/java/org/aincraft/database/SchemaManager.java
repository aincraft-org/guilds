package org.aincraft.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database schema initialization.
 * Centralizes all DDL operations.
 */
@Singleton
public class SchemaManager {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;
    private final Logger logger;

    @Inject
    public SchemaManager(ConnectionProvider connectionProvider, @Named("guilds") Logger logger) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
        this.logger = logger;
    }

    /**
     * Initializes all database tables.
     */
    public void initializeSchema() throws SQLException {
        logger.info("Initializing database schema for " + dbType);

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {

            // Core tables
            executeStatement(stmt, Sql.createGuildsTable(dbType));
            executeStatement(stmt, Sql.createGuildMembersTable(dbType));
            executeStatement(stmt, Sql.createPlayerGuildsTable(dbType));
            executeStatement(stmt, Sql.createGuildRolesTable(dbType));
            executeStatement(stmt, Sql.createMemberRolesTable(dbType));

            // Chunk claims
            executeStatement(stmt, Sql.createChunkClaimsTable(dbType));
            executeStatement(stmt, Sql.createChunkClaimLogTable(dbType));

            // Relationships and permissions
            executeStatement(stmt, Sql.createGuildRelationshipsTable(dbType));
            executeStatement(stmt, Sql.createGuildDefaultPermissionsTable(dbType));
            executeStatement(stmt, Sql.createInvitesTable(dbType));

            // Subregions
            executeStatement(stmt, Sql.createSubregionsTable(dbType));
            executeStatement(stmt, Sql.createRegionPermissionsTable(dbType));
            executeStatement(stmt, Sql.createRegionRolesTable(dbType));
            executeStatement(stmt, Sql.createMemberRegionRolesTable(dbType));
            executeStatement(stmt, Sql.createRegionTypeLimitsTable(dbType));

            // Vault
            executeStatement(stmt, Sql.createVaultsTable(dbType));
            executeStatement(stmt, Sql.createVaultTransactionsTable(dbType));

            // Progression
            executeStatement(stmt, Sql.createGuildProgressionTable(dbType));
            executeStatement(stmt, Sql.createXpContributionsTable(dbType));
            executeStatement(stmt, Sql.createProgressionLogTable(dbType));

            // Projects
            executeStatement(stmt, Sql.createGuildProjectsTable(dbType));
            executeStatement(stmt, Sql.createProjectQuestProgressTable(dbType));
            executeStatement(stmt, Sql.createProjectMaterialContributionsTable(dbType));
            executeStatement(stmt, Sql.createProjectPoolSeedTable(dbType));
            executeStatement(stmt, Sql.createGuildProjectPoolTable(dbType));
            executeStatement(stmt, Sql.createActiveBuffsTable(dbType));

            // LLM Project Text Cache
            executeStatement(stmt, Sql.createGeneratedProjectTextsTable(dbType));

            // Indexes
            createIndexes(stmt);

            // Migrations
            migrateGuildRolesTable(stmt);
            migrateAddGuildCreatedAt(stmt);

            logger.info("Database schema initialized successfully");
        }
    }

    private void createIndexes(Statement stmt) throws SQLException {
        // Guild indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_guild_name", "guilds", "name"));

        // Guild member indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_guild_members_guild", "guild_members", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_guild_members_player", "guild_members", "player_id"));

        // Chunk claim indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_guild_chunks_guild", "guild_chunks", "guild_id"));

        // Relationship indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_relationships_guild", "guild_relationships", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_relationships_target", "guild_relationships", "target_guild_id"));

        // Invite indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_invites_guild", "guild_invites", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_invites_invitee", "guild_invites", "invitee_id"));

        // Subregion indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_subregions_guild", "subregions", "guild_id"));

        // Region permission indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_region_perms_region", "region_permissions", "region_id"));

        // Role indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_guild_roles_guild", "guild_roles", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_region_roles_region", "region_roles", "region_id"));

        // Vault indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_vault_guild", "guild_vaults", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_vault_tx_vault", "vault_transactions", "vault_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_vault_tx_player", "vault_transactions", "player_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_vault_tx_timestamp", "vault_transactions", "timestamp"));

        // Progression indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_progression_log_guild", "progression_logs", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_progression_log_player", "progression_logs", "player_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_xp_contrib_guild", "guild_xp_contributions", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_xp_contrib_player", "guild_xp_contributions", "player_id"));

        // Project indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_projects_guild", "guild_projects", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_buffs_guild", "active_buffs", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_buffs_expires", "active_buffs", "expires_at"));

        // LLM Project Text indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_llm_texts_buff_type", "generated_project_texts", "buff_type"));

        // Project pool indexes
        executeStatement(stmt, Sql.createGuildProjectPoolIndex(dbType));

        // Claim log indexes
        executeStatement(stmt, Sql.createIndex(dbType, "idx_claim_log_guild", "chunk_claim_logs", "guild_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_claim_log_player", "chunk_claim_logs", "player_id"));
        executeStatement(stmt, Sql.createIndex(dbType, "idx_claim_log_time", "chunk_claim_logs", "timestamp"));
    }

    private void executeStatement(Statement stmt, String sql) throws SQLException {
        try {
            stmt.execute(sql.trim());
        } catch (SQLException e) {
            // Log but continue - might be duplicate index, etc.
            logger.log(Level.FINE, "SQL execution warning: " + e.getMessage());
        }
    }

    /**
     * Migrates guild_roles table to add creation metadata columns.
     * Safe to run multiple times - ignores errors if columns already exist.
     */
    private void migrateGuildRolesTable(Statement stmt) throws SQLException {
        try {
            String addCreatedBy = switch (dbType) {
                case SQLITE -> "ALTER TABLE guild_roles ADD COLUMN created_by TEXT";
                case MYSQL, MARIADB, POSTGRESQL, H2 -> "ALTER TABLE guild_roles ADD COLUMN created_by VARCHAR(36)";
            };
            stmt.execute(addCreatedBy);
            logger.info("Added created_by column to guild_roles table");
        } catch (SQLException e) {
            // Column already exists, ignore
            logger.log(Level.FINE, "created_by column already exists or error adding: " + e.getMessage());
        }

        try {
            String addCreatedAt = switch (dbType) {
                case SQLITE -> "ALTER TABLE guild_roles ADD COLUMN created_at INTEGER";
                case MYSQL, MARIADB, H2 -> "ALTER TABLE guild_roles ADD COLUMN created_at BIGINT";
                case POSTGRESQL -> "ALTER TABLE guild_roles ADD COLUMN created_at BIGINT";
            };
            stmt.execute(addCreatedAt);
            logger.info("Added created_at column to guild_roles table");
        } catch (SQLException e) {
            // Column already exists, ignore
            logger.log(Level.FINE, "created_at column already exists or error adding: " + e.getMessage());
        }
    }

    /**
     * Migrates guild_project_pool_seed table to add guild_created_at column.
     * Safe to run multiple times - ignores errors if column already exists.
     */
    private void migrateAddGuildCreatedAt(Statement stmt) throws SQLException {
        try {
            String addGuildCreatedAt = Sql.addGuildCreatedAtColumn(dbType);
            stmt.execute(addGuildCreatedAt);
            logger.info("Added guild_created_at column to guild_project_pool_seed table");
        } catch (SQLException e) {
            // Column already exists, ignore
            logger.log(Level.FINE, "guild_created_at column already exists or error adding: " + e.getMessage());
        }
    }
}

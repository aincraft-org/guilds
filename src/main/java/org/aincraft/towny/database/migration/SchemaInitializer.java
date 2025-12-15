package org.aincraft.towny.database.migration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.TownyPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages database schema initialization and migrations
 */
@Singleton
public class SchemaInitializer {

    private final TownyPlugin plugin;
    private final List<DatabaseMigration> migrations;

    @Inject
    public SchemaInitializer(TownyPlugin plugin) {
        this.plugin = plugin;
        this.migrations = new ArrayList<>();

        // Register migrations here
        registerMigrations();
    }

    /**
     * Register all available migrations
     */
    private void registerMigrations() {
        // Initial schema setup
        migrations.add(new InitialSchemaMigration());

        // Add spawn location support
        migrations.add(new AddTownSpawnMigration());

        // Add home_block_y column for better spawn fallback support
        migrations.add(new AddHomeBlockYMigration());

        // Add town level system
        migrations.add(new AddTownLevelSystemMigration());

        // Migrate TownBlock permissions to bitwise system
        migrations.add(new MigrateTownBlockToBitwiseMigration());

        // Add town toggle system
        migrations.add(new AddTownToggleMigration());

        // Add plot type system with extensible registry
        migrations.add(new AddPlotTypeSystemMigration());

        // Add town broadcasting system
        migrations.add(new AddBroadcastSystemMigration());

        // Add tech tree system
        migrations.add(new AddTechTreeSystemMigration());

        // Future migrations will be added here
        // migrations.add(new MigrationV3_AddPermissionFlags());
    }

    /**
     * Initialize the database schema and apply any pending migrations
     */
    public void initialize(Connection connection) {
        try {
            // Create migration tracking table if it doesn't exist
            createMigrationTable(connection);

            // Get current database version
            int currentVersion = getCurrentDatabaseVersion(connection);

            plugin.getLogger().info("Current database version: " + currentVersion);

            // Apply any pending migrations
            for (DatabaseMigration migration : migrations) {
                if (migration.getVersion() > currentVersion && !migration.isApplied(connection)) {
                    plugin.getLogger().info("Applying migration " + migration.getVersion() + ": " + migration.getDescription());

                    try {
                        migration.migrate(connection);
                        migration.markAsApplied(connection);
                        plugin.getLogger().info("Migration " + migration.getVersion() + " applied successfully.");
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to apply migration " + migration.getVersion() + ": " + e.getMessage(), e);
                        throw new RuntimeException("Migration failed", e);
                    }
                }
            }

            int newVersion = getCurrentDatabaseVersion(connection);
            plugin.getLogger().info("Database schema initialization completed. Current version: " + newVersion);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database schema: " + e.getMessage(), e);
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    /**
     * Create the migration tracking table
     */
    private void createMigrationTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    description TEXT NOT NULL,
                    applied_at TEXT NOT NULL,
                    checksum TEXT
                )
            """);
        }
    }

    /**
     * Get the current database version (highest applied migration)
     */
    private int getCurrentDatabaseVersion(Connection connection) throws SQLException {
        String sql = "SELECT MAX(version) FROM schema_migrations";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;
        }
    }

    /**
     * Check if a specific migration has been applied
     */
    public boolean isMigrationApplied(Connection connection, int version) throws SQLException {
        String sql = "SELECT COUNT(*) FROM schema_migrations WHERE version = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, version);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Get all applied migrations
     */
    public List<MigrationInfo> getAppliedMigrations(Connection connection) throws SQLException {
        List<MigrationInfo> appliedMigrations = new ArrayList<>();
        String sql = "SELECT version, description, applied_at, checksum FROM schema_migrations ORDER BY version";

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {

            while (resultSet.next()) {
                MigrationInfo info = new MigrationInfo(
                    resultSet.getInt("version"),
                    resultSet.getString("description"),
                    resultSet.getString("applied_at"),
                    resultSet.getString("checksum")
                );
                appliedMigrations.add(info);
            }
        }

        return appliedMigrations;
    }

    /**
     * Information about an applied migration
     */
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

    /**
     * Initial schema migration - creates the basic database structure
     */
    private static class InitialSchemaMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public String getDescription() {
            return "Initial database schema creation";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // Create residents table
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS residents (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        town_name TEXT,
                        last_online INTEGER NOT NULL,
                        is_online BOOLEAN DEFAULT FALSE,
                        joined_at TEXT NOT NULL,
                        permissions_flags INTEGER DEFAULT 0
                    )
                """);

                // Create towns table
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS towns (
                        id TEXT PRIMARY KEY,
                        name TEXT UNIQUE NOT NULL,
                        mayor_uuid TEXT NOT NULL,
                        balance REAL DEFAULT 0.0,
                        home_block_x INTEGER,
                        home_block_z INTEGER,
                        home_block_world TEXT,
                        is_open BOOLEAN DEFAULT TRUE,
                        created_at TEXT NOT NULL,
                        permissions_flags INTEGER DEFAULT 0,
                        tax_rates TEXT,
                        FOREIGN KEY (mayor_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
                    )
                """);

                // Create town residents mapping table
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_residents (
                        town_id TEXT,
                        resident_uuid TEXT,
                        role TEXT DEFAULT 'resident',
                        joined_at TEXT NOT NULL,
                        PRIMARY KEY (town_id, resident_uuid),
                        FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE,
                        FOREIGN KEY (resident_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                """);

                // Create town blocks table
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_blocks (
                        id TEXT PRIMARY KEY,
                        x INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        town_id TEXT,
                        owner_uuid TEXT,
                        plot_type TEXT DEFAULT 'default',
                        price REAL DEFAULT 0.0,
                        permissions_flags INTEGER DEFAULT 0,
                        claimed_at TEXT NOT NULL,
                        custom_name TEXT,
                        FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE SET NULL,
                        FOREIGN KEY (owner_uuid) REFERENCES residents(uuid) ON DELETE SET NULL,
                        UNIQUE(x, z, world)
                    )
                """);

                // Create permissions table with bitwise flags
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS permissions (
                        id TEXT PRIMARY KEY,
                        context TEXT NOT NULL,
                        context_id TEXT NOT NULL,
                        target_type TEXT NOT NULL,
                        target_id TEXT,
                        permissions_flags INTEGER NOT NULL,
                        granted_at TEXT NOT NULL,
                        granted_by_uuid TEXT,
                        FOREIGN KEY (granted_by_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
                    )
                """);

                // Create indexes
                statement.execute("CREATE INDEX IF NOT EXISTS idx_residents_town ON residents(town_name)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_towns_name ON towns(name)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_town_blocks_location ON town_blocks(x, z, world)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_town_blocks_town ON town_blocks(town_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_town_blocks_owner ON town_blocks(owner_uuid)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_permissions_context ON permissions(context, context_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_permissions_target ON permissions(target_type, target_id)");
            }
        }

        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            // Check if migration is already recorded in schema_migrations table
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 1")) {

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

    /**
     * Migration to add spawn location columns to towns table
     */
    private static class AddTownSpawnMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 2;
        }

        @Override
        public String getDescription() {
            return "Add spawn location columns to towns table";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // Add spawn location columns to towns table
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_x REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_y REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_z REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_yaw REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_pitch REAL DEFAULT NULL
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN spawn_world TEXT DEFAULT NULL
                """);
            }
        }

        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            // Check if spawn_x column exists
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(towns)")) {
                    while (resultSet.next()) {
                        String columnName = resultSet.getString("name");
                        if ("spawn_x".equals(columnName)) {
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
                // Table doesn't exist or other error, consider not applied
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

    /**
     * Migration to add home_block_y column to towns table
     */
    private static class AddHomeBlockYMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public String getDescription() {
            return "Add home_block_y column to towns table for better spawn fallback";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // Add home_block_y column to towns table
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN home_block_y INTEGER DEFAULT NULL
                """);
            }
        }

        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            // Check if home_block_y column exists
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(towns)")) {
                    while (resultSet.next()) {
                        String columnName = resultSet.getString("name");
                        if ("home_block_y".equals(columnName)) {
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
                // Table doesn't exist or other error, consider not applied
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

    /**
     * Migration to add town level system tables and columns
     */
    private static class AddTownLevelSystemMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 4;
        }

        @Override
        public String getDescription() {
            return "Add town level system with resource contributions and tech tree points";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // Add town level columns to towns table
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN town_level INTEGER DEFAULT 1
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN tech_points INTEGER DEFAULT 0
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN upgrade_progress TEXT DEFAULT '{}'
                """);

                // Create town levels table (level definitions)
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_levels (
                        level INTEGER PRIMARY KEY,
                        resource_costs_json TEXT NOT NULL DEFAULT '{}',
                        tech_points_reward INTEGER NOT NULL DEFAULT 0,
                        claim_limit_bonus INTEGER NOT NULL DEFAULT 0,
                        assistant_slots_bonus INTEGER NOT NULL DEFAULT 0,
                        daily_income_bonus REAL NOT NULL DEFAULT 0.0,
                        unlocked_plot_types TEXT DEFAULT '[]',
                        created_at TEXT NOT NULL
                    )
                """);

                // Create town resource bank table
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_resources (
                        id TEXT PRIMARY KEY,
                        town_id TEXT NOT NULL,
                        resource_type TEXT NOT NULL,
                        amount INTEGER NOT NULL DEFAULT 0,
                        last_updated TEXT NOT NULL,
                        FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE,
                        UNIQUE(town_id, resource_type)
                    )
                """);

                // Create resource contributions table
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS resource_contributions (
                        id TEXT PRIMARY KEY,
                        town_id TEXT NOT NULL,
                        contributor_uuid TEXT NOT NULL,
                        resource_type TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        contribution_time TEXT NOT NULL,
                        FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE,
                        FOREIGN KEY (contributor_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
                    )
                """);

                // Create level benefits table for tracking unlocked benefits
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_level_benefits (
                        id TEXT PRIMARY KEY,
                        town_id TEXT NOT NULL,
                        level INTEGER NOT NULL,
                        benefit_type TEXT NOT NULL,
                        benefit_value TEXT NOT NULL,
                        unlocked_at TEXT NOT NULL,
                        FOREIGN KEY (town_id) REFERENCES towns(id) ON DELETE CASCADE,
                        UNIQUE(town_id, level, benefit_type)
                    )
                """);

                // Level data will be populated from config.yml on plugin startup
                // See TownLevelConfigLoader and TownLevelService.syncConfigToDatabase()

                // Create indexes for performance
                statement.execute("CREATE INDEX IF NOT EXISTS idx_town_resources_town ON town_resources(town_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_town_resources_type ON town_resources(resource_type)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_resource_contributions_town ON resource_contributions(town_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_resource_contributions_contributor ON resource_contributions(contributor_uuid)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_resource_contributions_time ON resource_contributions(contribution_time)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_town_level_benefits_town ON town_level_benefits(town_id)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_town_level_benefits_level ON town_level_benefits(level)");
            }
        }


        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            // Check if town_level column exists
            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(towns)")) {
                    while (resultSet.next()) {
                        String columnName = resultSet.getString("name");
                        if ("town_level".equals(columnName)) {
                            return true;
                        }
                    }
                }
            } catch (SQLException e) {
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

    /**
     * Migration to convert TownBlock permissions from Map<String, Boolean> to bitwise flags
     */
    private static class MigrateTownBlockToBitwiseMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 5;
        }

        @Override
        public String getDescription() {
            return "Convert TownBlock permissions to bitwise system using existing Permission.Flag constants";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // The town_blocks table already has permissions_flags column, so we don't need to convert from string permissions
                // Just ensure all existing town_blocks have proper permissions_flags set

                // Set default permissions for town-owned plots (no owner_uuid)
                // Town-owned plots get basic build permissions for residents
                statement.execute("""
                    UPDATE town_blocks
                    SET permissions_flags = 15
                    WHERE owner_uuid IS NULL
                    AND (permissions_flags = 0 OR permissions_flags IS NULL)
                """);

                // Set full permissions for player-owned plots
                // Player-owned plots get all permissions for the owner
                statement.execute("""
                    UPDATE town_blocks
                    SET permissions_flags = 65535
                    WHERE owner_uuid IS NOT NULL
                    AND (permissions_flags = 0 OR permissions_flags IS NULL)
                """);

                // Set specific permissions based on plot type
                statement.execute("""
                    UPDATE town_blocks
                    SET permissions_flags = 13
                    WHERE plot_type = 'shop'
                    AND owner_uuid IS NULL
                    AND (permissions_flags = 15 OR permissions_flags = 0)
                """);

                statement.execute("""
                    UPDATE town_blocks
                    SET permissions_flags = 15
                    WHERE plot_type = 'farm'
                    AND owner_uuid IS NULL
                    AND (permissions_flags = 0 OR permissions_flags = 15)
                """);

                // Ensure all town_blocks have non-null permissions_flags
                statement.execute("""
                    UPDATE town_blocks
                    SET permissions_flags = 15
                    WHERE permissions_flags IS NULL OR permissions_flags = 0
                """);
            }
        }

        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            // Check if migration is already recorded in schema_migrations table
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 5")) {

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

    /**
     * Add town toggle system - adds dedicated columns for town toggles
     */
    private static class AddTownToggleMigration implements DatabaseMigration {

        @Override
        public int getVersion() {
            return 6;
        }

        @Override
        public String getDescription() {
            return "Add town toggle system with dedicated boolean columns";
        }

        @Override
        public void migrate(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                // Add toggle columns to towns table
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN pvp_enabled BOOLEAN DEFAULT FALSE
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN fire_enabled BOOLEAN DEFAULT FALSE
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN explosions_enabled BOOLEAN DEFAULT FALSE
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN mobs_enabled BOOLEAN DEFAULT TRUE
                """);
                statement.execute("""
                    ALTER TABLE towns ADD COLUMN public_enabled BOOLEAN DEFAULT FALSE
                """);

                // Initialize existing towns with default values from permissions map if it exists
                // For now, set defaults - towns can be migrated from JSON permissions later
                statement.execute("""
                    UPDATE towns
                    SET pvp_enabled = FALSE,
                        fire_enabled = FALSE,
                        explosions_enabled = FALSE,
                        mobs_enabled = TRUE,
                        public_enabled = FALSE
                    WHERE pvp_enabled IS NULL
                """);
            }
        }

        @Override
        public boolean isApplied(Connection connection) throws SQLException {
            // Check if migration is already recorded in schema_migrations table
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations WHERE version = 6")) {

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
}
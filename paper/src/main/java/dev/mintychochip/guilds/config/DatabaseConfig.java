package dev.mintychochip.guilds.config;

import dev.mintychochip.territory.persist.Database;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * View of the host plugin's shared PostgreSQL data source.
 *
 * <p>Guilds does not own a second pool or a database file.</p>
 */
public final class DatabaseConfig {
    /** The plugin. */
    private final JavaPlugin plugin;
    /** The database. */
    private final Database database;

    /**
     * Creates a new database config instance.
     * @param plugin the plugin
     * @param database the database
     */
    public DatabaseConfig(JavaPlugin plugin, Database database) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Returns the data source.
     * @return the result
     */
    public DataSource getDataSource() {
        return database.dataSource();
    }

    /** Performs the ensure database exists operation. */
    public void ensureDatabaseExists() {
        // PostgreSQL connectivity and common schema are initialized by the host.
    }

    /** Performs the shutdown operation. */
    public void shutdown() {
        // The host plugin owns and closes the shared pool.
        plugin.getLogger().info("Guilds released shared PostgreSQL data source.");
    }
}
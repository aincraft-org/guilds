package org.aincraft.guilds.config;

import com.azoth.territory.persist.Database;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * View of the host plugin's shared PostgreSQL data source.
 *
 * <p>Guilds does not own a second pool or a database file.</p>
 */
public final class DatabaseConfig {
    private final JavaPlugin plugin;
    private final Database database;

    public DatabaseConfig(JavaPlugin plugin, Database database) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
    }

    public DataSource getDataSource() {
        return database.dataSource();
    }

    public void ensureDatabaseExists() {
        // PostgreSQL connectivity and common schema are initialized by the host.
    }

    public void shutdown() {
        // The host plugin owns and closes the shared pool.
        plugin.getLogger().info("Guilds released shared PostgreSQL data source.");
    }
}
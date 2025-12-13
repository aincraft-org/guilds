package org.aincraft.towny.dependency;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.config.DatabaseConfig;
import org.aincraft.towny.config.TownyConfig;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.services.*;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Guice dependency injection module for Towny plugin
 * Configures all dependencies and services
 */
public class TownyModule extends AbstractModule {

    private final TownyPlugin plugin;

    public TownyModule(TownyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        // Bind plugin instance
        bind(TownyPlugin.class).toInstance(plugin);

        // Bind configurations
        bind(TownyConfig.class).asEagerSingleton();
        bind(DatabaseConfig.class).asEagerSingleton();

        // Bind database
        bind(DatabaseManager.class).asEagerSingleton();

        // Bind services as singletons
        bind(ResidentService.class).to(org.aincraft.towny.services.impl.ResidentServiceImpl.class).asEagerSingleton();
        bind(TownService.class).to(org.aincraft.towny.services.impl.TownServiceImpl.class).asEagerSingleton();
        bind(PlotService.class).to(org.aincraft.towny.services.impl.PlotServiceImpl.class).asEagerSingleton();
        bind(PermissionService.class).to(org.aincraft.towny.services.impl.PermissionServiceImpl.class).asEagerSingleton();
    }

    /**
     * Provides the data directory for the plugin
     * @return Plugin data directory
     */
    @Provides
    @Singleton
    @Named("dataDirectory")
    public File provideDataDirectory() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        return dataFolder;
    }

    /**
     * Provides the SQLite database file
     * @param dataDirectory Plugin data directory
     * @return SQLite database file
     */
    @Provides
    @Singleton
    @Named("databaseFile")
    public File provideDatabaseFile(@Named("dataDirectory") File dataDirectory) {
        return new File(dataDirectory, "towny.db");
    }

    /**
     * Provides the SQLite database connection string
     * @param databaseFile Database file
     * @return JDBC connection string
     */
    @Provides
    @Singleton
    @Named("databaseUrl")
    public String provideDatabaseUrl(@Named("databaseFile") File databaseFile) {
        return "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    /**
     * Provides a database connection for testing purposes
     * Note: In production, use DatabaseManager for connection pooling
     * @param databaseUrl Database URL
     * @return Database connection
     * @throws SQLException If connection fails
     */
    @Provides
    public Connection provideConnection(@Named("databaseUrl") String databaseUrl) throws SQLException {
        return java.sql.DriverManager.getConnection(databaseUrl);
    }
}
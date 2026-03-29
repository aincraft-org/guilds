package org.aincraft.towny.dependency;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.commands.BrigadierCommandRegistry;
import org.aincraft.towny.commands.TownCommand;
import org.aincraft.towny.commands.MapCommand;
import org.aincraft.towny.commands.TownyGeneralCommand;
import org.aincraft.towny.commands.TownLevelCommand;
import org.aincraft.towny.commands.PlotCommand;
import org.aincraft.towny.commands.PlotTypeCommand;
import org.aincraft.towny.commands.PermCommand;
import org.aincraft.towny.commands.TownBroadcastCommand;
import org.aincraft.towny.commands.brigadier.*;
import org.aincraft.towny.commands.arguments.*;
import org.aincraft.towny.plot.PlotTypeRegistry;
import org.aincraft.towny.plot.PlotTypeHandlerManager;
import org.aincraft.towny.listeners.PlayerMovementListener;
import org.aincraft.towny.listeners.TownToggleListener;
import org.aincraft.towny.listeners.TownPublicAccessListener;
import org.aincraft.towny.listeners.TownBroadcastListener;
import org.aincraft.towny.config.DatabaseConfig;
import org.aincraft.towny.config.TownyConfig;
import org.aincraft.towny.config.TownLevelConfigLoader;
import org.aincraft.towny.config.TechTreeConfigLoader;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.gui.TechTreeGUI;
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
        bind(TownLevelConfigLoader.class).asEagerSingleton();
        bind(TechTreeConfigLoader.class).asEagerSingleton();

        // Bind database
        bind(DatabaseManager.class).asEagerSingleton();

        // Bind services as singletons
        bind(ResidentService.class).to(org.aincraft.towny.services.impl.ResidentServiceImpl.class).asEagerSingleton();
        bind(TownService.class).to(org.aincraft.towny.services.impl.TownServiceImpl.class).asEagerSingleton();
        bind(PlotService.class).to(org.aincraft.towny.services.impl.PlotServiceImpl.class).asEagerSingleton();

        // Bind new location and toggle services
        bind(LocationService.class).to(org.aincraft.towny.services.impl.LocationServiceImpl.class).asEagerSingleton();
        bind(TownToggleService.class).to(org.aincraft.towny.services.impl.TownToggleServiceImpl.class).asEagerSingleton();

        bind(PermissionService.class).to(org.aincraft.towny.services.impl.PermissionServiceImpl.class).asEagerSingleton();

        // Bind town level system services
        bind(TownLevelService.class).to(org.aincraft.towny.services.impl.TownLevelServiceImpl.class).asEagerSingleton();
        bind(ResourceService.class).to(org.aincraft.towny.services.impl.ResourceServiceImpl.class).asEagerSingleton();

        // Bind tech tree system services
        bind(TechTreeService.class).to(org.aincraft.towny.services.impl.TechTreeServiceImpl.class).asEagerSingleton();
        bind(EconomyService.class).to(org.aincraft.towny.services.impl.EconomyServiceImpl.class).asEagerSingleton();

        // Bind broadcast service
        bind(BroadcastService.class).to(org.aincraft.towny.services.impl.BroadcastServiceImpl.class).asEagerSingleton();

        // Bind plot type system services
        bind(PlotTypeService.class).to(org.aincraft.towny.services.impl.PlotTypeServiceImpl.class).asEagerSingleton();
        bind(PlotTypeRegistry.class).to(org.aincraft.towny.plot.PlotTypeRegistryImpl.class).asEagerSingleton();
        bind(PlotTypeHandlerManager.class).asEagerSingleton();

        // Bind legacy command classes (for backwards compatibility during transition)
        bind(TownCommand.class).asEagerSingleton();
        bind(MapCommand.class).asEagerSingleton();
        bind(TownyGeneralCommand.class).asEagerSingleton();
        bind(TownLevelCommand.class).asEagerSingleton();
        bind(PlotCommand.class).asEagerSingleton();
        bind(PlotTypeCommand.class).asEagerSingleton();
        bind(PermCommand.class).asEagerSingleton();
        bind(TownBroadcastCommand.class).asEagerSingleton();

        // Bind Brigadier command registry
        bind(BrigadierCommandRegistry.class).asEagerSingleton();

        // Bind Brigadier command classes
        bind(TownBrigadierCommand.class).asEagerSingleton();
        bind(PlotBrigadierCommand.class).asEagerSingleton();
        bind(TownyGeneralBrigadierCommand.class).asEagerSingleton();
        bind(TownLevelBrigadierCommand.class).asEagerSingleton();
        bind(MapBrigadierCommand.class).asEagerSingleton();
        bind(PermBrigadierCommand.class).asEagerSingleton();
        bind(PlotTypeBrigadierCommand.class).asEagerSingleton();
        bind(TownBroadcastBrigadierCommand.class).asEagerSingleton();
        bind(TownPermBrigadierCommand.class).asEagerSingleton();
        bind(TechTreeBrigadierCommand.class).asEagerSingleton();

        // Bind GUI classes
        bind(TechTreeGUI.class).asEagerSingleton();

        // Bind listener classes
        bind(PlayerMovementListener.class).asEagerSingleton();
        bind(TownToggleListener.class).asEagerSingleton();
        bind(TownPublicAccessListener.class).asEagerSingleton();
        bind(TownBroadcastListener.class).asEagerSingleton();
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
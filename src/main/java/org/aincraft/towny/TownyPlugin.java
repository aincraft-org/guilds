package org.aincraft.towny;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.aincraft.towny.commands.TownCommand;
import org.aincraft.towny.dependency.TownyModule;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for Towny - Enhanced town and nation management plugin
 */
public class TownyPlugin extends JavaPlugin {

    private static TownyPlugin instance;
    private Injector injector;

    @Override
    public void onEnable() {
        instance = this;

        // Save default configuration
        saveDefaultConfig();

        // Initialize dependency injection
        setupDependencyInjection();

        // Initialize core services
        initializeServices();

        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();

        getLogger().info("Towny has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Cleanup resources
        getLogger().info("Towny has been disabled.");
    }

    /**
     * Gets the plugin instance
     * @return TownyPlugin instance
     */
    public static TownyPlugin getPlugin() {
        return instance;
    }

    /**
     * Gets the Guice injector for dependency injection
     * @return Guice injector instance
     */
    public Injector getInjector() {
        return injector;
    }

    /**
     * Sets up Guice dependency injection
     */
    private void setupDependencyInjection() {
        try {
            // Create Guice module and injector
            TownyModule module = new TownyModule(this);
            injector = Guice.createInjector(module);

            getLogger().info("Dependency injection initialized successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize dependency injection: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Initialize core services
     */
    private void initializeServices() {
        try {
            // Services will be automatically injected through Guice
            // This is just a placeholder for any additional initialization needed
            getLogger().info("Core services initialized.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize core services: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register plugin commands
     */
    private void registerCommands() {
        try {
            // Get command handlers from injector
            TownCommand townCommand = injector.getInstance(TownCommand.class);

            // Register commands
            getCommand("town").setExecutor(townCommand);
            getCommand("town").setTabCompleter(townCommand);

            getLogger().info("Town commands registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        // Listeners will be registered through Guice injection
        // This is a placeholder - we'll implement actual listener registration later
        getLogger().info("Event listeners registered.");
    }
}
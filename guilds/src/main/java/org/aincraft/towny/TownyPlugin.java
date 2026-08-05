package org.aincraft.towny;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.aincraft.towny.commands.BrigadierCommandRegistry;
import org.aincraft.towny.commands.TownCommand;
import org.aincraft.towny.commands.MapCommand;
import org.aincraft.towny.commands.TownyGeneralCommand;
import org.aincraft.towny.commands.TownLevelCommand;
import org.aincraft.towny.commands.PlotCommand;
import org.aincraft.towny.commands.PlotTypeCommand;
import org.aincraft.towny.commands.PermCommand;
import org.aincraft.towny.commands.TownBroadcastCommand;
import org.aincraft.towny.config.TownLevelConfigLoader;
import org.aincraft.towny.dependency.TownyModule;
import org.aincraft.towny.listeners.PlayerMovementListener;
import org.aincraft.towny.listeners.TownToggleListener;
import org.aincraft.towny.listeners.TownPublicAccessListener;
import org.aincraft.towny.listeners.TownBroadcastListener;
import org.aincraft.towny.services.TownLevelService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for Towny - Enhanced town and nation management plugin
 */
public class TownyPlugin extends JavaPlugin implements Listener {

    private static TownyPlugin instance;
    private Injector injector;
    private PlayerMovementListener playerMovementListener;
    private TownToggleListener townToggleListener;
    private TownPublicAccessListener townPublicAccessListener;
    private TownBroadcastListener townBroadcastListener;
    @Inject
    private org.aincraft.towny.web.WebServer webServer;

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
        // Stop web server
        webServer.stop();
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

            // Inject plugin-level dependencies
            injector.injectMembers(this);

            // Start web server for tech tree web interface
            webServer.start();

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
            // Load town level configuration from config.yml
            TownLevelConfigLoader configLoader = injector.getInstance(TownLevelConfigLoader.class);
            configLoader.loadConfiguration();
            getLogger().info("Town level configuration loaded successfully.");

            // Sync configuration to database
            TownLevelService townLevelService = injector.getInstance(TownLevelService.class);
            townLevelService.syncConfigToDatabase();
            getLogger().info("Town level data synchronized to database.");

            // Load tech tree configuration
            org.aincraft.towny.config.TechTreeConfigLoader techTreeConfigLoader = injector.getInstance(org.aincraft.towny.config.TechTreeConfigLoader.class);
            techTreeConfigLoader.loadConfiguration();
            getLogger().info("Tech tree configuration loaded successfully.");

            // Sync tech tree configuration to database
            org.aincraft.towny.services.TechTreeService techTreeService = injector.getInstance(org.aincraft.towny.services.TechTreeService.class);
            techTreeService.syncConfigToDatabase();
            getLogger().info("Tech tree data synchronized to database.");

            // Initialize plot types
            org.aincraft.towny.services.PlotTypeService plotTypeService = injector.getInstance(org.aincraft.towny.services.PlotTypeService.class);
            plotTypeService.initializeBuiltInTypes();
            getLogger().info("Plot type registry initialized.");

            getLogger().info("Core services initialized.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize core services: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register plugin commands using Brigadier
     */
    private void registerCommands() {
        try {
            // Check if injector was initialized successfully
            if (injector == null) {
                getLogger().severe("Cannot register commands - dependency injection failed to initialize.");
                return;
            }

            // Get Brigadier command registry from injector
            BrigadierCommandRegistry commandRegistry = injector.getInstance(BrigadierCommandRegistry.class);

            // Register all Brigadier commands
            commandRegistry.registerCommands();

            getLogger().info("Brigadier commands registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register Brigadier commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        // Get player movement listener from Guice injector
        playerMovementListener = injector.getInstance(PlayerMovementListener.class);

        // Get toggle listeners from Guice injector
        townToggleListener = injector.getInstance(TownToggleListener.class);
        townPublicAccessListener = injector.getInstance(TownPublicAccessListener.class);
        townBroadcastListener = injector.getInstance(TownBroadcastListener.class);

        // Register town chat listener
        getServer().getPluginManager().registerEvents(injector.getInstance(org.aincraft.towny.listeners.TownChatListener.class), this);

        // Register nation listener
        getServer().getPluginManager().registerEvents(injector.getInstance(org.aincraft.towny.listeners.NationListener.class), this);

        // Register events
        getServer().getPluginManager().registerEvents(playerMovementListener, this);
        getServer().getPluginManager().registerEvents(townToggleListener, this);
        getServer().getPluginManager().registerEvents(townPublicAccessListener, this);
        getServer().getPluginManager().registerEvents(townBroadcastListener, this);
        getServer().getPluginManager().registerEvents(injector.getInstance(org.aincraft.towny.gui.TechTreeGUI.class), this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("Event listeners registered.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up player data to prevent memory leaks
        if (playerMovementListener != null) {
            playerMovementListener.cleanupOfflinePlayer(event.getPlayer().getUniqueId());
        }
    }
}
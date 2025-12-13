package org.aincraft.towny;

import com.google.inject.Guice;
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
import org.aincraft.towny.dependency.TownyModule;
import org.aincraft.towny.listeners.PlayerMovementListener;
import org.aincraft.towny.listeners.TownToggleListener;
import org.aincraft.towny.listeners.TownPublicAccessListener;
import org.aincraft.towny.listeners.TownBroadcastListener;
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

        // Register events
        getServer().getPluginManager().registerEvents(playerMovementListener, this);
        getServer().getPluginManager().registerEvents(townToggleListener, this);
        getServer().getPluginManager().registerEvents(townPublicAccessListener, this);
        getServer().getPluginManager().registerEvents(townBroadcastListener, this);
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
package org.aincraft.towny;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.aincraft.towny.commands.BrigadierCommandRegistry;
import org.aincraft.towny.config.TownLevelConfigLoader;
import org.aincraft.towny.dependency.TownyModule;
import org.aincraft.towny.listeners.PlayerMovementListener;
import org.aincraft.towny.listeners.TownToggleListener;
import org.aincraft.towny.listeners.TownPublicAccessListener;
import org.aincraft.towny.listeners.TownBroadcastListener;
import org.aincraft.towny.services.TownLevelService;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Guilds / Towny subsystem lifecycle, hosted by the single Azoth Territory {@link JavaPlugin}.
 * Not a separate Paper plugin identity — one {@code plugin.yml}, one main class.
 */
public class TownyPlugin implements Listener {

    /** Resource name for guilds defaults (avoids clobbering territory {@code config.yml}). */
    public static final String GUILDS_CONFIG_RESOURCE = "guilds-config.yml";
    public static final String GUILDS_CONFIG_FILE = "guilds-config.yml";

    private static TownyPlugin instance;

    private final JavaPlugin host;
    private FileConfiguration guildsConfig;
    private File guildsConfigFile;
    private Injector injector;
    private PlayerMovementListener playerMovementListener;
    private TownToggleListener townToggleListener;
    private TownPublicAccessListener townPublicAccessListener;
    private TownBroadcastListener townBroadcastListener;
    @Inject
    private org.aincraft.towny.web.SessionManager sessionManager;
    private boolean enabled;

    @Inject
    private org.aincraft.towny.web.WebServer webServer;

    public TownyPlugin(JavaPlugin host) {
        this.host = host;
    }

    /**
     * Host Paper plugin that owns this subsystem (commands, listeners, data folder).
     */
    public JavaPlugin getHost() {
        return host;
    }

    /**
     * Enables the guilds/towny subsystem on the host plugin.
     */
    public void enable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        setupDependencyInjection();
        if (injector == null) {
            getLogger().severe("Guilds subsystem failed to start — dependency injection unavailable.");
            enabled = false;
            return;
        }

        initializeServices();
        registerCommands();
        registerListeners();
        enabled = true;
        getLogger().info("Guilds (Towny) subsystem has been enabled successfully!");
    }

    /**
     * Disables the guilds/towny subsystem.
     */
    public void disable() {
        enabled = false;
        if (webServer != null) {
            try {
                webServer.stop();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error stopping guilds web server", e);
            }
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        getLogger().info("Guilds (Towny) subsystem has been disabled.");
    }

    /**
     * Gets the guilds subsystem instance.
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

    public boolean isEnabled() {
        return enabled && host.isEnabled();
    }

    public Logger getLogger() {
        return host.getLogger();
    }

    public Server getServer() {
        return host.getServer();
    }

    public File getDataFolder() {
        return host.getDataFolder();
    }

    public String getName() {
        return host.getName();
    }

    public PluginDescriptionFile getDescription() {
        return host.getDescription();
    }

    public InputStream getResource(String name) {
        return host.getResource(name);
    }

    public LifecycleEventManager<? extends Plugin> getLifecycleManager() {
        return host.getLifecycleManager();
    }

    /**
     * Paper plugin used when registering events, schedulers, and lifecycle handlers.
     */
    public Plugin asPlugin() {
        return host;
    }

    public FileConfiguration getConfig() {
        if (guildsConfig == null) {
            reloadConfig();
        }
        return guildsConfig;
    }

    public void saveDefaultConfig() {
        File out = new File(getDataFolder(), GUILDS_CONFIG_FILE);
        if (!out.exists()) {
            host.saveResource(GUILDS_CONFIG_RESOURCE, false);
        }
    }

    public void saveConfig() {
        if (guildsConfig == null) {
            return;
        }
        try {
            guildsConfig.save(guildsConfigFile());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save " + GUILDS_CONFIG_FILE, e);
        }
    }

    public void reloadConfig() {
        guildsConfigFile = guildsConfigFile();
        guildsConfig = YamlConfiguration.loadConfiguration(guildsConfigFile);
        InputStream def = host.getResource(GUILDS_CONFIG_RESOURCE);
        if (def != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(def, StandardCharsets.UTF_8));
            guildsConfig.setDefaults(defaults);
        }
    }

    private File guildsConfigFile() {
        if (guildsConfigFile == null) {
            guildsConfigFile = new File(getDataFolder(), GUILDS_CONFIG_FILE);
        }
        return guildsConfigFile;
    }

    private void setupDependencyInjection() {
        try {
            TownyModule module = new TownyModule(this);
            injector = Guice.createInjector(module);
            injector.injectMembers(this);
            webServer.start();
            getLogger().info("Guilds dependency injection initialized successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize guilds dependency injection: " + e.getMessage());
            e.printStackTrace();
            injector = null;
        }
    }

    private void initializeServices() {
        try {
            TownLevelConfigLoader configLoader = injector.getInstance(TownLevelConfigLoader.class);
            configLoader.loadConfiguration();
            getLogger().info("Town level configuration loaded successfully.");

            TownLevelService townLevelService = injector.getInstance(TownLevelService.class);
            townLevelService.syncConfigToDatabase();
            getLogger().info("Town level data synchronized to database.");

            org.aincraft.towny.config.TechTreeConfigLoader techTreeConfigLoader =
                    injector.getInstance(org.aincraft.towny.config.TechTreeConfigLoader.class);
            techTreeConfigLoader.loadConfiguration();
            getLogger().info("Tech tree configuration loaded successfully.");

            org.aincraft.towny.services.TechTreeService techTreeService =
                    injector.getInstance(org.aincraft.towny.services.TechTreeService.class);
            techTreeService.syncConfigToDatabase();
            getLogger().info("Tech tree data synchronized to database.");

            org.aincraft.towny.plot.PlotTypeRegistry plotTypeRegistry =
                    injector.getInstance(org.aincraft.towny.plot.PlotTypeRegistry.class);
            plotTypeRegistry.registerBuiltInTypes();
            getLogger().info("Plot type registry initialized.");

            getLogger().info("Guilds core services initialized.");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize guilds core services: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerCommands() {
        try {
            if (injector == null) {
                getLogger().severe("Cannot register guilds commands - dependency injection failed to initialize.");
                return;
            }
            BrigadierCommandRegistry commandRegistry = injector.getInstance(BrigadierCommandRegistry.class);
            commandRegistry.registerCommands();
            getLogger().info("Guilds Brigadier commands registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register guilds Brigadier commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerListeners() {
        Plugin owner = host;
        playerMovementListener = injector.getInstance(PlayerMovementListener.class);
        townToggleListener = injector.getInstance(TownToggleListener.class);
        townPublicAccessListener = injector.getInstance(TownPublicAccessListener.class);
        townBroadcastListener = injector.getInstance(TownBroadcastListener.class);

        getServer().getPluginManager().registerEvents(
                injector.getInstance(org.aincraft.towny.listeners.TownChatListener.class), owner);
        getServer().getPluginManager().registerEvents(
                injector.getInstance(org.aincraft.towny.listeners.NationListener.class), owner);
        getServer().getPluginManager().registerEvents(playerMovementListener, owner);
        getServer().getPluginManager().registerEvents(townToggleListener, owner);
        getServer().getPluginManager().registerEvents(townPublicAccessListener, owner);
        getServer().getPluginManager().registerEvents(townBroadcastListener, owner);
        getServer().getPluginManager().registerEvents(
                injector.getInstance(org.aincraft.towny.gui.TechTreeGUI.class), owner);
        getServer().getPluginManager().registerEvents(this, owner);

        getLogger().info("Guilds event listeners registered.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (playerMovementListener != null) {
            playerMovementListener.cleanupOfflinePlayer(event.getPlayer().getUniqueId());
        }
    }
}

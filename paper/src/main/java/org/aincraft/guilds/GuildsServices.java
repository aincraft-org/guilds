package org.aincraft.guilds;

import org.aincraft.guilds.commands.BrigadierCommandRegistry;
import org.aincraft.guilds.commands.brigadier.BlueprintBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.ChatBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.MapBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.NationBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PermBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PlotBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.PlotTypeBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.QuestBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.SpecializationBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.TechTreeBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildBroadcastBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildLevelBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildPermBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.GuildsGeneralBrigadierCommand;
import org.aincraft.guilds.config.DatabaseConfig;
import org.aincraft.guilds.config.TechTreeConfigLoader;
import org.aincraft.guilds.config.GuildLevelConfigLoader;
import org.aincraft.guilds.config.GuildsConfig;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.database.migration.SchemaInitializer;
import org.aincraft.guilds.gui.TechTreeGUI;
import org.aincraft.guilds.listeners.NationListener;
import org.aincraft.guilds.listeners.PlayerMovementListener;
import org.aincraft.guilds.listeners.GuildBroadcastListener;
import org.aincraft.guilds.listeners.GuildChatListener;
import org.aincraft.guilds.listeners.GuildPublicAccessListener;
import org.aincraft.guilds.listeners.GuildToggleListener;
import org.aincraft.guilds.plot.PlotTypeHandlerManager;
import org.aincraft.guilds.plot.PlotTypeRegistry;
import org.aincraft.guilds.plot.PlotTypeRegistryImpl;
import org.aincraft.guilds.services.BlueprintService;
import org.aincraft.guilds.services.BroadcastService;
import org.aincraft.guilds.services.ChatService;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.NationService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.QuestService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.ResourceService;
import org.aincraft.guilds.services.SpecializationService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.GuildToggleService;
import org.aincraft.guilds.services.impl.BlueprintServiceImpl;
import org.aincraft.guilds.services.impl.BroadcastServiceImpl;
import org.aincraft.guilds.services.impl.ChatServiceImpl;
import org.aincraft.guilds.services.impl.LocationServiceImpl;
import org.aincraft.guilds.services.impl.NationServiceImpl;
import org.aincraft.guilds.services.impl.PermissionServiceImpl;
import org.aincraft.guilds.services.impl.PlotServiceImpl;
import org.aincraft.guilds.services.impl.QuestServiceImpl;
import org.aincraft.guilds.services.impl.ResidentServiceImpl;
import org.aincraft.guilds.services.impl.ResourceServiceImpl;
import org.aincraft.guilds.services.impl.SpecializationServiceImpl;
import org.aincraft.guilds.services.impl.TechTreeServiceImpl;
import org.aincraft.guilds.services.impl.GuildLevelServiceImpl;
import org.aincraft.guilds.services.impl.GuildServiceImpl;
import org.aincraft.guilds.services.impl.GuildToggleServiceImpl;
import org.aincraft.guilds.web.SessionManager;
import org.aincraft.guilds.web.WebServer;
import org.aincraft.guilds.web.WebServerConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manual composition root and lifecycle owner for the Guilds subsystem, hosted
 * directly by the single Azoth Territory {@link JavaPlugin}. There is no second
 * plugin identity: the host owns commands, listeners, data folder, and config.
 *
 * <p>Plain constructor wiring replaces the former Guice {@code GuildsModule}: every
 * dependency is built here in topological order and handed to consumers via
 * constructors (or, for the single Guild/Permission/Plot service cycle, a
 * post-construction setter). All instances are effectively singletons, matching
 * the old eager-singleton bindings.</p>
 */
public class GuildsServices {

    /** Resource/file name for guilds defaults (avoids clobbering territory {@code config.yml}). */
    public static final String GUILDS_CONFIG = "guilds-config.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private boolean enabled;

    // Config
    private final GuildsConfig guildsConfig;
    private final DatabaseConfig databaseConfig;
    private final GuildLevelConfigLoader guildLevelConfigLoader;
    private final TechTreeConfigLoader techTreeConfigLoader;

    // Database
    private final DatabaseManager databaseManager;

    // Services
    private final ResidentService residentService;
    private final GuildService guildService;
    private final PlotService plotService;
    private final LocationService locationService;
    private final GuildToggleService guildToggleService;
    private final PermissionService permissionService;
    private final GuildLevelService guildLevelService;
    private final ResourceService resourceService;
    private final TechTreeService techTreeService;
    private final SpecializationService specializationService;
    private final BroadcastService broadcastService;
    private final ChatService chatService;
    private final NationService nationService;
    private final QuestService questService;
    private final BlueprintService blueprintService;

    // Governance (guilds as local governments, nations as alliances)
    private final GuildsGovernanceSource governanceSource;

    // Plot types
    private final PlotTypeRegistry plotTypeRegistry;
    private final PlotTypeHandlerManager plotTypeHandlerManager;

    // Web
    private final WebServerConfig webServerConfig;
    private final SessionManager sessionManager;
    private final WebServer webServer;

    // GUI
    private final TechTreeGUI techTreeGUI;

    // Commands
    private final BrigadierCommandRegistry commandRegistry;

    // Listeners
    private final PlayerMovementListener playerMovementListener;
    private final GuildToggleListener guildToggleListener;
    private final GuildPublicAccessListener guildPublicAccessListener;
    private final GuildBroadcastListener guildBroadcastListener;
    private final GuildChatListener guildChatListener;
    private final NationListener nationListener;

    public GuildsServices(JavaPlugin plugin) {
        this.plugin = plugin;

        // Guilds config file (namespaced away from the territory config.yml)
        saveDefaultConfig();
        reloadConfig();

        // Data directory and SQLite location (was @Named("dataDirectory"/"databaseFile"/"databaseUrl"))
        File dataDirectory = plugin.getDataFolder();
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
        File databaseFile = new File(dataDirectory, "guilds.db");
        String databaseUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        // Config (eager singletons in the old module)
        guildsConfig = new GuildsConfig(config);
        databaseConfig = new DatabaseConfig(plugin, databaseFile, databaseUrl);
        guildLevelConfigLoader = new GuildLevelConfigLoader(config, plugin.getLogger());
        techTreeConfigLoader = new TechTreeConfigLoader(plugin);

        // Database (SchemaInitializer was JIT-injected once; keep one shared instance)
        SchemaInitializer schemaInitializer = new SchemaInitializer(plugin);
        databaseManager = new DatabaseManager(plugin, databaseConfig, schemaInitializer);

        // Services. The GuildService <-> PermissionService <-> PlotService cycle is
        // broken deliberately: GuildServiceImpl is built without PermissionService
        // and receives it via setPermissionService once it exists.
        residentService = new ResidentServiceImpl(databaseManager,
                Logger.getLogger(ResidentServiceImpl.class.getName()));
        GuildServiceImpl guildImpl = new GuildServiceImpl(databaseManager,
                Logger.getLogger(GuildServiceImpl.class.getName()), residentService);
        guildService = guildImpl;
        plotService = new PlotServiceImpl(databaseManager, guildService,
                Logger.getLogger(PlotServiceImpl.class.getName()));
        locationService = new LocationServiceImpl(plotService, guildService);
        guildToggleService = new GuildToggleServiceImpl(locationService);
        permissionService = new PermissionServiceImpl(databaseManager,
                Logger.getLogger(PermissionServiceImpl.class.getName()), plotService, guildService,
                residentService, guildToggleService, locationService);
        guildImpl.setPermissionService(permissionService);

        guildLevelService = new GuildLevelServiceImpl(plugin, databaseManager, guildService, guildLevelConfigLoader);
        resourceService = new ResourceServiceImpl(plugin, databaseManager, guildService);
        techTreeService = new TechTreeServiceImpl(plugin, databaseManager, techTreeConfigLoader, guildService);
        specializationService = new SpecializationServiceImpl(plugin, databaseManager, guildService);
        broadcastService = new BroadcastServiceImpl(databaseManager,
                Logger.getLogger(BroadcastServiceImpl.class.getName()),
                guildService, residentService, permissionService);
        chatService = new ChatServiceImpl(plugin, guildService, residentService);
        nationService = new NationServiceImpl(plugin, databaseManager,
                Logger.getLogger(NationServiceImpl.class.getName()), guildService);
        questService = new QuestServiceImpl(plugin, databaseManager);
        blueprintService = new BlueprintServiceImpl(plugin, databaseManager);

        // Governance: guilds as local governments, nations as alliances
        governanceSource = new GuildsGovernanceSource(databaseManager, guildService, nationService,
                Logger.getLogger(GuildsGovernanceSource.class.getName()));

        // Plot type registry
        plotTypeRegistry = new PlotTypeRegistryImpl(Logger.getLogger(PlotTypeRegistryImpl.class.getName()));
        plotTypeHandlerManager = new PlotTypeHandlerManager(plotTypeRegistry,
                Logger.getLogger(PlotTypeHandlerManager.class.getName()));

        // Web
        webServerConfig = WebServerConfig.loadFromConfig(config);
        sessionManager = new SessionManager(webServerConfig, Logger.getLogger(SessionManager.class.getName()));
        webServer = new WebServer(techTreeService, guildService, sessionManager, webServerConfig,
                Logger.getLogger(WebServer.class.getName()));

        // GUI
        techTreeGUI = new TechTreeGUI(plugin, techTreeService, guildService, residentService);

        // Listeners
        playerMovementListener = new PlayerMovementListener(plugin, plotService, guildService,
                residentService, plotTypeHandlerManager, plotTypeRegistry);
        guildToggleListener = new GuildToggleListener(plugin, permissionService);
        guildPublicAccessListener = new GuildPublicAccessListener(plugin, permissionService, residentService);
        guildBroadcastListener = new GuildBroadcastListener(plugin, broadcastService, residentService,
                guildService, Logger.getLogger(GuildBroadcastListener.class.getName()));
        guildChatListener = new GuildChatListener(plugin, chatService, guildService, residentService);
        nationListener = new NationListener(nationService, guildService, residentService);

        // Commands (built before the registry, which owns them)
        TechTreeBrigadierCommand techTreeCommand = new TechTreeBrigadierCommand(plugin, techTreeService,
                guildService, residentService, techTreeGUI, sessionManager, webServerConfig);
        GuildBrigadierCommand guildCommand = new GuildBrigadierCommand(plugin, residentService, guildService,
                plotService, permissionService, techTreeCommand, plotTypeRegistry, governanceSource);
        PlotBrigadierCommand plotCommand = new PlotBrigadierCommand(plugin, residentService, guildService,
                plotService, permissionService, plotTypeRegistry);
        GuildsGeneralBrigadierCommand guildsGeneralCommand = new GuildsGeneralBrigadierCommand(plugin,
                residentService, guildService, plotService, permissionService);
        GuildLevelBrigadierCommand guildLevelCommand = new GuildLevelBrigadierCommand(plugin, residentService,
                guildService, plotService, permissionService, guildLevelService, resourceService);
        MapBrigadierCommand mapCommand = new MapBrigadierCommand(plugin, residentService, guildService,
                plotService, permissionService);
        PermBrigadierCommand permCommand = new PermBrigadierCommand(plugin, permissionService, plotService, guildService);
        PlotTypeBrigadierCommand plotTypeCommand = new PlotTypeBrigadierCommand();
        GuildBroadcastBrigadierCommand guildBroadcastCommand = new GuildBroadcastBrigadierCommand();
        GuildPermBrigadierCommand guildPermCommand = new GuildPermBrigadierCommand(plugin, residentService,
                guildService, plotService, permissionService);
        ChatBrigadierCommand chatCommand = new ChatBrigadierCommand(plugin, chatService, guildService, residentService);
        NationBrigadierCommand nationCommand = new NationBrigadierCommand(plugin, nationService, guildService,
                residentService, governanceSource);
        SpecializationBrigadierCommand specializationCommand = new SpecializationBrigadierCommand(plugin,
                specializationService, guildService, residentService);
        QuestBrigadierCommand questCommand = new QuestBrigadierCommand(plugin, questService, guildService, residentService);
        BlueprintBrigadierCommand blueprintCommand = new BlueprintBrigadierCommand(plugin, blueprintService,
                guildService, residentService);

        commandRegistry = new BrigadierCommandRegistry(plugin, guildCommand, plotCommand, guildsGeneralCommand,
                guildLevelCommand, mapCommand, permCommand, plotTypeCommand, guildBroadcastCommand, guildPermCommand,
                techTreeCommand, chatCommand, nationCommand, specializationCommand, questCommand, blueprintCommand);
    }

    /**
     * Starts the guilds subsystem on the host plugin (web server, config sync, commands, listeners).
     */
    public void enable() {
        try {
            webServer.start();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Guilds subsystem failed to start — service wiring unavailable.", e);
            enabled = false;
            return;
        }

        initializeServices();
        registerCommands();
        registerListeners();
        enabled = true;
        plugin.getLogger().info("Guilds subsystem has been enabled successfully!");
    }

    /**
     * Stops the guilds subsystem (web server, sessions).
     */
    public void disable() {
        enabled = false;
        if (webServer != null) {
            try {
                webServer.stop();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error stopping guilds web server", e);
            }
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        plugin.getLogger().info("Guilds subsystem has been disabled.");
    }

    public boolean isEnabled() {
        return enabled && plugin.isEnabled();
    }

    /**
     * The guilds configuration (loaded from {@value #GUILDS_CONFIG}).
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Host Paper plugin that owns this subsystem (commands, listeners, data folder).
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }

    private void saveDefaultConfig() {
        File out = new File(plugin.getDataFolder(), GUILDS_CONFIG);
        if (!out.exists()) {
            plugin.saveResource(GUILDS_CONFIG, false);
        }
    }

    private void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), GUILDS_CONFIG);
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream def = plugin.getResource(GUILDS_CONFIG);
        if (def != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(def, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
    }

    private void initializeServices() {
        try {
            guildLevelConfigLoader.loadConfiguration();
            plugin.getLogger().info("Town level configuration loaded successfully.");

            guildLevelService.syncConfigToDatabase();
            plugin.getLogger().info("Town level data synchronized to database.");

            techTreeConfigLoader.loadConfiguration();
            plugin.getLogger().info("Tech tree configuration loaded successfully.");

            techTreeService.syncConfigToDatabase();
            plugin.getLogger().info("Tech tree data synchronized to database.");

            plotTypeRegistry.registerBuiltInTypes();
            plugin.getLogger().info("Plot type registry initialized.");

            plugin.getLogger().info("Guilds core services initialized.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize guilds core services", e);
        }
    }

    private void registerCommands() {
        try {
            commandRegistry.registerCommands();
            plugin.getLogger().info("Guilds Brigadier commands registered successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register guilds Brigadier commands", e);
        }
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(guildChatListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(nationListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(playerMovementListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(guildToggleListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(guildPublicAccessListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(guildBroadcastListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(techTreeGUI, plugin);

        plugin.getLogger().info("Guilds event listeners registered.");
    }

    public GuildsConfig getGuildsConfig() {
        return guildsConfig;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    public GuildLevelConfigLoader getGuildLevelConfigLoader() {
        return guildLevelConfigLoader;
    }

    public TechTreeConfigLoader getTechTreeConfigLoader() {
        return techTreeConfigLoader;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ResidentService getResidentService() {
        return residentService;
    }

    public GuildService getGuildService() {
        return guildService;
    }

    public PlotService getPlotService() {
        return plotService;
    }

    public LocationService getLocationService() {
        return locationService;
    }

    public GuildToggleService getGuildToggleService() {
        return guildToggleService;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public GuildLevelService getGuildLevelService() {
        return guildLevelService;
    }

    public ResourceService getResourceService() {
        return resourceService;
    }

    public TechTreeService getTechTreeService() {
        return techTreeService;
    }

    public SpecializationService getSpecializationService() {
        return specializationService;
    }

    public BroadcastService getBroadcastService() {
        return broadcastService;
    }

    public ChatService getChatService() {
        return chatService;
    }

    public NationService getNationService() {
        return nationService;
    }

    public GuildsGovernanceSource getGovernanceSource() {
        return governanceSource;
    }

    public QuestService getQuestService() {
        return questService;
    }

    public BlueprintService getBlueprintService() {
        return blueprintService;
    }

    public PlotTypeRegistry getPlotTypeRegistry() {
        return plotTypeRegistry;
    }

    public PlotTypeHandlerManager getPlotTypeHandlerManager() {
        return plotTypeHandlerManager;
    }

    public WebServerConfig getWebServerConfig() {
        return webServerConfig;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public TechTreeGUI getTechTreeGUI() {
        return techTreeGUI;
    }

    public BrigadierCommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public PlayerMovementListener getPlayerMovementListener() {
        return playerMovementListener;
    }

    public GuildToggleListener getGuildToggleListener() {
        return guildToggleListener;
    }

    public GuildPublicAccessListener getGuildPublicAccessListener() {
        return guildPublicAccessListener;
    }

    public GuildBroadcastListener getGuildBroadcastListener() {
        return guildBroadcastListener;
    }

    public GuildChatListener getGuildChatListener() {
        return guildChatListener;
    }

    public NationListener getNationListener() {
        return nationListener;
    }
}

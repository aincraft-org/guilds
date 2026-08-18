package dev.mintychochip.guilds;

import dev.mintychochip.guilds.commands.brigadier.TerritoryBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TerritoryCommand;
import dev.mintychochip.guilds.commands.BrigadierCommandRegistry;
import dev.mintychochip.guilds.commands.brigadier.ChatBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.MapBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.AllianceBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PermBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PlotBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.PlotTypeBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.QuestBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.SpecializationBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.TechTreeBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildBroadcastBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildLevelBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildPermBrigadierCommand;
import dev.mintychochip.guilds.commands.brigadier.GuildsGeneralBrigadierCommand;
import dev.mintychochip.guilds.config.DatabaseConfig;
import dev.mintychochip.guilds.config.TechTreeConfigLoader;
import dev.mintychochip.guilds.config.GuildLevelConfigLoader;
import dev.mintychochip.guilds.config.GuildsConfig;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.territory.persist.Database;
import dev.mintychochip.guilds.database.migration.SchemaInitializer;
import dev.mintychochip.guilds.gui.TechTreeGUI;
import dev.mintychochip.guilds.listeners.AllianceListener;
import dev.mintychochip.guilds.listeners.PlayerMovementListener;
import dev.mintychochip.guilds.listeners.GuildBroadcastListener;
import dev.mintychochip.guilds.listeners.GuildToggleListener;
import dev.mintychochip.guilds.listeners.GuildPublicAccessListener;
import dev.mintychochip.guilds.listeners.GuildChatListener;
import dev.mintychochip.guilds.listeners.GuildBankVillagerListener;
import dev.mintychochip.guilds.services.GuildBankEnrollmentService;
import dev.mintychochip.guilds.services.MintGuildBankService;
import dev.mintychochip.guilds.services.MintTransferPort;
import dev.mintychochip.guilds.services.impl.GuildBankEnrollmentServiceImpl;
import dev.mintychochip.guilds.plot.PlotTypeHandlerManager;
import dev.mintychochip.guilds.plot.PlotTypeRegistry;
import dev.mintychochip.guilds.plot.PlotTypeRegistryImpl;
import dev.mintychochip.guilds.services.BroadcastService;
import dev.mintychochip.guilds.services.ChatService;
import dev.mintychochip.guilds.services.LocationService;
import dev.mintychochip.guilds.services.AllianceService;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.QuestService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.ResourceService;
import dev.mintychochip.guilds.services.SpecializationService;
import dev.mintychochip.guilds.services.TechTreeService;
import dev.mintychochip.guilds.services.GuildProjectService;
import dev.mintychochip.guilds.services.GuildLevelService;
import dev.mintychochip.guilds.services.GuildContractService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.GuildToggleService;
import dev.mintychochip.guilds.services.impl.BroadcastServiceImpl;
import dev.mintychochip.guilds.services.impl.ChatServiceImpl;
import dev.mintychochip.guilds.services.impl.LocationServiceImpl;
import dev.mintychochip.guilds.services.impl.AllianceServiceImpl;
import dev.mintychochip.guilds.services.impl.PermissionServiceImpl;
import dev.mintychochip.guilds.services.impl.PlotServiceImpl;
import dev.mintychochip.guilds.services.impl.QuestServiceImpl;
import dev.mintychochip.guilds.services.impl.ResidentServiceImpl;
import dev.mintychochip.guilds.services.impl.ResourceServiceImpl;
import dev.mintychochip.guilds.services.impl.SpecializationServiceImpl;
import dev.mintychochip.guilds.services.impl.TechTreeServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildProjectServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildLevelServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildContractServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildToggleServiceImpl;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.territory.economy.GuildBankCapacity;
import dev.mintychochip.territory.economy.MintEconomyRail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manual composition root and lifecycle owner for the Guilds subsystem, hosted
 * directly by the single Guilds {@link JavaPlugin}. There is no second
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

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The mint economy rail. */
    private final MintEconomyRail mintEconomyRail;
    /** The mint guild bank service. */
    private volatile MintGuildBankService mintGuildBankService;
    /** The guild bank enrollment service. */
    private final GuildBankEnrollmentService guildBankEnrollmentService;
    /** The guild brigadier command. */
    private GuildBrigadierCommand guildBrigadierCommand;
    /** The guilds general command. */
    private GuildsGeneralBrigadierCommand guildsGeneralCommand;
    /** The guild bank villager listener. */
    private GuildBankVillagerListener guildBankVillagerListener;
    /** The config. */
    private FileConfiguration config;
    /** The enabled. */
    private boolean enabled;

    // Config
    /** The guilds config. */
    private final GuildsConfig guildsConfig;
    /** The database config. */
    private final DatabaseConfig databaseConfig;
    /** The guild level config loader. */
    private final GuildLevelConfigLoader guildLevelConfigLoader;
    /** The tech tree config loader. */
    private final TechTreeConfigLoader techTreeConfigLoader;

    // Database
    /** The database manager. */
    private final DatabaseManager databaseManager;

    // Services
    /** The resident service. */
    private final ResidentService residentService;
    /** The guild service. */
    private final GuildService guildService;
    /** The plot service. */
    private final PlotService plotService;
    /** The location service. */
    private final LocationService locationService;
    /** The guild toggle service. */
    private final GuildToggleService guildToggleService;
    /** The permission service. */
    private final PermissionService permissionService;
    /** The guild level service. */
    private final GuildLevelService guildLevelService;
    /** The guild contract service. */
    private final GuildContractService guildContractService;
    /** The resource service. */
    private final ResourceService resourceService;
    /** The tech tree service. */
    private final TechTreeService techTreeService;
    /** The guild project service. */
    private final GuildProjectService guildProjectService;
    /** The specialization service. */
    private final SpecializationService specializationService;
    /** The broadcast service. */
    private final BroadcastService broadcastService;
    /** The chat service. */
    private final ChatService chatService;
    /** The alliance service. */
    private final AllianceService allianceService;
    /** The quest service. */
    private final QuestService questService;

    // Governance (guilds as local governments, alliances as alliances)
    /** The governance source. */
    private final GuildsGovernanceSource governanceSource;

    // Plot types
    /** The plot type registry. */
    private final PlotTypeRegistry plotTypeRegistry;
    /** The plot type handler manager. */
    private final PlotTypeHandlerManager plotTypeHandlerManager;

    // GUI
    /** The tech tree gui. */
    private final TechTreeGUI techTreeGUI;

    // Commands
    /** The command registry. */
    private final BrigadierCommandRegistry commandRegistry;

    // Listeners
    /** The player movement listener. */
    private final PlayerMovementListener playerMovementListener;
    /** The guild toggle listener. */
    private final GuildToggleListener guildToggleListener;
    /** The guild public access listener. */
    private final GuildPublicAccessListener guildPublicAccessListener;
    /** The guild broadcast listener. */
    private final GuildBroadcastListener guildBroadcastListener;
    /** The guild chat listener. */
    private final GuildChatListener guildChatListener;
    /** The alliance listener. */
    private final AllianceListener allianceListener;

    // Hearthstone (deferred until BlockProtection is available)
    /** The hearthstone service. */
    private dev.mintychochip.guilds.services.GuildHearthstoneService hearthstoneService;
    /** The hearthstone listener. */
    private dev.mintychochip.guilds.listeners.GuildHearthstoneListener hearthstoneListener;

    /**
     * Creates a new guilds services instance.
     * @param plugin the plugin
     * @param database the database
     */
    public GuildsServices(JavaPlugin plugin, Database database) {
        this(plugin, database, null);
    }

    /**
     * Creates a new guilds services instance.
     * @param plugin the plugin
     * @param database the database
     * @param mintEconomyRail the mint economy rail
     */
    public GuildsServices(JavaPlugin plugin, Database database, MintEconomyRail mintEconomyRail) {
        this.plugin = plugin;
        this.mintEconomyRail = mintEconomyRail;
        // Guilds config file (namespaced away from the territory config.yml)
        saveDefaultConfig();
        reloadConfig();

        guildsConfig = new GuildsConfig(config);
        databaseConfig = new DatabaseConfig(plugin, database);
        guildLevelConfigLoader = new GuildLevelConfigLoader(config, plugin.getLogger());
        techTreeConfigLoader = new TechTreeConfigLoader(plugin);

        SchemaInitializer schemaInitializer = new SchemaInitializer(plugin.getLogger());
        databaseManager = new DatabaseManager(plugin.getLogger(), database.dataSource(), schemaInitializer);

        // Services. The GuildService <-> PermissionService <-> PlotService cycle is
        // broken deliberately: GuildServiceImpl is built without PermissionService
        // and receives it via setPermissionService once it exists.
        residentService = new ResidentServiceImpl(databaseManager,
                Logger.getLogger(ResidentServiceImpl.class.getName()));
        GuildServiceImpl guildImpl = new GuildServiceImpl(databaseManager,
                Logger.getLogger(GuildServiceImpl.class.getName()), residentService);
        guildService = guildImpl;
        PlotServiceImpl plotImpl = new PlotServiceImpl(databaseManager, guildService,
                Logger.getLogger(PlotServiceImpl.class.getName()));
        plotService = plotImpl;
        locationService = new LocationServiceImpl(plotService, guildService);
        guildToggleService = new GuildToggleServiceImpl(locationService);
        allianceService = new AllianceServiceImpl(databaseManager,
                Logger.getLogger(AllianceServiceImpl.class.getName()), guildService);
        permissionService = new PermissionServiceImpl(databaseManager,
                Logger.getLogger(PermissionServiceImpl.class.getName()), plotService, guildService,
                residentService, guildToggleService, locationService, allianceService);
        guildImpl.setPermissionService(permissionService);
        plotImpl.setPermissionService(permissionService);

        guildLevelService = new GuildLevelServiceImpl(plugin, databaseManager, guildService, guildLevelConfigLoader);
        guildContractService = new GuildContractServiceImpl(databaseManager, guildService);
        resourceService = new ResourceServiceImpl(plugin, databaseManager, guildService);
        techTreeService = new TechTreeServiceImpl(plugin, databaseManager, techTreeConfigLoader, guildService);
        guildProjectService = new GuildProjectServiceImpl(plugin, databaseManager, techTreeConfigLoader);
        specializationService = new SpecializationServiceImpl(plugin, databaseManager, guildService);
        broadcastService = new BroadcastServiceImpl(databaseManager,
                Logger.getLogger(BroadcastServiceImpl.class.getName()),
                guildService, residentService, permissionService);
        chatService = new ChatServiceImpl(plugin, guildService, residentService);
        questService = new QuestServiceImpl(plugin, databaseManager);
        guildBankEnrollmentService = new GuildBankEnrollmentServiceImpl(databaseManager, guildService, residentService,
                Logger.getLogger(GuildBankEnrollmentServiceImpl.class.getName()));

        // Governance: guilds as local governments, alliances as alliances
        governanceSource = new GuildsGovernanceSource(databaseManager, guildService, allianceService,
                Logger.getLogger(GuildsGovernanceSource.class.getName()));

        // Plot type registry
        plotTypeRegistry = new PlotTypeRegistryImpl(Logger.getLogger(PlotTypeRegistryImpl.class.getName()));
        plotTypeHandlerManager = new PlotTypeHandlerManager(plotTypeRegistry,
                Logger.getLogger(PlotTypeHandlerManager.class.getName()));

        // GUI
        techTreeGUI = new TechTreeGUI(plugin, techTreeService, guildProjectService, guildService, residentService);
        TechTreeBrigadierCommand techTreeCommand = new TechTreeBrigadierCommand(techTreeService,
                guildProjectService, guildService, residentService, techTreeGUI);
        // Commands are built after all core services exist.
        // Listeners
        playerMovementListener = new PlayerMovementListener(plugin, plotService, guildService,
                residentService, plotTypeHandlerManager, plotTypeRegistry,
                ((dev.mintychochip.guilds.GuildsPlugin) plugin).getRegistry());
        guildToggleListener = new GuildToggleListener(plugin, permissionService);
        guildPublicAccessListener = new GuildPublicAccessListener(plugin, permissionService, residentService);
        guildBroadcastListener = new GuildBroadcastListener(plugin, broadcastService, residentService,
                guildService, Logger.getLogger(GuildBroadcastListener.class.getName()));
        guildChatListener = new GuildChatListener(plugin, chatService, guildService, residentService);
        allianceListener = new AllianceListener(allianceService, guildService, residentService);

        // Hearthstone service/listener are deferred until BlockProtection is available.
        this.hearthstoneService = null;
        this.hearthstoneListener = null;

        GuildBrigadierCommand guildCommand = new GuildBrigadierCommand(plugin, residentService, guildService,
                plotService, permissionService, techTreeCommand, plotTypeRegistry, governanceSource);
        this.guildBrigadierCommand = guildCommand;
        this.guildBankVillagerListener = new GuildBankVillagerListener(plugin, guildService, residentService, null,
                config.getString("bank.villager-scoreboard-tag", "GUILD_BANK"));
        PlotBrigadierCommand plotCommand = new PlotBrigadierCommand(plugin, residentService, guildService,
                plotService, permissionService, plotTypeRegistry);
        this.guildsGeneralCommand = new GuildsGeneralBrigadierCommand(plugin,
                residentService, guildService, plotService, permissionService);
        GuildsGeneralBrigadierCommand guildsGeneralCommand = this.guildsGeneralCommand;
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
        AllianceBrigadierCommand allianceCommand = new AllianceBrigadierCommand(plugin, allianceService, guildService,
                residentService, governanceSource);
        SpecializationBrigadierCommand specializationCommand = new SpecializationBrigadierCommand(plugin,
                specializationService, guildService, residentService);
        QuestBrigadierCommand questCommand = new QuestBrigadierCommand(plugin, questService, guildService, residentService);
        TerritoryBrigadierCommand territoryCommand = new TerritoryBrigadierCommand(
                new TerritoryCommand(
                        (dev.mintychochip.guilds.GuildsPlugin) plugin));
        commandRegistry = new BrigadierCommandRegistry(plugin, guildCommand, plotCommand, guildsGeneralCommand,
                guildLevelCommand, mapCommand, permCommand, plotTypeCommand, guildBroadcastCommand, guildPermCommand,
                chatCommand, allianceCommand, specializationCommand, territoryCommand, questCommand);
    }

    /** Creates guild services with a lease already supplied by the trusted Mint integration. */
    public static GuildsServices withMintLease(JavaPlugin plugin, Database database,
                                               dev.mintychochip.mint.api.service.MintClientLease lease,
                                               dev.mintychochip.mint.api.id.CurrencyId currency, int scale) {
        if (lease == null) throw new IllegalArgumentException("Mint lease is required");
        return new GuildsServices(plugin, database,
                new MintEconomyRail(lease, currency, scale, plugin.getLogger()));
    }

    /**
     * Starts the guilds subsystem on the host plugin (config sync, commands, listeners).
     */
    public void enable() {
        initializeServices();
        registerCommands();
        registerListeners();
        enabled = true;
        plugin.getLogger().info("Guilds subsystem has been enabled successfully!");
    }

    /**
     * Stops the guilds subsystem.
     */
    public void disable() {
        enabled = false;
        plugin.getLogger().info("Guilds subsystem has been disabled.");
    }

    /**
     * Returns whether enabled.
     * @return the result
     */
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

    /** Saves the default config. */
    private void saveDefaultConfig() {
        File out = new File(plugin.getDataFolder(), GUILDS_CONFIG);
        if (!out.exists()) {
            plugin.saveResource(GUILDS_CONFIG, false);
        }
    }

    /** Performs the reload config operation. */
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

    /** Performs the initialize services operation. */
    private void initializeServices() {
        try {
            guildLevelConfigLoader.loadConfiguration();
            plugin.getLogger().info("Guild level configuration loaded successfully.");

            guildLevelService.syncConfigToDatabase();
            plugin.getLogger().info("Guild level data synchronized to database.");

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

    /** Performs the register commands operation. */
    private void registerCommands() {
        try {
            commandRegistry.registerCommands();
            plugin.getLogger().info("Guilds Brigadier commands registered successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register guilds Brigadier commands", e);
        }
    }

    /** Performs the register listeners operation. */
    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(guildChatListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(allianceListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(playerMovementListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(guildToggleListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(guildPublicAccessListener, plugin);
        if (guildBankVillagerListener != null) {
            plugin.getServer().getPluginManager().registerEvents(guildBankVillagerListener, plugin);
        }
        plugin.getServer().getPluginManager().registerEvents(guildBroadcastListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(techTreeGUI, plugin);

        if (hearthstoneListener != null) {
            plugin.getServer().getPluginManager().registerEvents(hearthstoneListener, plugin);
        }

        plugin.getLogger().info("Guilds event listeners registered.");
    }

    /**
     * Lazily construct and register the hearthstone teleport service/listener
     * once the canonical {@link BlockProtection} is available.
     */
    public void registerHearthstone(dev.mintychochip.territory.permission.BlockProtection blockProtection) {
        if (blockProtection == null || hearthstoneService != null) {
            return;
        }
        String matName = config.getString("hearthstone.item", "ENDER_PEARL");
        org.bukkit.Material material = org.bukkit.Material.getMaterial(
                matName == null ? "ENDER_PEARL" : matName.trim().toUpperCase(java.util.Locale.ROOT));
        if (material == null) material = org.bukkit.Material.ENDER_PEARL;
        long cooldown = config.getLong("hearthstone.cooldown-seconds", 30);
        hearthstoneService = new dev.mintychochip.guilds.services.impl.GuildHearthstoneServiceImpl(
                plugin, guildService, blockProtection, cooldown);
        hearthstoneListener = new dev.mintychochip.guilds.listeners.GuildHearthstoneListener(
                hearthstoneService, material);
    }

    /**
     * Returns the mint economy rail.
     * @return the result
     */
    public MintEconomyRail getMintEconomyRail() {
        return mintEconomyRail;
    }

    /**
     * Performs the bind mint transfer port operation.
     * @param mintTransferPort the mint transfer port
     * @return the result
     */
    public MintGuildBankService bindMintTransferPort(MintTransferPort mintTransferPort) {
        if (mintTransferPort == null) {
            throw new IllegalArgumentException("Mint transfer port is required");
        }
        GuildBankCapacity capacity = new GuildBankCapacity(
                config.getString("bank.capacity-per-level", null) == null
                        ? java.math.BigDecimal.valueOf(1000)
                        : new java.math.BigDecimal(config.getString("bank.capacity-per-level")),
                config.getInt("bank.capacity-scale", 2));
        MintGuildBankService service = new MintGuildBankService(mintTransferPort, guildBankEnrollmentService,
                guildId -> guildService.getGuildById(guildId).orElse(null), capacity);
        MintGuildBankService previous = this.mintGuildBankService;
        this.mintGuildBankService = service;
        if (guildBrigadierCommand != null) {
            guildBrigadierCommand.setMintGuildBankService(service);
        }
        if (guildBankVillagerListener != null) {
            guildBankVillagerListener.setBank(service);
        }
        if (previous != null && previous != service) {
            previous.close();
        }
        return service;
    }

    /**
     * Returns the mint guild bank service.
     * @return the result
     */
    public MintGuildBankService getMintGuildBankService() {
        return mintGuildBankService;
    }

    /**
     * Returns the guild bank enrollment service.
     * @return the result
     */
    public GuildBankEnrollmentService getGuildBankEnrollmentService() {
        return guildBankEnrollmentService;
    }

    /**
     * Returns the guild bank villager listener.
     * @return the result
     */
    public GuildBankVillagerListener getGuildBankVillagerListener() {
        return guildBankVillagerListener;
    }

    /**
     * Returns the guild brigadier command.
     * @return the result
     */
    public GuildBrigadierCommand getGuildBrigadierCommand() {
        return guildBrigadierCommand;
    }

    /**
     * Returns the guilds general command.
     * @return the result
     */
    public GuildsGeneralBrigadierCommand getGuildsGeneralCommand() {
        return guildsGeneralCommand;
    }


    /**
     * Returns the guilds config.
     * @return the result
     */
    public GuildsConfig getGuildsConfig() {
        return guildsConfig;
    }

    /**
     * Returns the database config.
     * @return the result
     */
    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    /**
     * Returns the guild level config loader.
     * @return the result
     */
    public GuildLevelConfigLoader getGuildLevelConfigLoader() {
        return guildLevelConfigLoader;
    }

    /**
     * Returns the tech tree config loader.
     * @return the result
     */
    public TechTreeConfigLoader getTechTreeConfigLoader() {
        return techTreeConfigLoader;
    }

    /**
     * Returns the database manager.
     * @return the result
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Returns the resident service.
     * @return the result
     */
    public ResidentService getResidentService() {
        return residentService;
    }

    /**
     * Returns the guild service.
     * @return the result
     */
    public GuildService getGuildService() {
        return guildService;
    }

    /**
     * Returns the plot service.
     * @return the result
     */
    public PlotService getPlotService() {
        return plotService;
    }

    /**
     * Returns the location service.
     * @return the result
     */
    public LocationService getLocationService() {
        return locationService;
    }

    /**
     * Returns the guild toggle service.
     * @return the result
     */
    public GuildToggleService getGuildToggleService() {
        return guildToggleService;
    }

    /**
     * Returns the permission service.
     * @return the result
     */
    public PermissionService getPermissionService() {
        return permissionService;
    }

    /**
     * Returns the guild level service.
     * @return the result
     */
    public GuildLevelService getGuildLevelService() {
        return guildLevelService;
    }

    /**
     * Returns the guild contract service.
     * @return the result
     */
    public GuildContractService getGuildContractService() {
        return guildContractService;
    }

    /**
     * Returns the resource service.
     * @return the result
     */
    public ResourceService getResourceService() {
        return resourceService;
    }

    /**
     * Returns the tech tree service.
     * @return the result
     */
    public TechTreeService getTechTreeService() {
        return techTreeService;
    }

    /**
     * Returns the guild project service.
     * @return the result
     */
    public GuildProjectService getGuildProjectService() {
        return guildProjectService;
    }

    /**
     * Returns the specialization service.
     * @return the result
     */
    public SpecializationService getSpecializationService() {
        return specializationService;
    }

    /**
     * Returns the broadcast service.
     * @return the result
     */
    public BroadcastService getBroadcastService() {
        return broadcastService;
    }

    /**
     * Returns the chat service.
     * @return the result
     */
    public ChatService getChatService() {
        return chatService;
    }

    /**
     * Returns the alliance service.
     * @return the result
     */
    public AllianceService getAllianceService() {
        return allianceService;
    }

    /**
     * Returns the governance source.
     * @return the result
     */
    public GuildsGovernanceSource getGovernanceSource() {
        return governanceSource;
    }

    /**
     * Returns the quest service.
     * @return the result
     */
    public QuestService getQuestService() {
        return questService;
    }

    /**
     * Let the guilds permission service apply government-form semantics to
     * territory chunks that have no plot rows (late-bound from the host
     * plugin, which owns the territory registry).
     */
    public void wireTerritoryRegistry(dev.mintychochip.territory.registry.TerritoryRegistry registry) {
        if (permissionService instanceof PermissionServiceImpl impl) {
            impl.setTerritoryRegistry(registry);
        }
    }

    /**
     * Returns the plot type registry.
     * @return the result
     */
    public PlotTypeRegistry getPlotTypeRegistry() {
        return plotTypeRegistry;
    }

    /**
     * Returns the plot type handler manager.
     * @return the result
     */
    public PlotTypeHandlerManager getPlotTypeHandlerManager() {
        return plotTypeHandlerManager;
    }

    /**
     * Returns the tech tree gui.
     * @return the result
     */
    public TechTreeGUI getTechTreeGUI() {
        return techTreeGUI;
    }

    /**
     * Returns the command registry.
     * @return the result
     */
    public BrigadierCommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    /**
     * Returns the player movement listener.
     * @return the result
     */
    public PlayerMovementListener getPlayerMovementListener() {
        return playerMovementListener;
    }

    /**
     * Returns the guild toggle listener.
     * @return the result
     */
    public GuildToggleListener getGuildToggleListener() {
        return guildToggleListener;
    }

    /**
     * Returns the guild public access listener.
     * @return the result
     */
    public GuildPublicAccessListener getGuildPublicAccessListener() {
        return guildPublicAccessListener;
    }

    /**
     * Returns the guild broadcast listener.
     * @return the result
     */
    public GuildBroadcastListener getGuildBroadcastListener() {
        return guildBroadcastListener;
    }

    /**
     * Returns the guild chat listener.
     * @return the result
     */
    public GuildChatListener getGuildChatListener() {
        return guildChatListener;
    }

    /**
     * Returns the alliance listener.
     * @return the result
     */
    public AllianceListener getAllianceListener() {
        return allianceListener;
    }
}

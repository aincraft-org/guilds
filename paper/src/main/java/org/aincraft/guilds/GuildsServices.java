package org.aincraft.guilds;

import org.aincraft.guilds.commands.BrigadierCommandRegistry;
import org.aincraft.guilds.commands.brigadier.ChatBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.MapBrigadierCommand;
import org.aincraft.guilds.commands.brigadier.AllianceBrigadierCommand;
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
import com.azoth.territory.persist.PostgresDatabase;
import org.aincraft.guilds.database.migration.SchemaInitializer;
import org.aincraft.guilds.gui.TechTreeGUI;
import org.aincraft.guilds.listeners.AllianceListener;
import org.aincraft.guilds.listeners.PlayerMovementListener;
import org.aincraft.guilds.listeners.GuildBroadcastListener;
import org.aincraft.guilds.listeners.GuildToggleListener;
import org.aincraft.guilds.listeners.GuildPublicAccessListener;
import org.aincraft.guilds.listeners.GuildChatListener;
import org.aincraft.guilds.listeners.GuildBankVillagerListener;
import org.aincraft.guilds.services.GuildBankEnrollmentService;
import org.aincraft.guilds.services.MintGuildBankService;
import org.aincraft.guilds.services.MintTransferPort;
import org.aincraft.guilds.services.impl.GuildBankEnrollmentServiceImpl;
import org.aincraft.guilds.plot.PlotTypeHandlerManager;
import org.aincraft.guilds.plot.PlotTypeRegistry;
import org.aincraft.guilds.plot.PlotTypeRegistryImpl;
import org.aincraft.guilds.services.BroadcastService;
import org.aincraft.guilds.services.ChatService;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.QuestService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.ResourceService;
import org.aincraft.guilds.services.SpecializationService;
import org.aincraft.guilds.services.TechTreeService;
import org.aincraft.guilds.services.GuildLevelService;
import org.aincraft.guilds.services.GuildContractService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.GuildToggleService;
import org.aincraft.guilds.services.impl.BroadcastServiceImpl;
import org.aincraft.guilds.services.impl.ChatServiceImpl;
import org.aincraft.guilds.services.impl.LocationServiceImpl;
import org.aincraft.guilds.services.impl.AllianceServiceImpl;
import org.aincraft.guilds.services.impl.PermissionServiceImpl;
import org.aincraft.guilds.services.impl.PlotServiceImpl;
import org.aincraft.guilds.services.impl.QuestServiceImpl;
import org.aincraft.guilds.services.impl.ResidentServiceImpl;
import org.aincraft.guilds.services.impl.ResourceServiceImpl;
import org.aincraft.guilds.services.impl.SpecializationServiceImpl;
import org.aincraft.guilds.services.impl.TechTreeServiceImpl;
import org.aincraft.guilds.services.impl.GuildLevelServiceImpl;
import org.aincraft.guilds.services.impl.GuildContractServiceImpl;
import org.aincraft.guilds.services.impl.GuildServiceImpl;
import org.aincraft.guilds.services.impl.GuildToggleServiceImpl;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.azoth.territory.economy.GuildBankCapacity;
import com.azoth.territory.economy.MintEconomyRail;

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
    private final MintEconomyRail mintEconomyRail;
    private volatile MintGuildBankService mintGuildBankService;
    private final GuildBankEnrollmentService guildBankEnrollmentService;
    private GuildBrigadierCommand guildBrigadierCommand;
    private GuildBankVillagerListener guildBankVillagerListener;
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
    private final GuildContractService guildContractService;
    private final ResourceService resourceService;
    private final TechTreeService techTreeService;
    private final SpecializationService specializationService;
    private final BroadcastService broadcastService;
    private final ChatService chatService;
    private final AllianceService allianceService;
    private final QuestService questService;

    // Governance (guilds as local governments, alliances as alliances)
    private final GuildsGovernanceSource governanceSource;

    // Plot types
    private final PlotTypeRegistry plotTypeRegistry;
    private final PlotTypeHandlerManager plotTypeHandlerManager;

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
    private final AllianceListener allianceListener;

    // Hearthstone (deferred until BlockProtection is available)
    private org.aincraft.guilds.services.GuildHearthstoneService hearthstoneService;
    private org.aincraft.guilds.listeners.GuildHearthstoneListener hearthstoneListener;

    public GuildsServices(JavaPlugin plugin, PostgresDatabase database) {
        this(plugin, database, null);
    }

    public GuildsServices(JavaPlugin plugin, PostgresDatabase database, MintEconomyRail mintEconomyRail) {
        this.plugin = plugin;
        this.mintEconomyRail = mintEconomyRail;
        // Guilds config file (namespaced away from the territory config.yml)
        saveDefaultConfig();
        reloadConfig();

        guildsConfig = new GuildsConfig(config);
        databaseConfig = new DatabaseConfig(plugin, database);
        guildLevelConfigLoader = new GuildLevelConfigLoader(config, plugin.getLogger());
        techTreeConfigLoader = new TechTreeConfigLoader(plugin);

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
        PlotServiceImpl plotImpl = new PlotServiceImpl(databaseManager, guildService,
                Logger.getLogger(PlotServiceImpl.class.getName()));
        plotService = plotImpl;
        locationService = new LocationServiceImpl(plotService, guildService);
        guildToggleService = new GuildToggleServiceImpl(locationService);
        allianceService = new AllianceServiceImpl(plugin, databaseManager,
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
        techTreeGUI = new TechTreeGUI(plugin, techTreeService, guildService, residentService);
        TechTreeBrigadierCommand techTreeCommand = new TechTreeBrigadierCommand(techTreeService,
                guildService, residentService, techTreeGUI);
        // Commands are built after all core services exist.
        // Listeners
        playerMovementListener = new PlayerMovementListener(plugin, plotService, guildService,
                residentService, plotTypeHandlerManager, plotTypeRegistry);
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
        AllianceBrigadierCommand allianceCommand = new AllianceBrigadierCommand(plugin, allianceService, guildService,
                residentService, governanceSource);
        SpecializationBrigadierCommand specializationCommand = new SpecializationBrigadierCommand(plugin,
                specializationService, guildService, residentService);
        QuestBrigadierCommand questCommand = new QuestBrigadierCommand(plugin, questService, guildService, residentService);

        commandRegistry = new BrigadierCommandRegistry(plugin, guildCommand, plotCommand, guildsGeneralCommand,
                guildLevelCommand, mapCommand, permCommand, plotTypeCommand, guildBroadcastCommand, guildPermCommand,
                techTreeCommand, chatCommand, allianceCommand, specializationCommand, questCommand);
    }

    /** Creates guild services with a lease already supplied by the trusted Mint integration. */
    public static GuildsServices withMintLease(JavaPlugin plugin, PostgresDatabase database,
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
    public void registerHearthstone(com.azoth.territory.permission.BlockProtection blockProtection) {
        if (blockProtection == null || hearthstoneService != null) {
            return;
        }
        String matName = config.getString("hearthstone.item", "ENDER_PEARL");
        org.bukkit.Material material = org.bukkit.Material.getMaterial(
                matName == null ? "ENDER_PEARL" : matName.trim().toUpperCase(java.util.Locale.ROOT));
        if (material == null) material = org.bukkit.Material.ENDER_PEARL;
        long cooldown = config.getLong("hearthstone.cooldown-seconds", 30);
        hearthstoneService = new org.aincraft.guilds.services.impl.GuildHearthstoneServiceImpl(
                plugin, guildService, blockProtection, cooldown);
        hearthstoneListener = new org.aincraft.guilds.listeners.GuildHearthstoneListener(
                hearthstoneService, material);
    }

    public MintEconomyRail getMintEconomyRail() {
        return mintEconomyRail;
    }

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

    public MintGuildBankService getMintGuildBankService() {
        return mintGuildBankService;
    }

    public GuildBankEnrollmentService getGuildBankEnrollmentService() {
        return guildBankEnrollmentService;
    }

    public GuildBankVillagerListener getGuildBankVillagerListener() {
        return guildBankVillagerListener;
    }

    public GuildBrigadierCommand getGuildBrigadierCommand() {
        return guildBrigadierCommand;
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

    public GuildContractService getGuildContractService() {
        return guildContractService;
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

    public AllianceService getAllianceService() {
        return allianceService;
    }

    public GuildsGovernanceSource getGovernanceSource() {
        return governanceSource;
    }

    public QuestService getQuestService() {
        return questService;
    }

    /**
     * Let the guilds permission service apply government-form semantics to
     * territory chunks that have no plot rows (late-bound from the host
     * plugin, which owns the territory registry).
     */
    public void wireTerritoryRegistry(com.azoth.territory.registry.TerritoryRegistry registry) {
        if (permissionService instanceof PermissionServiceImpl impl) {
            impl.setTerritoryRegistry(registry);
        }
    }

    public PlotTypeRegistry getPlotTypeRegistry() {
        return plotTypeRegistry;
    }

    public PlotTypeHandlerManager getPlotTypeHandlerManager() {
        return plotTypeHandlerManager;
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

    public AllianceListener getAllianceListener() {
        return allianceListener;
    }
}

package dev.mintychochip.territory;

import dev.mintychochip.territory.command.TerritoryCommand;
import dev.mintychochip.territory.economy.BukkitEconomyBridge;
import dev.mintychochip.territory.economy.ExpenseLedger;
import dev.mintychochip.territory.economy.EconomyBridge;
import dev.mintychochip.territory.economy.EconomyConfig;

import dev.mintychochip.territory.economy.PaymentRail;
import dev.mintychochip.territory.economy.SettlementResult;
import dev.mintychochip.territory.economy.SimulationTreasury;
import dev.mintychochip.territory.economy.TreasuryDebitResult;
import dev.mintychochip.territory.influence.InfluenceConfig;
import dev.mintychochip.territory.influence.InfluenceConfigLoader;
import dev.mintychochip.territory.influence.InfluenceEngine;
import dev.mintychochip.territory.influence.InfluenceListener;
import dev.mintychochip.territory.influence.InfluenceService;
import dev.mintychochip.territory.influence.InfluenceStatusFormatter;
import dev.mintychochip.territory.influence.InfluenceStatusTask;
import dev.mintychochip.territory.influence.PostgresInfluenceStore;
import dev.mintychochip.territory.listener.InteractionProtectionListener;
import dev.mintychochip.territory.listener.ProtectionListener;
import dev.mintychochip.territory.permission.BlockProtection;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.guilds.GovernanceSource;
import dev.mintychochip.guilds.Guild;
import dev.mintychochip.territory.persist.DatabaseSettings;
import dev.mintychochip.territory.persist.DatabaseSettingsLoader;
import dev.mintychochip.territory.persist.DatabaseFactory;
import dev.mintychochip.territory.persist.Database;
import dev.mintychochip.territory.persist.PostgresExpenseStore;
import dev.mintychochip.territory.persist.PostgresReconciliationStore;
import dev.mintychochip.territory.persist.PostgresTerritoryStore;
import dev.mintychochip.territory.persist.PostgresFacilityStore;
import dev.mintychochip.territory.persist.TerritoryJson;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.standing.PostgresStandingStore;
import dev.mintychochip.territory.standing.StandingConfig;
import dev.mintychochip.territory.standing.StandingConfigLoader;
import dev.mintychochip.territory.standing.StandingEngine;
import dev.mintychochip.territory.standing.StandingTier;
import dev.mintychochip.territory.persist.PostgresUpkeepStore;
import dev.mintychochip.territory.upkeep.UpkeepConfig;
import dev.mintychochip.territory.upkeep.UpkeepEngine;
import dev.mintychochip.territory.standing.HarvestBonusListener;
import dev.mintychochip.territory.standing.StandingListener;
import dev.mintychochip.territory.squaremap.TerritorySquaremapBridge;
import dev.mintychochip.territory.web.TerritoryWebServer;
import dev.mintychochip.territory.web.WebConfig;
import dev.mintychochip.territory.web.WebConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.guilds.GuildsServices;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import dev.mintychochip.mint.api.service.MintClientLease;
import dev.mintychochip.mint.api.service.MintClientReceiver;
import dev.mintychochip.mint.api.id.CurrencyId;
import dev.mintychochip.territory.economy.MintEconomyRail;
import dev.mintychochip.territory.economy.MintGuildTaxSettlement;
import org.aincraft.guilds.services.MintGuildBankService;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public final class AzothTerritoryPlugin extends JavaPlugin {
    private volatile MintClientLease trustedMintLease;
    private volatile MintEconomyRail mintEconomyRail;
    private MintClientReceiver mintClientReceiver;

    /** Mint calls this through its documented MintClientReceiver service binding. */
    public void bindMintClient(MintClientLease lease) {
        this.trustedMintLease = java.util.Objects.requireNonNull(lease, "lease");
        if (getConfig().getString("economy.mode", "SIMULATION").equalsIgnoreCase("MINT")
                && economyBridge != null) {
            EconomyConfig config = EconomyConfig.fromBukkit(getConfig());
            this.mintEconomyRail = new MintEconomyRail(lease, CurrencyId.parse(config.mintCurrency()),
                    config.mintScale(), getLogger());
            MintGuildBankService bank = guilds == null ? null : guilds.bindMintTransferPort(mintEconomyRail);
            if (bank != null) {
                economyBridge.setAsyncSettlement(new MintGuildTaxSettlement(bank));
            }
            getLogger().info("Bound Mint lease; asynchronous territory taxes now credit guild accounts");
        }
    }
    private TerritoryRegistry registry;
    private FacilityRegistry facilities;
    private PostgresFacilityStore facilityStore;
    private dev.mintychochip.territory.building.BuildingCommand buildingCommand;
    private dev.mintychochip.territory.building.FacilityMutationService facilityMutations;
    private dev.mintychochip.territory.building.WaystoneTravelService waystoneTravelService;
    private PostgresTerritoryStore store;
    private Database database;
    private GovernanceRegistry governance;
    private BlockProtection blockProtection;
    private TerritoryWebServer webServer;
    private WebConfig webConfig;
    private EconomyBridge economyBridge;
    private BukkitEconomyBridge bukkitEconomyBridge;

    private PostgresReconciliationStore reconciliationStore;
    private PostgresExpenseStore expenseStore;
    private ExpenseLedger expenseLedger;
    private boolean expenseLedgerLoaded;
    private GuildsServices guilds;
    private UpkeepEngine upkeepEngine;
    private PostgresUpkeepStore upkeepStore;
    private BukkitTask upkeepTask;
    private InfluenceEngine influenceEngine;
    private PostgresInfluenceStore influenceStore;
    private TerritorySquaremapBridge squaremapBridge;
    private PostgresStandingStore standingStore;
    private StandingEngine standingEngine;
    private BukkitTask influenceStatusTask;
    private dev.mintychochip.territory.invasion.InvasionRuntime invasionRuntime;
    private BukkitTask invasionBossBarTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mintClientReceiver = new MintClientReceiver() {
            @Override public void bindMintClient(MintClientLease lease) {
                AzothTerritoryPlugin.this.bindMintClient(lease);
            }
        };
        getServer().getServicesManager().register(
                MintClientReceiver.class, mintClientReceiver, this, ServicePriority.Normal);
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create data folder: " + getDataFolder());
        }

        java.io.File bonusesFile = new java.io.File(getDataFolder(), "bonuses.json");
        if (!bonusesFile.exists()) {
            saveResource("bonuses.json", false);
        }
        this.registry = new TerritoryRegistry();
        try {
            DatabaseSettings settings = DatabaseSettingsLoader.fromValues(getConfig().getValues(true));
            this.database = DatabaseFactory.open(settings);
            this.database.initializeSchema();
            this.store = new PostgresTerritoryStore(database);
            this.reconciliationStore = new PostgresReconciliationStore(database);
            this.store.loadInto(registry);
            this.facilities = new FacilityRegistry(registry);
            this.facilityStore = new PostgresFacilityStore(database);
            this.facilityStore.loadInto(facilities);
            getLogger().info("Loaded " + registry.size() + " territor(y/ies) from " + settings.type());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE,
                    "PostgreSQL persistence is mandatory; plugin startup aborted", e);
            if (database != null) {
                database.close();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Guilds subsystem is the governance source (guilds as local governments,
        // nations as alliances). Constructed before the governance registry so
        // permission checks resolve through real guild data; the web server and
        // commands start later in enableGuildsSubsystem().
        constructGuildsSubsystem();
        if (this.guilds != null) {
            // Let the guilds plot gate apply government-form semantics to
            // territory chunks that have no plot rows.
            this.guilds.wireTerritoryRegistry(registry);
        }

        GovernanceSource source = this.guilds != null
                ? this.guilds.getGovernanceSource()
                : GovernanceSource.none();
        this.governance = new GovernanceRegistry(registry, source);

        try {
            EconomyConfig economyConfig = EconomyConfig.fromBukkit(getConfig());
            boolean simulation = economyConfig.mode() == EconomyConfig.Mode.SIMULATION;
            if (economyConfig.mode() == EconomyConfig.Mode.MINT) {
                String binding = economyConfig.mintClientBinding();
                if (binding == null || binding.isBlank()) {
                    throw new IllegalStateException("economy.mode=MINT requires economy.mint.client-binding");
                }
                getLogger().warning("Mint mode configured but no trusted Mint lease resolver is registered for binding '"
                        + binding + "'; Mint economy remains unavailable");
            }
            this.expenseStore = new PostgresExpenseStore(database);
            this.expenseLedger = new ExpenseLedger(entries -> {
                try {
                    expenseStore.save(entries);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to persist expense journal", e);
                }
            });
            this.expenseLedger.load(expenseStore.load());
            this.expenseLedgerLoaded = true;
            PaymentRail rail;
            if (economyConfig.mode() == EconomyConfig.Mode.MINT) {
                rail = new UnavailableRail();
                getLogger().info("Mint mode awaiting the configured MintClientReceiver lease");
            } else {
                rail = new SimulationTreasury();
                getLogger().info("Economy in SIMULATION mode — non-monetary ledger only, no player charges");
            }
            this.economyBridge = new EconomyBridge(
                    registry, governance, dev.mintychochip.territory.decree.GoodsCatalog.defaultCatalog(),
                    rail, simulation, expenseLedger);
            this.bukkitEconomyBridge = new BukkitEconomyBridge(economyBridge, facilities);
            try {
                economyBridge.loadUnresolvedTransactions(reconciliationStore.load());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to load reconciliation queue", e);
            }
            economyBridge.setUnresolvedTransactionSink(entries -> {
                if (!entries.isEmpty()) {
                    getLogger().log(Level.SEVERE, "Settlement reconciliation required; "
                            + entries.size() + " unresolved transaction(s) persisted to PostgreSQL");
                }
                try {
                    reconciliationStore.save(entries);
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "Failed to persist reconciliation queue", e);
                }
            });
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to wire economy — settlement disabled", e);
            this.economyBridge = null;
            this.bukkitEconomyBridge = null;
        }

        this.blockProtection = new BlockProtection(governance);
        getServer().getPluginManager().registerEvents(
                new InteractionProtectionListener(blockProtection), this);
        if (this.guilds != null) {
            this.guilds.registerHearthstone(blockProtection);
        }
        getLogger().info(
                "Registered territory protection listeners "
                        + "(break/place/fire/explosions/mob-spawn/entity-grief/interaction/pvp/teleport)");
        startBuildings();

        // squaremap integration: render territory/zone/influence boundaries as map layers.
        // Self-degrading when squaremap is absent; must start after registry load.
        this.squaremapBridge = new TerritorySquaremapBridge(
                this,
                registry,
                () -> Optional.ofNullable(influenceEngine)
                        .map(engine -> (InfluenceService) engine));
        this.squaremapBridge.start();

        // Standing engine (constructed before influence so the influence hook
        // can read development tiers; listeners + flush timer wired in Task 6).
        StandingConfig standingConfig = StandingConfigLoader.load(
                        new java.io.File(getDataFolder(), "bonuses.json").toPath())
                .orElse(StandingConfig.defaults());
        this.standingStore = new PostgresStandingStore(database);
        this.standingEngine = new StandingEngine(
                governance, standingConfig, standingStore, getLogger());
        this.standingEngine.recover(System.currentTimeMillis());
        getServer().getPluginManager().registerEvents(
                new StandingListener(governance, standingEngine), this);
        getServer().getPluginManager().registerEvents(
                new HarvestBonusListener(governance, standingEngine), this);
        long standingFlushTicks = Math.max(1, 60L * 20L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            try {
                standingEngine.flush();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush standing state", e);
            }
        }, standingFlushTicks, standingFlushTicks);
        getLogger().info("Territory standing + harvest bonuses enabled");
        startUpkeep();

        // Territory influence race (accrual → declare → countdown flip).
        InfluenceConfig influenceConfig = InfluenceConfigLoader.fromBukkit(getConfig());
        if (influenceConfig.enabled()) {
            try {
                this.influenceStore = new PostgresInfluenceStore(database);
                this.influenceEngine = new InfluenceEngine(
                        governance, influenceConfig, influenceStore,
                        (territoryId, newOwnerGuildId) -> saveTerritories(),
                        getLogger(),
                        standingEngine);
                broadcastFlips(influenceEngine.recover(System.currentTimeMillis()));
                getServer().getPluginManager().registerEvents(
                        new InfluenceListener(governance, influenceEngine), this);
                long flushTicks = Math.max(1, influenceConfig.flushSeconds() * 20L);
                getServer().getScheduler().runTaskTimer(this, () -> {
                    try {
                        broadcastFlips(influenceEngine.tickFlips(System.currentTimeMillis()));
                        influenceEngine.flush();
                    } catch (IOException e) {
                        getLogger().log(Level.SEVERE, "Failed to flush influence state", e);
                    }
                }, flushTicks, flushTicks);
                this.influenceStatusTask = getServer().getScheduler().runTaskTimer(
                        this,
                        new InfluenceStatusTask(
                                registry, influenceEngine, new InfluenceStatusFormatter()),
                        1L,
                        20L);
                getLogger().info("Territory influence race enabled (cap " + influenceConfig.cap() + ")");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to start influence system — disabled", e);
                this.influenceEngine = null;
                this.influenceStore = null;
            }
        } else {
            getLogger().info("Territory influence race disabled (influence.enabled=false)");
        }

        TerritoryCommand cmd = new TerritoryCommand(this);
        var pluginCommand = getCommand("territory");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(cmd);
            pluginCommand.setTabCompleter(cmd);
        } else {
            getLogger().warning("Command 'territory' not defined in plugin.yml");
        }

        startWebIfEnabled();
        enableGuildsSubsystem();
        wireInvasions();
    }
    @Override
    public void onDisable() {
        if (mintClientReceiver != null) {
            getServer().getServicesManager().unregister(MintClientReceiver.class, mintClientReceiver);
            mintClientReceiver = null;
        }
        stopInfluenceStatus();
        stopWeb();
        stopSquaremap();
        if (waystoneTravelService != null) {
            waystoneTravelService.stop();
            waystoneTravelService = null;
        }
        if (standingEngine != null) {
            try {
                standingEngine.flush();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush standing state on disable", e);
            }
        }
        if (influenceEngine != null) {
            try {
                influenceEngine.flush();
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush influence state on disable", e);
            }
        }
        if (invasionBossBarTask != null) {
            invasionBossBarTask.cancel();
            invasionBossBarTask = null;
        }
        if (invasionRuntime != null) {
            invasionRuntime.disable(System.currentTimeMillis());
            invasionRuntime = null;
        }
        disableGuildsSubsystem();
        if (expenseStore != null && expenseLedger != null && expenseLedgerLoaded) {
            try {
                expenseStore.save(expenseLedger.entries());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush expense journal on disable", e);
            }
        }
        if (facilityStore != null && facilities != null) {
            try {
                facilityStore.save(facilities.list());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush facility state on disable", e);
            }
        }
        if (reconciliationStore != null && economyBridge != null) {
            try {
                reconciliationStore.save(economyBridge.unresolvedTransactions());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save reconciliation queue", e);
            }
        }
        if (store != null && registry != null) {
            try {
                store.save(registry);
                getLogger().info("Saved " + registry.size() + " territor(y/ies)");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save territories", e);
            }
        }
        if (database != null) {
            database.close();
        }
    }
    private void startUpkeep() {
        if (!getConfig().getBoolean("upkeep.enabled", true)) {
            getLogger().info("Territory upkeep disabled (upkeep.enabled=false)");
            return;
        }
        if (economyBridge == null) {
            getLogger().warning("Territory upkeep disabled because the economy bridge is unavailable");
            return;
        }
        UpkeepConfig defaults = UpkeepConfig.defaults();
        try {
            long intervalDays = getConfig().getLong("upkeep.interval-days", 7L);
            long graceDays = getConfig().getLong("upkeep.grace-days", 2L);
            long checkSeconds = getConfig().getLong("upkeep.check-seconds", 60L);
            if (checkSeconds <= 0L) {
                throw new IllegalArgumentException("upkeep.check-seconds must be positive");
            }
            UpkeepConfig config = new UpkeepConfig(
                    getConfig().getDouble("upkeep.base-amount", defaults.baseAmount()),
                    getConfig().getDouble("upkeep.chunk-amount", defaults.chunkAmount()),
                    getConfig().getDouble("upkeep.facility-amount", defaults.facilityAmount()),
                    getConfig().getDouble(
                            "upkeep.development-level-amount", defaults.developmentLevelAmount()),
                    daysToMillis(intervalDays),
                    daysToMillis(graceDays));
            this.upkeepStore = new PostgresUpkeepStore(database);
            this.upkeepEngine = new UpkeepEngine(
                    registry, economyBridge, facilities, config, upkeepStore, this::developmentLevelFor);
            long now = System.currentTimeMillis();
            upkeepEngine.recover(now);
            long checkTicks = Math.max(1L, Math.multiplyExact(checkSeconds, 20L));
            this.upkeepTask = getServer().getScheduler().runTaskTimer(
                    this, this::tickUpkeep, checkTicks, checkTicks);
            getLogger().info("Territory upkeep enabled (interval " + intervalDays + "d, grace "
                    + graceDays + "d)");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start territory upkeep — disabled", e);
            this.upkeepEngine = null;
            this.upkeepStore = null;
            this.upkeepTask = null;
        }
    }

    private void tickUpkeep() {
        if (upkeepEngine == null) {
            return;
        }
        try {
            upkeepEngine.tick(System.currentTimeMillis());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to persist territory upkeep state", e);
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Territory upkeep tick failed", e);
        }
    }

    private void stopUpkeep() {
        if (upkeepTask != null) {
            upkeepTask.cancel();
            upkeepTask = null;
        }
        if (upkeepStore != null && upkeepEngine != null) {
            try {
                upkeepStore.save(upkeepEngine.all());
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to flush territory upkeep state on disable", e);
            }
        }
    }

    private void stopInfluenceStatus() {
        if (influenceStatusTask != null) {
            influenceStatusTask.cancel();
            influenceStatusTask = null;
        }
    }

    private int developmentLevelFor(String territoryId) {
        if (standingEngine == null) {
            return 0;
        }
        return registry.get(territoryId)
                .flatMap(territory -> territory.governedByGuildId()
                        .flatMap(guildId -> standingEngine.tierFor(territoryId, guildId)))
                .map(StandingTier::level)
                .orElse(0);
    }

    private static long daysToMillis(long days) {
        if (days <= 0L) {
            throw new IllegalArgumentException("upkeep day values must be positive");
        }
        return Math.multiplyExact(TimeUnit.DAYS.toMillis(1), days);
    }


    private static final class UnavailableRail implements PaymentRail {
        @Override
        public SettlementResult settle(java.util.UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.PROVIDER_UNAVAILABLE);
        }

        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            return new TreasuryDebitResult(dev.mintychochip.territory.economy.TreasuryDebitStatus.PROVIDER_UNAVAILABLE);
        }

        @Override
        public boolean available() {
            return false;
        }
    }

    private void startWebIfEnabled() {
        try {
            this.webConfig = WebConfigLoader.fromValues(getConfig().getValues(true), getDataFolder().toPath());
            if (!webConfig.enabled()) {
                getLogger().info("Territory web submodule disabled (web.enabled=false)");
                return;
            }
            this.webServer = new TerritoryWebServer(
                    webConfig,
                    registry,
                    new dev.mintychochip.territory.persist.TerritoryJson(),
                    store,
                    () -> java.util.Optional.ofNullable(influenceEngine),
                    getLogger()
            );
            webServer.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start territory web submodule", e);
            webServer = null;
        }
    }

    private void stopWeb() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
    }

    private void stopSquaremap() {
        if (squaremapBridge != null) {
            squaremapBridge.stop();
            squaremapBridge = null;
        }
    }

    /**
     * Construct the guilds subsystem (config, database, services). This is
     * early — before the governance registry — so governance resolves through
     * real guild data. Failure is non-fatal: governance falls back to
     * territory-local government via {@link GovernanceSource#none()}.
     */
    private void constructGuildsSubsystem() {
        try {
            var economy = EconomyConfig.fromBukkit(getConfig());
            this.guilds = new GuildsServices(this, database);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE,
                    "Failed to construct guilds subsystem — governance falls back to territory-local", e);
            this.guilds = null;
        }
    }

    private void enableGuildsSubsystem() {
        if (this.guilds == null) {
            getLogger().warning("Guilds subsystem unavailable — skipping enable");
            return;
        }
        try {
            this.guilds.enable();
            if (guilds.isEnabled()) {
                getLogger().info("Guilds subsystem started");
            } else {
                getLogger().warning("Guilds subsystem did not fully enable — see errors above");
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start guilds subsystem", e);
        }
    }

    private void disableGuildsSubsystem() {
        if (guilds != null) {
            try {
                guilds.disable();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to stop guilds subsystem", e);
            }
            guilds = null;
        }
    }

    private void wireInvasions() {
        try {
            var loaded = dev.mintychochip.territory.invasion.InvasionConfigLoader.fromBukkit(getConfig());
            if (!loaded.enabled() || guilds == null) return;
            var invasionConfig = loaded.config();
            var invasionStore = new dev.mintychochip.territory.invasion.PostgresInvasionStore(database);
            var engine = new dev.mintychochip.territory.invasion.InvasionEngine(invasionConfig, invasionStore);
            var resolver = new dev.mintychochip.territory.invasion.GuildInvasionTargetResolver(guilds.getGuildService(), guilds.getPlotService());
            var spawner = new dev.mintychochip.territory.invasion.InvasionMobSpawner(this);
            var bars = new dev.mintychochip.territory.invasion.InvasionBossBars(loaded.nearbyRadius());
            this.invasionRuntime = new dev.mintychochip.territory.invasion.InvasionRuntime(
                    this, engine, resolver, spawner, bars, invasionConfig,
                    loaded.spawnRadius(), loaded.spawnAttempts(), loaded.waveDelayTicks(),
                    (location, guildId) -> guilds.getPlotService().getGuildBlock(location.getChunk().getX(), location.getChunk().getZ(), location.getWorld().getName())
                            .map(block -> block.getGuildId().equals(guildId))
                            .orElse(false),
                    guildId -> guilds.getGuildService().getGuildById(guildId)
                            .map(guild -> java.util.Set.copyOf(guild.getResidents())).orElse(java.util.Set.of()));
            invasionRuntime.recover(System.currentTimeMillis());
            var invasionListener = new dev.mintychochip.territory.invasion.InvasionListener(
                    invasionRuntime, engine, guilds.getPlotService(), loaded.materials());
            getServer().getPluginManager().registerEvents(invasionListener, this);
            getServer().getPluginManager().registerEvents(
                    new ProtectionListener(blockProtection, invasionListener::bypassesProtection), this);
            this.invasionBossBarTask = getServer().getScheduler().runTaskTimer(
                    this, invasionRuntime::reconcileBossBars, 20L, 20L);
            getLogger().info("Guild invasion lifecycle enabled");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to wire invasion lifecycle — disabled", e);
            invasionRuntime = null;
            if (invasionBossBarTask != null) {
                invasionBossBarTask.cancel();
                invasionBossBarTask = null;
            }
        }
    }

    public dev.mintychochip.territory.invasion.InvasionRuntime getInvasionRuntime() {
        return invasionRuntime;
    }
    /**
     * Guilds subsystem hosted by this plugin (null if enable failed).
     */
    public GuildsServices getGuilds() {
        return guilds;
    }

    public InfluenceEngine getInfluenceEngine() {
        return influenceEngine;
    }

    public StandingEngine getStandingEngine() {
        return standingEngine;
    }

    public UpkeepEngine getUpkeepEngine() {
        return upkeepEngine;
    }

    private void broadcastFlips(List<InfluenceEngine.TerritoryFlip> flips) {
        for (InfluenceEngine.TerritoryFlip flip : flips) {
            String oldName = resolveGuildName(flip.oldOwnerGuildId());
            String newName = resolveGuildName(flip.newOwnerGuildId());
            getServer().broadcast(Component.text(
                    "The territory '" + flip.territoryId() + "' has been taken over by "
                            + newName + " (formerly " + oldName + ")!", NamedTextColor.GOLD));
            getLogger().info("Territory " + flip.territoryId() + " flipped "
                    + oldName + " -> " + newName);
        }
    }

    private String resolveGuildName(String guildId) {
        if (governance != null && guildId != null) {
            return governance.source().guild(guildId).map(Guild::name).orElse(guildId);
        }
        return guildId;
    }

    public String resolveGuildNameFor(String guildId) {
        return resolveGuildName(guildId);
    }

    public TerritoryRegistry getRegistry() {
        return registry;
    }

    public FacilityRegistry getFacilities() {
        return facilities;
    }

    public dev.mintychochip.territory.building.BuildingCommand getBuildingCommand() {
        return buildingCommand;
    }

    public dev.mintychochip.territory.building.FacilityMutationService getFacilityMutations() {
        return facilityMutations;
    }

    public dev.mintychochip.territory.building.WaystoneTravelService getWaystoneTravelService() {
        return waystoneTravelService;
    }

    public PostgresTerritoryStore getStore() {
        return store;
    }

    public GovernanceRegistry getGovernance() {
        return governance;
    }

    public BlockProtection getBlockProtection() {
        return blockProtection;
    }

    public TerritoryWebServer getWebServer() {
        return webServer;
    }

    public WebConfig getWebConfig() {
        return webConfig;
    }
    private void startBuildings() {
        if (guilds == null) {
            getLogger().warning("Territory buildings unavailable because guilds failed to start");
            return;
        }
        try {
            var config = dev.mintychochip.territory.building.BuildingConfigLoader.from(getConfig());
            this.facilityMutations = new dev.mintychochip.territory.building.FacilityMutationService(
                    facilities, facilityStore);
            var authorization = new dev.mintychochip.territory.building.BuildingAuthorization(
                    guilds.getGuildService(), guilds.getPermissionService());
            var anchors = new dev.mintychochip.territory.building.FacilityAnchorValidator(
                    getServer(), registry, facilities, config);
            var sessions = new dev.mintychochip.territory.building.BuildingPlacementSessions(
                    config.placementTimeoutMillis());
            var selections = new dev.mintychochip.territory.building.WaystoneSelections(
                    config.placementTimeoutMillis());
            var access = new dev.mintychochip.territory.building.WaystoneAccess(
                    facilities, registry, anchors, authorization);
            this.waystoneTravelService = new dev.mintychochip.territory.building.WaystoneTravelService(
                    this, facilities, anchors, access,
                    new dev.mintychochip.territory.building.SafeLandingResolver(getServer()),
                    blockProtection, config);
            this.buildingCommand = new dev.mintychochip.territory.building.BuildingCommand(
                    sessions, facilities, registry, anchors, authorization,
                    facilityMutations, config, selections, waystoneTravelService);
            getServer().getPluginManager().registerEvents(
                    new dev.mintychochip.territory.building.BuildingListener(
                            sessions, config, registry, facilities, authorization,
                            facilityMutations, anchors, access, selections,
                            getServer().getPluginManager()), this);
            getServer().getPluginManager().registerEvents(
                    new dev.mintychochip.territory.building.WaystoneTravelListener(waystoneTravelService),
                    this);
            getLogger().info("Territory anchor buildings enabled");
        } catch (RuntimeException e) {
            this.buildingCommand = null;
            this.facilityMutations = null;
            this.waystoneTravelService = null;
            getLogger().log(Level.SEVERE, "Failed to start territory buildings — disabled", e);
        }
    }


    public EconomyBridge getEconomyBridge() {
        return economyBridge;
    }

    public BukkitEconomyBridge getBukkitEconomyBridge() {
        return bukkitEconomyBridge;
    }

    /**
     * Reload territories from PostgreSQL.
     */
    public void reloadTerritories() throws IOException {
        store.loadInto(registry);
    }

    /**
     * Persist current registry to PostgreSQL.
     */
    public void saveTerritories() throws IOException {
        store.save(registry);
    }

    /**
     * Reload plugin config and restart the web submodule.
     */
    public void reloadWeb() throws IOException {
        reloadConfig();
        stopWeb();
        startWebIfEnabled();
    }
}

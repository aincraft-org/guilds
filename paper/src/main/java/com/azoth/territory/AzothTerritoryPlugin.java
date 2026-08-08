package com.azoth.territory;

import com.azoth.territory.command.TerritoryCommand;
import com.azoth.territory.economy.BukkitEconomyBridge;
import com.azoth.territory.economy.EconomyBridge;
import com.azoth.territory.economy.EconomyConfig;
import com.azoth.territory.economy.PaymentRail;
import com.azoth.territory.economy.SettlementResult;
import com.azoth.territory.economy.SimulationTreasury;
import com.azoth.territory.economy.TreasuryDebitResult;
import com.azoth.territory.economy.VaultTreasury;
import com.azoth.territory.influence.InfluenceConfig;
import com.azoth.territory.influence.InfluenceConfigLoader;
import com.azoth.territory.influence.InfluenceEngine;
import com.azoth.territory.influence.InfluenceListener;
import com.azoth.territory.influence.PostgresInfluenceStore;
import com.azoth.territory.listener.InteractionProtectionListener;
import com.azoth.territory.listener.ProtectionListener;
import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.permission.GovernanceSource;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.persist.DatabaseSettings;
import com.azoth.territory.persist.DatabaseSettingsLoader;
import com.azoth.territory.persist.PostgresDatabase;
import com.azoth.territory.persist.PostgresReconciliationStore;
import com.azoth.territory.persist.PostgresTerritoryStore;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.registry.TerritoryRegistry;
import com.azoth.territory.standing.PostgresStandingStore;
import com.azoth.territory.standing.StandingConfig;
import com.azoth.territory.standing.StandingConfigLoader;
import com.azoth.territory.standing.StandingEngine;
import com.azoth.territory.squaremap.TerritorySquaremapBridge;
import com.azoth.territory.web.TerritoryWebServer;
import com.azoth.territory.web.WebConfig;
import com.azoth.territory.web.WebConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.guilds.GuildsServices;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public final class AzothTerritoryPlugin extends JavaPlugin {
    private TerritoryRegistry registry;
    private PostgresDatabase database;
    private PostgresTerritoryStore store;
    private GovernanceRegistry governance;
    private BlockProtection blockProtection;
    private TerritoryWebServer webServer;
    private WebConfig webConfig;
    private EconomyBridge economyBridge;
    private BukkitEconomyBridge bukkitEconomyBridge;
    private PostgresReconciliationStore reconciliationStore;
    private GuildsServices guilds;
    private InfluenceEngine influenceEngine;
    private PostgresInfluenceStore influenceStore;
    private StandingEngine standingEngine;
    private PostgresStandingStore standingStore;
    private TerritorySquaremapBridge squaremapBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create data folder: " + getDataFolder());
        }

        this.registry = new TerritoryRegistry();
        try {
            DatabaseSettings settings = DatabaseSettingsLoader.fromValues(getConfig().getValues(true));
            this.database = new PostgresDatabase(settings);
            this.database.initializeSchema();
            this.store = new PostgresTerritoryStore(database);
            this.reconciliationStore = new PostgresReconciliationStore(database);
            this.store.loadInto(registry);
            getLogger().info("Loaded " + registry.size() + " territor(y/ies) from PostgreSQL");
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
            PaymentRail rail;
            if (simulation) {
                rail = new SimulationTreasury();
                getLogger().info("Economy in SIMULATION mode — non-monetary ledger only, no player charges");
            } else if (getServer().getPluginManager().getPlugin("Vault") == null) {
                rail = new UnavailableRail();
                getLogger().warning("Vault not found — settlement returns VAULT_UNAVAILABLE");
            } else {
                net.milkbowl.vault.economy.Economy vaultEconomy = resolveVaultEconomy();
                if (vaultEconomy == null) {
                    rail = new UnavailableRail();
                    getLogger().warning("Vault economy provider not found — settlement returns VAULT_UNAVAILABLE");
                } else {
                    VaultTreasury vaultTreasury = new VaultTreasury(vaultEconomy, Bukkit::getOfflinePlayer);
                    int provisioningFailures = vaultTreasury.provisionTerritories(
                            registry.list().stream().map(territory -> territory.id()).toList());
                    rail = vaultTreasury;
                    if (provisioningFailures > 0) {
                        getLogger().warning("Could not provision " + provisioningFailures
                                + " territory treasury bank(s); affected sales return VAULT_UNAVAILABLE");
                    } else {
                        getLogger().info("Economy wired to Vault banks (territory treasury per settlement)");
                    }
                }
            }
            this.economyBridge = new EconomyBridge(
                    registry, governance, com.azoth.territory.decree.GoodsCatalog.defaultCatalog(), rail, simulation);
            this.bukkitEconomyBridge = new BukkitEconomyBridge(economyBridge);
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
                new ProtectionListener(blockProtection), this);
        getServer().getPluginManager().registerEvents(
                new InteractionProtectionListener(blockProtection), this);
        if (this.guilds != null) {
            this.guilds.registerHearthstone(blockProtection);
        }
        getLogger().info(
                "Registered territory protection listeners "
                        + "(break/place/fire/explosions/mob-spawn/entity-grief/interaction/pvp/teleport)");

        // squaremap integration: render territory/zone boundaries as map layers.
        // Self-degrading when squaremap is absent; must start after registry load.
        this.squaremapBridge = new TerritorySquaremapBridge(this, registry);
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
    }

    @Override
    public void onDisable() {
        disableGuildsSubsystem();
        stopSquaremap();
        stopWeb();
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

    private net.milkbowl.vault.economy.Economy resolveVaultEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        var registration = getServer().getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private static final class UnavailableRail implements PaymentRail {
        @Override
        public SettlementResult settle(java.util.UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
        }

        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            return new TreasuryDebitResult(com.azoth.territory.economy.TreasuryDebitStatus.VAULT_UNAVAILABLE);
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
                    new TerritoryJson(),
                    store,
                    () -> Optional.ofNullable(influenceEngine),
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
            return governance.source().guild(guildId).map(GuildBody::name).orElse(guildId);
        }
        return guildId;
    }

    public String resolveGuildNameFor(String guildId) {
        return resolveGuildName(guildId);
    }

    public TerritoryRegistry getRegistry() {
        return registry;
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

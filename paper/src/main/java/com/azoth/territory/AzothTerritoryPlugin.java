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
import com.azoth.territory.influence.InfluenceStore;
import com.azoth.territory.listener.InteractionProtectionListener;
import com.azoth.territory.listener.ProtectionListener;
import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.permission.GovernanceSource;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.persist.DatabaseSettings;
import com.azoth.territory.persist.DatabaseSettingsLoader;
import com.azoth.territory.persist.PostgresTerritoryRepository;
import com.azoth.territory.persist.ReconciliationStore;
import com.azoth.territory.persist.TerritoryJson;
import com.azoth.territory.persist.TerritoryRepository;
import com.azoth.territory.persist.TerritoryStore;
import com.azoth.territory.registry.TerritoryRegistry;
import com.azoth.territory.web.TerritoryWebServer;
import com.azoth.territory.web.WebConfig;
import com.azoth.territory.web.WebConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.guilds.GuildsServices;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public final class AzothTerritoryPlugin extends JavaPlugin {
    private TerritoryRegistry registry;
    private TerritoryRepository store;
    private GovernanceRegistry governance;
    private BlockProtection blockProtection;
    private TerritoryWebServer webServer;
    private WebConfig webConfig;
    private EconomyBridge economyBridge;
    private BukkitEconomyBridge bukkitEconomyBridge;
    private ReconciliationStore reconciliationStore;
    private GuildsServices guilds;
    private InfluenceEngine influenceEngine;
    private InfluenceStore influenceStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create data folder: " + getDataFolder());
        }

        this.registry = new TerritoryRegistry();
        Path dataFile = getDataFolder().toPath().resolve(TerritoryStore.DEFAULT_FILE_NAME);
        this.reconciliationStore = new ReconciliationStore(
                getDataFolder().toPath().resolve(ReconciliationStore.DEFAULT_FILE_NAME));
        try {
            this.store = createStore(dataFile);
        } catch (IOException e) {
            this.store = null;
            getLogger().log(Level.SEVERE,
                    "Territory persistence unavailable (database.enabled=true) — "
                            + "territory data and web submodule disabled", e);
        }
        if (store != null) {
            try {
                store.loadInto(registry);
                getLogger().info("Loaded " + registry.size() + " territor(y/ies) from " + describeStore(dataFile));
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to load territories from " + describeStore(dataFile), e);
            }
        }

        // Guilds subsystem is the governance source (guilds as local governments,
        // nations as alliances). Constructed before the governance registry so
        // permission checks resolve through real guild data; the web server and
        // commands start later in enableGuildsSubsystem().
        constructGuildsSubsystem();

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
                            + entries.size() + " unresolved transaction(s) persisted to "
                            + reconciliationStore.file());
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
        getLogger().info(
                "Registered territory protection listeners "
                        + "(break/place/fire/explosions/mob-spawn/entity-grief/interaction/pvp/teleport)");

        // Territory influence race (accrual → declare → countdown flip).
        InfluenceConfig influenceConfig = InfluenceConfigLoader.fromBukkit(getConfig());
        if (influenceConfig.enabled()) {
            try {
                this.influenceStore = new InfluenceStore(
                        getDataFolder().toPath().resolve("influence.json"));
                this.influenceEngine = new InfluenceEngine(
                        governance, influenceConfig, influenceStore,
                        (territoryId, newOwnerGuildId) -> saveTerritories(),
                        getLogger());
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
        stopWeb();
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
        if (store != null) {
            store.close();
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

    /**
     * Pick the territory store: {@link TerritoryStore} (JSON file) unless
     * {@code database.enabled: true}, which requires a reachable remote
     * PostgreSQL — no silent fallback.
     *
     * @throws IOException when Postgres is configured but unreachable
     */
    private TerritoryRepository createStore(Path dataFile) throws IOException {
        DatabaseSettings db = DatabaseSettingsLoader.fromValues(getConfig().getValues(true));
        if (!db.enabled()) {
            return new TerritoryStore(dataFile);
        }
        PostgresTerritoryRepository repo = new PostgresTerritoryRepository(db);
        getLogger().info("Territory persistence: remote PostgreSQL at " + db.jdbcUrl());
        return repo;
    }

    private String describeStore(Path dataFile) {
        return store instanceof PostgresTerritoryRepository
                ? "PostgreSQL"
                : dataFile.getFileName().toString();
    }

    private void startWebIfEnabled() {
        try {
            this.webConfig = WebConfigLoader.fromValues(getConfig().getValues(true), getDataFolder().toPath());
            if (!webConfig.enabled()) {
                getLogger().info("Territory web submodule disabled (web.enabled=false)");
                return;
            }
            if (store == null) {
                getLogger().warning("Territory web submodule not started: no territory store (see previous errors)");
                return;
            }
            this.webServer = new TerritoryWebServer(
                    webConfig,
                    registry,
                    new TerritoryJson(),
                    () -> store,
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

    /**
     * Construct the guilds subsystem (config, database, services). This is
     * early — before the governance registry — so governance resolves through
     * real guild data. Failure is non-fatal: governance falls back to
     * territory-local government via {@link GovernanceSource#none()}.
     */
    private void constructGuildsSubsystem() {
        try {
            this.guilds = new GuildsServices(this);
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

    public TerritoryRepository getStore() {
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
     * Reload territories from disk (admin command / tests).
     */
    public void reloadTerritories() throws IOException {
        store.loadInto(registry);
    }

    /**
     * Persist current registry to disk.
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

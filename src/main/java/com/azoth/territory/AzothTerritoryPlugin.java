package com.azoth.territory;

import com.azoth.territory.command.TerritoryCommand;
import com.azoth.territory.economy.BukkitEconomyBridge;
import com.azoth.territory.economy.EconomyBridge;
import com.azoth.territory.economy.EconomyConfig;
import com.azoth.territory.economy.PaymentRail;
import com.azoth.territory.economy.SettlementResult;
import com.azoth.territory.economy.SimulationTreasury;
import com.azoth.territory.economy.VaultTreasury;
import com.azoth.territory.listener.InteractionProtectionListener;
import com.azoth.territory.listener.ProtectionListener;
import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.persist.TerritoryStore;
import com.azoth.territory.persist.ReconciliationStore;
import com.azoth.territory.registry.TerritoryRegistry;
import com.azoth.territory.web.TerritoryWebServer;
import com.azoth.territory.web.WebConfig;
import com.azoth.territory.web.WebConfigLoader;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

public final class AzothTerritoryPlugin extends JavaPlugin {
    private TerritoryRegistry registry;
    private TerritoryStore store;
    private GovernanceRegistry governance;
    private BlockProtection blockProtection;
    private TerritoryWebServer webServer;
    private WebConfig webConfig;
    private EconomyBridge economyBridge;
    private BukkitEconomyBridge bukkitEconomyBridge;
    private ReconciliationStore reconciliationStore;

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
        this.store = new TerritoryStore(dataFile);
        try {
            store.loadInto(registry);
            getLogger().info("Loaded " + registry.size() + " territor(y/ies) from " + dataFile.getFileName());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load territories from " + dataFile, e);
        }

        this.governance = new GovernanceRegistry(registry);

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

        TerritoryCommand cmd = new TerritoryCommand(this);
        var pluginCommand = getCommand("territory");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(cmd);
            pluginCommand.setTabCompleter(cmd);
        } else {
            getLogger().warning("Command 'territory' not defined in plugin.yml");
        }

        startWebIfEnabled();
    }

    @Override
    public void onDisable() {
        stopWeb();
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
        public boolean available() {
            return false;
        }
    }

    private void startWebIfEnabled() {
        try {
            this.webConfig = WebConfigLoader.fromBukkit(getConfig(), getDataFolder().toPath());
            if (!webConfig.enabled()) {
                getLogger().info("Territory web submodule disabled (web.enabled=false)");
                return;
            }
            this.webServer = new TerritoryWebServer(
                    webConfig,
                    registry,
                    store.json(),
                    () -> store,
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

    public TerritoryRegistry getRegistry() {
        return registry;
    }

    public TerritoryStore getStore() {
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

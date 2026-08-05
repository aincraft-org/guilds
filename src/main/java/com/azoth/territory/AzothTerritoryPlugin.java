package com.azoth.territory;

import com.azoth.territory.command.TerritoryCommand;
import com.azoth.territory.listener.InteractionProtectionListener;
import com.azoth.territory.listener.ProtectionListener;
import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.persist.TerritoryStore;
import com.azoth.territory.registry.TerritoryRegistry;
import com.azoth.territory.web.TerritoryWebServer;
import com.azoth.territory.web.WebConfig;
import com.azoth.territory.web.WebConfigLoader;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Paper entry point for Azoth Territory.
 * Loads the territory registry from disk on enable, wires governance and block
 * protection listeners (break/place/fire/explosions/mob spawn/entity grief),
 * starts the optional embedded web submodule, and exposes the registry for
 * in-game lookups.
 */
public final class AzothTerritoryPlugin extends JavaPlugin {
    private TerritoryRegistry registry;
    private TerritoryStore store;
    private GovernanceRegistry governance;
    private BlockProtection blockProtection;
    private TerritoryWebServer webServer;
    private WebConfig webConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create data folder: " + getDataFolder());
        }

        this.registry = new TerritoryRegistry();
        Path dataFile = getDataFolder().toPath().resolve(TerritoryStore.DEFAULT_FILE_NAME);
        this.store = new TerritoryStore(dataFile);
        try {
            store.loadInto(registry);
            getLogger().info("Loaded " + registry.size() + " territor(y/ies) from " + dataFile.getFileName());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load territories from " + dataFile, e);
        }

        this.governance = new GovernanceRegistry(registry);
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
        if (store != null && registry != null) {
            try {
                store.save(registry);
                getLogger().info("Saved " + registry.size() + " territor(y/ies)");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Failed to save territories", e);
            }
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

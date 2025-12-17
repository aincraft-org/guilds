package org.aincraft.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildsPlugin;

import java.util.Objects;

@Singleton
public class GuildsConfig {
    private final GuildsPlugin plugin;
    private int claimBufferDistance;

    @Inject
    public GuildsConfig(GuildsPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        claimBufferDistance = plugin.getConfig().getInt("claim.buffer-distance", 4);

        if (claimBufferDistance < 0) {
            plugin.getLogger().warning("Invalid claim.buffer-distance: " + claimBufferDistance + ". Using default: 4");
            claimBufferDistance = 4;
        }

        plugin.getLogger().info("Claim buffer distance set to: " + claimBufferDistance + " chunks");
    }

    public int getClaimBufferDistance() {
        return claimBufferDistance;
    }

    public void reload() {
        loadConfig();
    }
}

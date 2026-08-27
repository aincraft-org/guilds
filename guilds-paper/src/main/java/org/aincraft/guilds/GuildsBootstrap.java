package org.aincraft.guilds;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Paper bootstrap for the Guilds plugin. Runs before the plugin instance exists;
 * only early, Bukkit-free initialization belongs here. All runtime setup stays in
 * {@link GuildsPlugin#onEnable()}.
 */
public final class GuildsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        context.getLogger().info("Guilds bootstrap: early initialization");
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new GuildsPlugin();
    }
}

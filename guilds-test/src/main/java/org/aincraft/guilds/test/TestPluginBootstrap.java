package org.aincraft.guilds.test;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Paper bootstrap for the guilds-test harness plugin. All setup stays in
 * {@link TestPlugin#onEnable()}; bootstrap only logs early.
 */
public final class TestPluginBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        context.getLogger().info("TestPlugin bootstrap: early initialization");
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new TestPlugin();
    }
}

package org.aincraft.guilds.test;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal test plugin loaded by the guilds-test runServer harness alongside the
 * guilds shadow jar. Exists so the harness boots a second plugin and can host
 * test-only commands/listeners without touching production code.
 */
public final class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Guilds test plugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("Guilds test plugin disabled");
    }
}

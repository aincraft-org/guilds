package org.aincraft.guilds.storage.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** Schedules work on the Paper main thread via the Bukkit scheduler. */
public final class BukkitMainThreadExecutor implements MainThreadExecutor {
    private final Plugin plugin;

    public BukkitMainThreadExecutor(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void run(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}

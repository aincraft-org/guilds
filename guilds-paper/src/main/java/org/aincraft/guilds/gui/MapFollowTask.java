package org.aincraft.guilds.gui;

import de.flog99.mapgui.MapGui;
import de.flog99.mapgui.Session;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MapFollowTask {

    private static final Map<JavaPlugin, BukkitTask> tasks = new ConcurrentHashMap<>();

    private MapFollowTask() {
    }

    public static void start(JavaPlugin plugin, MapGui mapGui) {
        tasks.computeIfAbsent(plugin, p -> p.getServer().getScheduler().runTaskTimer(p, () -> {
            for (Session session : mapGui.sessions()) {
                if (!(session.screen() instanceof GuildClaimScreen screen)) {
                    continue;
                }
                var loc = session.player().getLocation();
                int cx = loc.getChunk().getX();
                int cz = loc.getChunk().getZ();
                String world = loc.getWorld().getName();
                if (cx != screen.lastChunkX() || cz != screen.lastChunkZ()
                        || !world.equals(screen.lastWorld())) {
                    screen.setFollow(cx, cz, world);
                    session.invalidate();
                }
            }
        }, 20L, 10L));
    }

    public static void stop(JavaPlugin plugin) {
        BukkitTask task = tasks.remove(plugin);
        if (task != null) {
            task.cancel();
        }
    }
}

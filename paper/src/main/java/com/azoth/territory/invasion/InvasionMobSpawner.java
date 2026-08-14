package com.azoth.territory.invasion;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

public final class InvasionMobSpawner {
    private final Plugin plugin;
    private final InvasionMobTags tags;
    private final Random random;
    public InvasionMobSpawner(Plugin plugin) { this(plugin, new Random()); }
    public InvasionMobSpawner(Plugin plugin, Random random) { this.plugin = plugin; this.tags = new InvasionMobTags(plugin); this.random = random; }
    public List<UUID> spawn(InvasionRecord record, Wave wave, int radius, int attempts, Predicate<Location> claim) {
        World world = plugin.getServer().getWorld(record.worldId());
        if (world == null || radius < 0 || attempts <= 0 || radius > (Integer.MAX_VALUE - 1) / 2) return List.of();
        long side = (long) radius * 2L + 1L;
        if (side > Integer.MAX_VALUE) return List.of();
        List<UUID> result = new ArrayList<>();
        for (MobEntry entry : wave.mobs()) for (int n = 0; n < entry.count(); n++) {
            EntityType type = EntityType.fromName(entry.entityType());
            if (type == null) continue;
            for (int i = 0; i < attempts; i++) {
                int x = (int) Math.floor(record.x()) + random.nextInt((int) side) - radius;
                int z = (int) Math.floor(record.z()) + random.nextInt((int) side) - radius;
                int y = world.getHighestBlockYAt(x, z) + 1;
                Location location = new Location(world, x + .5, y, z + .5);
                if (!world.isChunkLoaded(x >> 4, z >> 4) || !claim.test(location)) continue;
                if (world.getBlockAt(x, y - 1, z).isPassable()
                        || !world.getBlockAt(x, y, z).isPassable() || !world.getBlockAt(x, y + 1, z).isPassable()) continue;
                Entity entity = world.spawnEntity(location, type);
                tags.tag(entity, record.invasionId(), record.guildId());
                result.add(entity.getUniqueId());
                break;
            }
        }
        return List.copyOf(result);
    }
}

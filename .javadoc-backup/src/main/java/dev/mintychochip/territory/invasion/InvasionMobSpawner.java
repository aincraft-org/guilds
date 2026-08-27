package dev.mintychochip.territory.invasion;

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
    private final Predicate<org.bukkit.block.Block> solidFloor;
    public InvasionMobSpawner(Plugin plugin) { this(plugin, new Random(), block -> !block.isPassable()); }
    public InvasionMobSpawner(Plugin plugin, Random random) { this(plugin, random, block -> !block.isPassable()); }
    public InvasionMobSpawner(Plugin plugin, Random random, Predicate<org.bukkit.block.Block> solidFloor) {
        this.plugin = plugin; this.tags = new InvasionMobTags(plugin); this.random = random; this.solidFloor = solidFloor;
    }
    public List<UUID> spawn(InvasionRecord record, Wave wave, int radius, int attempts, Predicate<Location> claim) {
        World world = plugin.getServer().getWorld(record.worldId());
        if (world == null || radius < 0 || attempts <= 0 || radius > (Integer.MAX_VALUE - 1) / 2) return List.of();
        long side = (long) radius * 2L + 1L;
        if (side > Integer.MAX_VALUE) return List.of();
        List<UUID> result = new ArrayList<>();
        List<Entity> created = new ArrayList<>();
        try {
            for (MobEntry entry : wave.mobs()) for (int n = 0; n < entry.count(); n++) {
                EntityType type = EntityType.fromName(entry.entityType());
                if (type == null) continue;
                for (int i = 0; i < attempts; i++) {
                    long centerX = (long) Math.floor(record.x());
                    long centerZ = (long) Math.floor(record.z());
                    long xLong = centerX + random.nextInt((int) side) - (long) radius;
                    long zLong = centerZ + random.nextInt((int) side) - (long) radius;
                    if (xLong < Integer.MIN_VALUE || xLong > Integer.MAX_VALUE
                            || zLong < Integer.MIN_VALUE || zLong > Integer.MAX_VALUE) continue;
                    int x = (int) xLong;
                    int z = (int) zLong;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    int y = world.getHighestBlockYAt(x, z) + 1;
                    Location location = new Location(world, x + .5, y, z + .5);
                    if (!claim.test(location)) continue;
                    if (!solidFloor.test(world.getBlockAt(x, y - 1, z))
                            || !world.getBlockAt(x, y, z).isPassable() || !world.getBlockAt(x, y + 1, z).isPassable()) continue;
                    Entity entity = world.spawnEntity(location, type);
                    created.add(entity);
                    tags.tag(entity, record.invasionId(), record.guildId());
                    result.add(entity.getUniqueId());
                    break;
                }
            }
            return List.copyOf(result);
        } catch (RuntimeException failure) {
            for (Entity entity : created) {
                try {
                    entity.remove();
                } catch (RuntimeException ignored) {
                    // Preserve the original spawn failure while attempting best-effort rollback.
                }
            }
            throw failure;
        }
    }
}

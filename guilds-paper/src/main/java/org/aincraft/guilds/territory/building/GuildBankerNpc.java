package org.aincraft.guilds.territory.building;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/** Spawns a wandering tagged banker villager at a BANK facility. */
public final class GuildBankerNpc {
    public static final String TAG = "GUILD_BANK";
    public static final String FACILITY_TAG_PREFIX = "guild-bank-facility:";
    static final double WANDER_RADIUS = 4.0;

    private final JavaPlugin plugin;

    public GuildBankerNpc(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void spawn(SettlementFacility facility) {
        if (facility == null || facility.type() != FacilityType.BANK) {
            return;
        }
        World world = plugin.getServer().getWorld(facility.worldId());
        if (world == null) {
            return;
        }
        if (find(world, facility) != null) {
            return;
        }
        Location home = home(world, facility);
        Entity spawned = world.spawnEntity(home, EntityType.VILLAGER);
        if (!(spawned instanceof Villager villager)) {
            return;
        }
        configure(villager, facility);
        try {
            villager.setProfession(Villager.Profession.LIBRARIAN);
            villager.setVillagerLevel(2);
        } catch (RuntimeException ignored) {
            // Profession registry is only available on a live Paper server.
        }
        startWander(villager, home);
    }

    public void despawn(SettlementFacility facility) {
        if (facility == null) {
            return;
        }
        World world = plugin.getServer().getWorld(facility.worldId());
        if (world == null) {
            return;
        }
        Villager existing = find(world, facility);
        if (existing != null) {
            existing.remove();
        }
    }

    public void restore(Collection<SettlementFacility> facilities) {
        if (facilities == null) {
            return;
        }
        for (SettlementFacility facility : facilities) {
            spawn(facility);
        }
    }

    public static void configure(Villager villager, SettlementFacility facility) {
        villager.customName(Component.text(facility.name() + " Banker", NamedTextColor.GOLD));
        villager.setCustomNameVisible(true);
        villager.addScoreboardTag(TAG);
        villager.addScoreboardTag(FACILITY_TAG_PREFIX + facility.id());
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setCanPickupItems(false);
        villager.setAdult();
        villager.setAI(true);
        villager.setAware(true);
    }

    private void startWander(Villager villager, Location home) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!villager.isValid() || villager.isDead()) {
                    cancel();
                    return;
                }
                Location current = villager.getLocation();
                if (current.getWorld() == null || !current.getWorld().equals(home.getWorld())) {
                    return;
                }
                double distance = current.distanceSquared(home);
                Location target = distance > (WANDER_RADIUS * WANDER_RADIUS)
                        ? home
                        : home.clone().add(offset(), 0, offset());
                try {
                    villager.getPathfinder().moveTo(target, 0.6);
                } catch (RuntimeException ignored) {
                    if (distance > (WANDER_RADIUS * WANDER_RADIUS)) {
                        villager.teleport(home);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    private static double offset() {
        return ThreadLocalRandom.current().nextDouble(-WANDER_RADIUS + 1, WANDER_RADIUS - 1);
    }

    private static Villager find(World world, SettlementFacility facility) {
        String tag = FACILITY_TAG_PREFIX + facility.id();
        for (Villager villager : world.getEntitiesByClass(Villager.class)) {
            if (villager.getScoreboardTags().contains(tag)) {
                return villager;
            }
        }
        return null;
    }

    private static Location home(World world, SettlementFacility facility) {
        return new Location(world, facility.x() + 0.5, facility.y() + 1, facility.z() + 0.5);
    }
}

package org.aincraft.towny.listeners;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.PermissionService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.Material;

import java.util.logging.Level;

/**
 * Event listener that enforces town toggle settings
 */
public class TownToggleListener implements Listener {

    private final TownyPlugin plugin;
    private final PermissionService permissionService;

    @Inject
    public TownToggleListener(TownyPlugin plugin, PermissionService permissionService) {
        this.plugin = plugin;
        this.permissionService = permissionService;
    }

    // ==================== PvP TOGGLE ====================

    /**
     * Handle PvP damage prevention when PvP is disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isPvpDisabled(event)) {
            event.setCancelled(true);

            // Send feedback to players
            Entity damager = event.getDamager();
            Entity victim = event.getEntity();

            if (damager instanceof Player) {
                ((Player) damager).sendMessage("§cPvP is disabled in this town!");
            }
            if (victim instanceof Player) {
                ((Player) victim).sendMessage("§cPvP is disabled in this town!");
            }

            plugin.getLogger().fine("PvP damage prevented - PvP disabled at location");
        }
    }

    /**
     * Prevent player from shooting projectiles in non-PvP towns
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player shooter = (Player) event.getEntity().getShooter();

            // Check if this would potentially affect PvP
            if (isPvpProjectile(event.getEntity().getType())) {
                if (!permissionService.isPvpEnabledAtLocation(
                    shooter.getLocation().getBlockX(),
                    shooter.getLocation().getBlockZ(),
                    shooter.getWorld().getName())) {
                    event.setCancelled(true);
                    shooter.sendMessage("§cProjectile combat is disabled in this town!");
                    plugin.getLogger().fine("Projectile launch prevented - PvP disabled");
                }
            }
        }
    }

    // ==================== FIRE TOGGLE ====================

    /**
     * Prevent fire spread when fire is disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!permissionService.isFireEnabledAtLocation(
                event.getBlock().getX(),
                event.getBlock().getZ(),
                event.getBlock().getWorld().getName())) {

            // Allow fire from players in towns with fire disabled (for controlled fires)
            if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL &&
                event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) {
                event.setCancelled(true);
                plugin.getLogger().fine("Fire ignition prevented - Fire disabled at location");
            }
        }
    }

    /**
     * Prevent fire spread when fire is disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE || event.getSource().getType() == Material.LAVA) {
            if (!permissionService.isFireEnabledAtLocation(
                    event.getBlock().getX(),
                    event.getBlock().getZ(),
                    event.getBlock().getWorld().getName())) {
                event.setCancelled(true);
                plugin.getLogger().fine("Fire spread prevented - Fire disabled at location");
            }
        }
    }

    /**
     * Prevent fire from burning blocks in towns with fire disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!permissionService.isFireEnabledAtLocation(
                event.getBlock().getX(),
                event.getBlock().getZ(),
                event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
            plugin.getLogger().fine("Block burn prevented - Fire disabled at location");
        }
    }

    /**
     * Prevent fire from burning entities in towns with fire disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        if (!permissionService.isFireEnabledAtLocation(
                entity.getLocation().getBlockX(),
                entity.getLocation().getBlockZ(),
                entity.getWorld().getName())) {
            event.setCancelled(true);
            plugin.getLogger().fine("Entity combustion prevented - Fire disabled at location");
        }
    }

    // ==================== EXPLOSIONS TOGGLE ====================

    /**
     * Prevent explosions when explosions are disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Check explosion location
        if (!permissionService.areExplosionsEnabledAtLocation(
                event.getLocation().getBlockX(),
                event.getLocation().getBlockZ(),
                event.getLocation().getWorld().getName())) {
            event.setCancelled(true);
            plugin.getLogger().fine("Entity explosion prevented - Explosions disabled at location");
            return;
        }

        // Check if explosion would affect blocks in explosion-disabled towns
        event.blockList().removeIf(block ->
            !permissionService.areExplosionsEnabledAtLocation(
                block.getX(),
                block.getZ(),
                block.getWorld().getName())
        );
    }

    /**
     * Prevent block explosion when explosions are disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!permissionService.areExplosionsEnabledAtLocation(
                event.getBlock().getX(),
                event.getBlock().getZ(),
                event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
            plugin.getLogger().fine("Block explosion prevented - Explosions disabled at location");
        }
    }

    // ==================== MOBS TOGGLE ====================

    /**
     * Prevent mob spawning when mobs are disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isHostileMob(event.getEntityType())) {
            // Only block natural/uncontrolled spawns, allow player-initiated
            if (shouldBlockSpawn(event.getSpawnReason())) {
                if (!permissionService.areMobsEnabledAtLocation(
                        event.getLocation().getBlockX(),
                        event.getLocation().getBlockZ(),
                        event.getLocation().getWorld().getName())) {
                    event.setCancelled(true);
                    plugin.getLogger().fine("Hostile mob spawn prevented - Mobs disabled at location: " +
                                           event.getEntityType() + " (reason: " + event.getSpawnReason() + ")");
                }
            }
        }
    }

    /**
     * Prevent mob from targeting players in towns where PvP is disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player && isHostileMob(event.getEntity().getType())) {
            Player player = (Player) event.getTarget();

            if (!permissionService.isPvpEnabledAtLocation(
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockZ(),
                    player.getWorld().getName())) {
                // Reset target to prevent mob aggression in non-PvP towns
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if PvP is disabled at the event location
     */
    private boolean isPvpDisabled(EntityDamageByEntityEvent event) {
        // Only check PvP-related damage
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return false;
        }

        return !permissionService.isPvpEnabledAtLocation(
                event.getEntity().getLocation().getBlockX(),
                event.getEntity().getLocation().getBlockZ(),
                event.getEntity().getWorld().getName()
        );
    }

    /**
     * Check if a projectile type is related to PvP
     */
    private boolean isPvpProjectile(EntityType type) {
        return switch (type) {
            case ARROW, SPECTRAL_ARROW, TRIDENT, EGG, SNOWBALL, FIREBALL, SMALL_FIREBALL,
                 WITHER_SKULL, DRAGON_FIREBALL -> true;
            default -> false;
        };
    }

    /**
     * Check if an entity type is considered hostile
     */
    private boolean isHostileMob(EntityType type) {
        return switch (type) {
            case ZOMBIE, SKELETON, SPIDER, CREEPER, ENDERMAN, WITCH, GHAST,
                 WITHER, ENDER_DRAGON, PHANTOM, PILLAGER, RAVAGER, VINDICATOR,
                 EVOKER, VEX, HUSK, STRAY, DROWNED, ZOGLIN, ZOMBIFIED_PIGLIN,
                 BOGGED, HOGLIN, SHULKER -> true;
            default -> false;
        };
    }

    /**
     * Check if a spawn reason should be blocked when mobs are disabled
     */
    private boolean shouldBlockSpawn(CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            // Block natural and uncontrolled spawns
            case NATURAL, REINFORCEMENTS, PATROL, NETHER_PORTAL,
                 CHUNK_GEN, DEFAULT, SLIME_SPLIT, RAID, VILLAGE_INVASION -> true;
            // Allow player-initiated and controlled spawns (spawners, eggs, breeding, etc.)
            case SPAWNER, TRIAL_SPAWNER, SPAWNER_EGG, EGG, DISPENSE_EGG,
                 BREEDING, BUILD_IRONGOLEM, BUILD_WITHER, BUILD_SNOWMAN,
                 CURED, TRAP, JOCKEY, INFECTION, LIGHTNING, COMMAND, CUSTOM,
                 BUCKET, BEEHIVE, SHOULDER_ENTITY, ENDER_PEARL, MOUNT -> false;
            // Block unknown spawn reasons by default for safety
            default -> true;
        };
    }

    /**
     * Handle player interaction with blocks when certain toggles are disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Material blockType = event.getClickedBlock().getType();

        // Prevent interaction with fire-related blocks when fire is disabled
        if (blockType == Material.FLINT_AND_STEEL) {
            if (!permissionService.isFireEnabledAtLocation(
                    event.getClickedBlock().getX(),
                    event.getClickedBlock().getZ(),
                    event.getClickedBlock().getWorld().getName())) {
                if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§cFire-related actions are disabled in this town!");
                    plugin.getLogger().fine("Flint and steel interaction prevented - Fire disabled");
                }
            }
        }
    }

    /**
     * Prevent lightning strikes when fire is disabled
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        if (!permissionService.isFireEnabledAtLocation(
                event.getLightning().getLocation().getBlockX(),
                event.getLightning().getLocation().getBlockZ(),
                event.getLightning().getWorld().getName())) {
            // Allow lightning strikes but prevent fire from lightning
            // This is handled by the BlockIgniteEvent with cause = LIGHTNING
            plugin.getLogger().fine("Lightning strike allowed, but fire will be prevented - Fire disabled");
        }
    }
}
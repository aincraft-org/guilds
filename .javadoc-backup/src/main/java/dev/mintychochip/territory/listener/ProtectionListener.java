package dev.mintychochip.territory.listener;

import dev.mintychochip.territory.permission.BlockProtection;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Paper edge: maps block break/place, interaction, fire, explosions, pistons/fluids,
 * natural/hostile mob spawn, entity block grief, and crop trampling onto domain
 * {@link BlockProtection} decisions. No parallel permission logic.
 */
public final class ProtectionListener implements Listener {

    /**
     * Spawn reasons cancelled under assigned governments. Eggs, spawners, commands,
     * breeding, and other player/plugin-driven reasons stay unrestricted (domain is
     * location-only; reason filter lives here).
     */
    private static final Set<CreatureSpawnEvent.SpawnReason> RESTRICTED_SPAWN_REASONS =
            EnumSet.of(
                    CreatureSpawnEvent.SpawnReason.NATURAL,
                    CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
                    CreatureSpawnEvent.SpawnReason.PATROL,
                    CreatureSpawnEvent.SpawnReason.RAID,
                    CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
                    CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
                    CreatureSpawnEvent.SpawnReason.JOCKEY,
                    CreatureSpawnEvent.SpawnReason.MOUNT,
                    CreatureSpawnEvent.SpawnReason.TRAP,
                    CreatureSpawnEvent.SpawnReason.SILVERFISH_BLOCK,
                    CreatureSpawnEvent.SpawnReason.DEFAULT
            );

    private final BlockProtection protection;
    private final BiPredicate<org.bukkit.entity.Entity, Block> entityGriefBypass;

    public ProtectionListener(BlockProtection protection) {
        this(protection, (entity, block) -> false);
    }

    public ProtectionListener(BlockProtection protection,
                              BiPredicate<org.bukkit.entity.Entity, Block> entityGriefBypass) {
        this.protection = Objects.requireNonNull(protection, "protection");
        this.entityGriefBypass = Objects.requireNonNull(entityGriefBypass, "entityGriefBypass");
    }

    public BlockProtection protection() {
        return protection;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String world = block.getWorld().getName();
        String actorId = player.getUniqueId().toString();
        if (!protection.canBreak(world, block.getX(), block.getZ(), actorId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String world = block.getWorld().getName();
        String actorId = player.getUniqueId().toString();
        if (!protection.canPlace(world, block.getX(), block.getZ(), actorId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Player player = event.getPlayer();
        String world = clicked.getWorld().getName();
        String actorId = player.getUniqueId().toString();
        if (!protection.canInteract(world, clicked.getX(), clicked.getZ(), actorId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        denyPistonCrossingBoundary(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        denyPistonCrossingBoundary(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        // Water/lava flow (or any block transfer) crossing a claim boundary
        Block from = event.getBlock();
        Block to = event.getToBlock();
        String world = from.getWorld().getName();
        if (protection.crossesBoundary(
                world, from.getX(), from.getZ(), to.getX(), to.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        if (protection.isFireProtected(
                block.getWorld().getName(), block.getX(), block.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        // Fire (and similar) spreading onto a new block
        if (event.getSource().getType() != Material.FIRE
                && event.getNewState().getType() != Material.FIRE) {
            return;
        }
        Block target = event.getBlock();
        if (protection.isFireProtected(
                target.getWorld().getName(), target.getX(), target.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (protection.isFireProtected(
                block.getWorld().getName(), block.getX(), block.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterProtectedBlocks(event.blockList(), block -> entityGriefBypass.test(event.getEntity(), block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterProtectedBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isRestrictedSpawnReason(event.getSpawnReason())) {
            return;
        }
        var loc = event.getLocation();
        if (protection.blocksMobSpawn(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (!entityGriefBypass.test(event.getEntity(), block)
                && protection.blocksEntityGrief(
                        block.getWorld().getName(), block.getX(), block.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        // Crop trampling path: entity physical interact on farmland
        Block block = event.getBlock();
        if (block.getType() != Material.FARMLAND) {
            return;
        }
        if (protection.blocksEntityGrief(
                block.getWorld().getName(), block.getX(), block.getZ())) {
            event.setCancelled(true);
        }
    }
    /**
     * Whether this Bukkit spawn reason is in the natural/hostile set blocked under
     * assigned governments. Exposed for structural tests without a live server.
     */
    public static boolean isRestrictedSpawnReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason != null && RESTRICTED_SPAWN_REASONS.contains(reason);
    }

    private void filterProtectedBlocks(java.util.List<Block> blocks) {
        filterProtectedBlocks(blocks, ignored -> false);
    }

    private void filterProtectedBlocks(java.util.List<Block> blocks, Predicate<Block> bypass) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (!bypass.test(block) && protection.areExplosionsProtected(
                    block.getWorld().getName(), block.getX(), block.getZ())) {
                it.remove();
            }
        }
    }

    private void denyPistonCrossingBoundary(BlockPistonEvent event) {
        Block base = event.getBlock();
        String world = base.getWorld().getName();
        int bx = base.getX();
        int bz = base.getZ();
        if (event instanceof BlockPistonExtendEvent extend) {
            for (Block moved : extend.getBlocks()) {
                if (protection.crossesBoundary(world, moved.getX(), moved.getZ(),
                        moved.getRelative(event.getDirection()).getX(),
                        moved.getRelative(event.getDirection()).getZ())) {
                    event.setCancelled(true);
                    return;
                }
            }
        } else if (event instanceof BlockPistonRetractEvent retract) {
            for (Block moved : retract.getBlocks()) {
                int tx = moved.getRelative(event.getDirection()).getX();
                int tz = moved.getRelative(event.getDirection()).getZ();
                if (moved.getX() != bx || moved.getZ() != bz
                        || retract.getRetractLocation().getBlockX() != tx
                        || retract.getRetractLocation().getBlockZ() != tz) {
                    continue;
                }
                if (protection.crossesBoundary(world, moved.getX(), moved.getZ(), tx, tz)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}

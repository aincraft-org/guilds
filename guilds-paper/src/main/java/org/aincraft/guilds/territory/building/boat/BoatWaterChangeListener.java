package org.aincraft.guilds.territory.building.boat;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Collection;
import java.util.HashSet;

/** Invalidates boat geometry after water, shoreline, or clear-space changes. */
public final class BoatWaterChangeListener implements Listener {
    private static final int[][] NEIGHBORS = {
            {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0},
            {0, 0, -1}, {0, 0, 1}
    };

    private final BoatRouteCache cache;

    public BoatWaterChangeListener(BoatRouteCache cache) {
        this.cache = java.util.Objects.requireNonNull(cache, "cache");
    }
    public BoatWaterChangeListener(BoatRouteService service) {
        this(java.util.Objects.requireNonNull(service, "service").cache());
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (waterAffecting(block)) {
            invalidate(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (waterAffecting(block)) {
            invalidate(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluidMove(BlockFromToEvent event) {
        Block source = event.getBlock();
        Block destination = event.getToBlock();
        if (isWater(source.getType()) || isWater(destination.getType())
                || waterAffecting(source) || waterAffecting(destination)) {
            invalidate(source);
            invalidate(destination);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        invalidatePiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        invalidatePiston(event, event.getBlocks());
    }

    public void onPiston(BlockPistonEvent event) {
        if (waterAffecting(event.getBlock())) {
            invalidate(event.getBlock());
        }
    }

    private void invalidatePiston(BlockPistonEvent event, Collection<Block> moved) {
        Set<Block> affected = new HashSet<>(moved);
        affected.add(event.getBlock());
        if (event.getBlock().getWorld() != null) {
            affected.add(event.getBlock().getRelative(event.getDirection()));
        }
        for (Block block : affected) {
            if (waterAffecting(block)) {
                invalidate(block);
            }
        }
    }

    private void invalidate(Block block) {
        cache.invalidateChunk(block.getWorld().getUID(), block.getChunk().getX(), block.getChunk().getZ());
    }

    private static boolean waterAffecting(Block block) {
        if (isWater(block.getType())) {
            return true;
        }
        for (int[] offset : NEIGHBORS) {
            if (isWater(block.getRelative(offset[0], offset[1], offset[2]).getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWater(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN;
    }
}

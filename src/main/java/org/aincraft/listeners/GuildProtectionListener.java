package org.aincraft.listeners;

import com.google.inject.Inject;
import org.aincraft.ChunkKey;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.GuildService;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.subregion.Subregion;
import org.aincraft.subregion.SubregionService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * Event listener for protecting guild-claimed chunks and subregions.
 * Checks permissions before allowing block modifications.
 */
public class GuildProtectionListener implements Listener {
    private final GuildService guildService;
    private final SubregionService subregionService;

    @Inject
    public GuildProtectionListener(GuildService guildService, SubregionService subregionService) {
        this.guildService = guildService;
        this.subregionService = subregionService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!canPerformAction(player, loc, GuildPermission.DESTROY)) {
            event.setCancelled(true);
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "You don't have permission to break blocks here"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!canPerformAction(player, loc, GuildPermission.BUILD)) {
            event.setCancelled(true);
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "You don't have permission to build here"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only check for block interactions
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Only protect interactable blocks (chests, doors, buttons, etc.)
        if (!isProtectedInteraction(block)) {
            return;
        }

        Player player = event.getPlayer();
        Location loc = block.getLocation();

        if (!canPerformAction(player, loc, GuildPermission.INTERACT)) {
            event.setCancelled(true);
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                    "You don't have permission to interact here"));
        }
    }

    /**
     * Checks if a player can perform an action at a location.
     * Priority: Subregion permissions > Guild chunk permissions
     */
    private boolean canPerformAction(Player player, Location loc, GuildPermission permission) {
        // Check if location is in a claimed chunk
        ChunkKey chunk = ChunkKey.from(loc.getChunk());
        Guild chunkOwner = guildService.getChunkOwner(chunk);

        // Not claimed - allow action
        if (chunkOwner == null) {
            return true;
        }

        // Check if player is in the owning guild
        Guild playerGuild = guildService.getPlayerGuild(player.getUniqueId());

        // Not in any guild - check if they need protection
        if (playerGuild == null) {
            return false; // Non-guild members can't modify claimed chunks
        }

        // Different guild - deny
        if (!playerGuild.getId().equals(chunkOwner.getId())) {
            return false;
        }

        // Same guild - check subregion first
        Optional<Subregion> subregionOpt = subregionService.getSubregionAt(loc);
        if (subregionOpt.isPresent()) {
            return subregionService.hasSubregionPermission(
                    subregionOpt.get(), player.getUniqueId(), permission);
        }

        // No subregion - check guild permission
        return guildService.hasPermission(chunkOwner.getId(), player.getUniqueId(), permission);
    }

    /**
     * Checks if a block interaction should be protected.
     */
    private boolean isProtectedInteraction(Block block) {
        return switch (block.getType()) {
            // Containers
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX, ENDER_CHEST, HOPPER, DROPPER, DISPENSER,
                 FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND,
                 // Doors and gates
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR,
                 ACACIA_DOOR, DARK_OAK_DOOR, MANGROVE_DOOR, CHERRY_DOOR,
                 BAMBOO_DOOR, CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR,
                 OAK_TRAPDOOR, SPRUCE_TRAPDOOR, BIRCH_TRAPDOOR, JUNGLE_TRAPDOOR,
                 ACACIA_TRAPDOOR, DARK_OAK_TRAPDOOR, MANGROVE_TRAPDOOR, CHERRY_TRAPDOOR,
                 BAMBOO_TRAPDOOR, CRIMSON_TRAPDOOR, WARPED_TRAPDOOR, IRON_TRAPDOOR,
                 OAK_FENCE_GATE, SPRUCE_FENCE_GATE, BIRCH_FENCE_GATE, JUNGLE_FENCE_GATE,
                 ACACIA_FENCE_GATE, DARK_OAK_FENCE_GATE, MANGROVE_FENCE_GATE,
                 CHERRY_FENCE_GATE, BAMBOO_FENCE_GATE, CRIMSON_FENCE_GATE, WARPED_FENCE_GATE,
                 // Redstone
                 LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON,
                 JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON,
                 CHERRY_BUTTON, BAMBOO_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 POLISHED_BLACKSTONE_BUTTON, REPEATER, COMPARATOR, DAYLIGHT_DETECTOR,
                 NOTE_BLOCK, JUKEBOX,
                 // Utility
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, ENCHANTING_TABLE,
                 LECTERN, GRINDSTONE, STONECUTTER, LOOM, CARTOGRAPHY_TABLE,
                 SMITHING_TABLE, CRAFTING_TABLE, BEACON,
                 // Beds
                 WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED,
                 YELLOW_BED, LIME_BED, PINK_BED, GRAY_BED,
                 LIGHT_GRAY_BED, CYAN_BED, PURPLE_BED, BLUE_BED,
                 BROWN_BED, GREEN_BED, RED_BED, BLACK_BED,
                 // Signs
                 OAK_SIGN, SPRUCE_SIGN, BIRCH_SIGN, JUNGLE_SIGN,
                 ACACIA_SIGN, DARK_OAK_SIGN, MANGROVE_SIGN, CHERRY_SIGN,
                 BAMBOO_SIGN, CRIMSON_SIGN, WARPED_SIGN,
                 OAK_WALL_SIGN, SPRUCE_WALL_SIGN, BIRCH_WALL_SIGN, JUNGLE_WALL_SIGN,
                 ACACIA_WALL_SIGN, DARK_OAK_WALL_SIGN, MANGROVE_WALL_SIGN, CHERRY_WALL_SIGN,
                 BAMBOO_WALL_SIGN, CRIMSON_WALL_SIGN, WARPED_WALL_SIGN,
                 OAK_HANGING_SIGN, SPRUCE_HANGING_SIGN, BIRCH_HANGING_SIGN, JUNGLE_HANGING_SIGN,
                 ACACIA_HANGING_SIGN, DARK_OAK_HANGING_SIGN, MANGROVE_HANGING_SIGN, CHERRY_HANGING_SIGN,
                 BAMBOO_HANGING_SIGN, CRIMSON_HANGING_SIGN, WARPED_HANGING_SIGN,
                 // Misc
                 RESPAWN_ANCHOR, BELL, COMPOSTER, FLOWER_POT,
                 ARMOR_STAND, ITEM_FRAME, GLOW_ITEM_FRAME -> true;
            default -> false;
        };
    }
}

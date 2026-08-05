package org.aincraft.towny.listeners;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.Resident;
import org.aincraft.towny.services.PermissionService;
import org.aincraft.towny.services.ResidentService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Event listener that enforces town public access settings
 */
public class TownPublicAccessListener implements Listener {

    private final TownyPlugin plugin;
    private final PermissionService permissionService;
    private final ResidentService residentService;

    @Inject
    public TownPublicAccessListener(TownyPlugin plugin, PermissionService permissionService, ResidentService residentService) {
        this.plugin = plugin;
        this.permissionService = permissionService;
        this.residentService = residentService;
    }

    // ==================== BLOCK PROTECTION ====================

    /**
     * Prevent non-residents from breaking blocks in private towns
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        int x = event.getBlock().getLocation().getBlockX();
        int z = event.getBlock().getLocation().getBlockZ();
        String world = event.getBlock().getWorld().getName();

        // Check proper destroy permissions
        if (!permissionService.canDestroy(playerUuid, x, z, world)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot break blocks in this town!");
            plugin.getLogger().fine("Block break prevented - No destroy permission");
            return;
        }
    }

    /**
     * Prevent non-residents from placing blocks in private towns
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        int x = event.getBlock().getLocation().getBlockX();
        int z = event.getBlock().getLocation().getBlockZ();
        String world = event.getBlock().getWorld().getName();

        // Check proper build permissions
        if (!permissionService.canBuild(playerUuid, x, z, world)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot place blocks in this town!");
            plugin.getLogger().fine("Block place prevented - No build permission");
            return;
        }
    }

    // ==================== ENTITY PROTECTION ====================

    /**
     * Prevent non-authorized players from damaging entities
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player damager = (Player) event.getDamager();
        UUID damagerUuid = damager.getUniqueId();

        // Check if this is not PvP (PvP handled by TownToggleListener)
        if (event.getEntity() instanceof Player) return;

        int x = event.getEntity().getLocation().getBlockX();
        int z = event.getEntity().getLocation().getBlockZ();
        String world = event.getEntity().getWorld().getName();

        // Use unified permission check (same hierarchy as blocks)
        if (!permissionService.canInteractWithEntity(damagerUuid, x, z, world)) {
            event.setCancelled(true);
            damager.sendMessage("§cYou cannot damage entities here!");
            plugin.getLogger().fine("Entity damage prevented - No permission at location");
        }
    }

    /**
     * Prevent non-authorized players from damaging vehicles
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!(event.getAttacker() instanceof Player)) return;

        Player damager = (Player) event.getAttacker();
        UUID damagerUuid = damager.getUniqueId();

        int x = event.getVehicle().getLocation().getBlockX();
        int z = event.getVehicle().getLocation().getBlockZ();
        String world = event.getVehicle().getWorld().getName();

        if (!permissionService.canInteractWithEntity(damagerUuid, x, z, world)) {
            event.setCancelled(true);
            damager.sendMessage("§cYou cannot damage vehicles here!");
            plugin.getLogger().fine("Vehicle damage prevented - No permission at location");
        }
    }

    /**
     * Prevent non-authorized players from entering vehicles
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) return;

        Player player = (Player) event.getEntered();
        UUID playerUuid = player.getUniqueId();

        int x = event.getVehicle().getLocation().getBlockX();
        int z = event.getVehicle().getLocation().getBlockZ();
        String world = event.getVehicle().getWorld().getName();

        if (!permissionService.canInteractWithEntity(playerUuid, x, z, world)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot enter vehicles here!");
            plugin.getLogger().fine("Vehicle enter prevented - No permission at location");
        }
    }

    // ==================== ITEM PROTECTION ====================

    /**
     * Prevent non-residents from using items that modify blocks in private towns
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        org.bukkit.Material blockType = event.getClickedBlock().getType();

        // Check for interactable blocks that could modify the town
        if (isTownModifyingBlock(blockType)) {
            if (!permissionService.canSwitch(playerUuid, event.getClickedBlock().getX(),
                    event.getClickedBlock().getZ(), event.getClickedBlock().getWorld().getName())) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot interact with this block in this town!");
                plugin.getLogger().fine("Block interaction prevented - No interact permission: " + blockType);
            }
        }
    }

    /**
     * Prevent non-residents from opening containers in private towns
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Check if inventory is a container (chests, furnaces, etc.)
        if (isContainerInventory(event.getInventory().getType())) {
            if (!permissionService.canSwitch(playerUuid, player.getLocation().getBlockX(),
                    player.getLocation().getBlockZ(), player.getLocation().getWorld().getName())) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot open containers in this town!");
                plugin.getLogger().fine("Inventory open prevented - No interact permission");
            }
        }
    }

    // ==================== HANGING ENTITIES PROTECTION ====================

    /**
     * Prevent non-authorized players from breaking hanging entities (item frames, paintings, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player)) return;

        Player player = (Player) event.getRemover();
        UUID playerUuid = player.getUniqueId();

        int x = event.getEntity().getLocation().getBlockX();
        int z = event.getEntity().getLocation().getBlockZ();
        String world = event.getEntity().getWorld().getName();

        if (!permissionService.canInteractWithEntity(playerUuid, x, z, world)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot break hanging entities here!");
            plugin.getLogger().fine("Hanging entity break prevented - No permission at location");
        }
    }

    /**
     * Prevent non-authorized players from placing hanging entities (item frames, paintings, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        int x = event.getEntity().getLocation().getBlockX();
        int z = event.getEntity().getLocation().getBlockZ();
        String world = event.getEntity().getWorld().getName();

        if (!permissionService.canInteractWithEntity(playerUuid, x, z, world)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot place hanging entities here!");
            plugin.getLogger().fine("Hanging entity place prevented - No permission at location");
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if a block type can modify town structure
     */
    private boolean isTownModifyingBlock(org.bukkit.Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL, SHULKER_BOX, HOPPER,
            DISPENSER, DROPPER, BREWING_STAND, FURNACE, SMOKER, BLAST_FURNACE,
            ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, GRINDSTONE, SMITHING_TABLE,
            CARTOGRAPHY_TABLE, FLETCHING_TABLE, LOOM, STONECUTTER, BEACON,
            CONDUIT, RESPAWN_ANCHOR, BELL, LECTERN, COMPOSTER, CAMPFIRE,
            SOUL_CAMPFIRE, CAULDRON -> true;
            default -> false;
        };
    }

    /**
     * Check if an inventory type is a container
     */
    private boolean isContainerInventory(InventoryType inventoryType) {
        return inventoryType == InventoryType.CHEST ||
               inventoryType == InventoryType.FURNACE ||
               inventoryType == InventoryType.BREWING ||
               inventoryType == InventoryType.CRAFTING ||
               inventoryType == InventoryType.DISPENSER ||
               inventoryType == InventoryType.DROPPER ||
               inventoryType == InventoryType.HOPPER ||
               inventoryType == InventoryType.SHULKER_BOX ||
               inventoryType == InventoryType.BARREL ||
               inventoryType == InventoryType.BEACON ||
               inventoryType == InventoryType.ANVIL ||
               inventoryType == InventoryType.ENCHANTING ||
               inventoryType == InventoryType.SMOKER ||
               inventoryType == InventoryType.BLAST_FURNACE;
    }
}
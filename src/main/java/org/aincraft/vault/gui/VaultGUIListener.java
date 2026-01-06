package org.aincraft.vault.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.mintychochip.mint.Mint;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.VaultTransaction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles vault GUI interactions with permission checks.
 * Uses shared inventory instances to prevent duplication exploits.
 */
@Singleton
public class VaultGUIListener implements Listener {
    private final VaultService vaultService;
    private final SharedVaultInventoryManager inventoryManager;

    @Inject
    public VaultGUIListener(VaultService vaultService, SharedVaultInventoryManager inventoryManager) {
        this.vaultService = vaultService;
        this.inventoryManager = inventoryManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SharedVaultInventoryManager.SharedVaultInventory shared)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        boolean isVaultInventory = clickedInventory != null
                && clickedInventory.getHolder() instanceof SharedVaultInventoryManager.SharedVaultInventory;
        boolean isPlayerInventory = clickedInventory == player.getInventory();

        switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> {
                // Taking from vault = withdraw
                if (isVaultInventory && !shared.canWithdraw()) {
                    event.setCancelled(true);
                    Mint.sendMessage(player, "<error>You don't have permission to withdraw from this vault</error>");
                    return;
                }
                if (isVaultInventory) {
                    logWithdraw(shared, player, event.getCurrentItem(), event);
                }
            }
            case PLACE_ALL, PLACE_ONE, PLACE_SOME -> {
                // Placing in vault = deposit
                if (isVaultInventory && !shared.canDeposit()) {
                    event.setCancelled(true);
                    Mint.sendMessage(player, "<error>You don't have permission to deposit to this vault</error>");
                    return;
                }
                if (isVaultInventory) {
                    logDeposit(shared, player, event.getCursor());
                }
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                // Shift-click: direction depends on which inventory was clicked
                if (isPlayerInventory) {
                    // Shift-click from player inventory = deposit
                    if (!shared.canDeposit()) {
                        event.setCancelled(true);
                        Mint.sendMessage(player, "<error>You don't have permission to deposit to this vault</error>");
                        return;
                    }
                    logDeposit(shared, player, event.getCurrentItem());
                } else if (isVaultInventory) {
                    // Shift-click from vault = withdraw
                    if (!shared.canWithdraw()) {
                        event.setCancelled(true);
                        Mint.sendMessage(player, "<error>You don't have permission to withdraw from this vault</error>");
                        return;
                    }
                    logWithdraw(shared, player, event.getCurrentItem(), event);
                }
            }
            case SWAP_WITH_CURSOR -> {
                if (isVaultInventory) {
                    // Swapping requires both permissions
                    if (!shared.canDeposit() || !shared.canWithdraw()) {
                        event.setCancelled(true);
                        Mint.sendMessage(player, "<error>You don't have permission to use this vault</error>");
                        return;
                    }
                    logDeposit(shared, player, event.getCursor());
                    logWithdraw(shared, player, event.getCurrentItem(), event);
                }
            }
            case HOTBAR_SWAP -> {
                if (isVaultInventory) {
                    // Hotbar swap also requires both permissions
                    if (!shared.canDeposit() || !shared.canWithdraw()) {
                        event.setCancelled(true);
                        Mint.sendMessage(player, "<error>You don't have permission to use this vault</error>");
                        return;
                    }
                    // Log both transactions
                    ItemStack currentItem = event.getCurrentItem();
                    ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                    if (currentItem != null && !currentItem.getType().isAir()) {
                        logWithdraw(shared, player, currentItem, event);
                    }
                    if (hotbarItem != null && !hotbarItem.getType().isAir()) {
                        logDeposit(shared, player, hotbarItem);
                    }
                }
            }
            default -> {
                // Other actions don't need special handling
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SharedVaultInventoryManager.SharedVaultInventory shared)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if any slots are in the vault inventory
        boolean affectsVault = event.getRawSlots().stream()
                .anyMatch(slot -> slot < shared.getInventory().getSize());

        if (affectsVault && !shared.canDeposit()) {
            event.setCancelled(true);
            Mint.sendMessage(player, "<error>You don't have permission to deposit to this vault</error>");
        } else if (affectsVault) {
            // Log deposit for dragged items
            ItemStack newItems = event.getOldCursor().clone();
            int remaining = event.getCursor() != null ? event.getCursor().getAmount() : 0;
            newItems.setAmount(newItems.getAmount() - remaining);
            if (newItems.getAmount() > 0) {
                logDeposit(shared, player, newItems);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SharedVaultInventoryManager.SharedVaultInventory shared)) {
            return;
        }

        // Notify manager that a player closed - it will save if no viewers remain
        inventoryManager.onPlayerClose(shared.getVault().getId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.hasBlock()) {
            return;
        }

        if (event.getClickedBlock().getType() != Material.CHEST) {
            return;
        }

        // Check if this is a vault chest
        VaultService.VaultAccessResult result = vaultService.openVaultByChest(
                event.getPlayer(),
                event.getClickedBlock().getLocation()
        );

        if (!result.isVault()) {
            // Not a vault chest, let normal behavior proceed
            return;
        }

        // Cancel the normal chest opening
        event.setCancelled(true);

        if (!result.success()) {
            Mint.sendMessage(event.getPlayer(), "<error>Vault not found</error>");
            return;
        }

        // Get or create shared inventory for this vault
        SharedVaultInventoryManager.SharedVaultInventory shared = inventoryManager.getOrCreateInventory(
                result.vault(), result.canDeposit(), result.canWithdraw());

        event.getPlayer().openInventory(shared.getInventory());
    }

    private void logDeposit(SharedVaultInventoryManager.SharedVaultInventory shared, Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        VaultTransaction transaction = new VaultTransaction(
                shared.getVault().getId(),
                player.getUniqueId(),
                VaultTransaction.TransactionType.DEPOSIT,
                item.getType(),
                item.getAmount()
        );
        vaultService.logTransaction(transaction);
    }

    private void logWithdraw(SharedVaultInventoryManager.SharedVaultInventory shared, Player player, ItemStack item, InventoryClickEvent event) {
        if (item == null || item.getType().isAir()) {
            return;
        }

        // Calculate actual amount withdrawn based on action
        int amount = switch (event.getAction()) {
            case PICKUP_ALL, MOVE_TO_OTHER_INVENTORY -> item.getAmount();
            case PICKUP_HALF -> (item.getAmount() + 1) / 2; // rounds up
            case PICKUP_ONE -> 1;
            case PICKUP_SOME -> {
                int maxStack = item.getMaxStackSize();
                int cursorAmount = event.getCursor() != null ? event.getCursor().getAmount() : 0;
                yield Math.min(item.getAmount(), maxStack - cursorAmount);
            }
            default -> item.getAmount();
        };

        VaultTransaction transaction = new VaultTransaction(
                shared.getVault().getId(),
                player.getUniqueId(),
                VaultTransaction.TransactionType.WITHDRAW,
                item.getType(),
                amount
        );
        vaultService.logTransaction(transaction);
    }
}

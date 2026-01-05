package org.aincraft.vault;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.vault.gui.SharedVaultInventoryManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command component for vault operations.
 * Handles: /g vault [open|info|log|destroy]
 */
public class VaultComponent {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");
    private static final int TRANSACTION_PAGE_SIZE = 10;
    private static final int UUID_DISPLAY_LENGTH = 8;

    private final VaultService vaultService;
    private final SharedVaultInventoryManager inventoryManager;

    public VaultComponent(VaultService vaultService, SharedVaultInventoryManager inventoryManager) {
        this.vaultService = vaultService;
        this.inventoryManager = inventoryManager;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 2) {
            // Default to open
            return handleOpen(player);
        }

        String subCommand = args[1].toLowerCase();

        return switch (subCommand) {
            case "open" -> handleOpen(player);
            case "info" -> handleInfo(player);
            case "destroy" -> handleDestroy(player, args);
            default -> {
                showHelp(player);
                yield true;
            }
        };
    }

    private void showHelp(Player player) {
        Messages.send(player, MessageKey.LIST_HEADER, "Vault Commands");
        Messages.send(player, MessageKey.INFO_HEADER, "/g vault - Open the guild vault");
        Messages.send(player, MessageKey.INFO_HEADER, "/g vault info - Show vault information");
        Messages.send(player, MessageKey.INFO_HEADER, "/g vault destroy confirm - Destroy the vault (owner only)");
        Messages.send(player, MessageKey.INFO_HEADER, "/g log vault [page] - View vault transaction history");
    }

    private boolean handleOpen(Player player) {
        VaultService.VaultAccessResult result = vaultService.openVault(player);

        if (!result.success()) {
            Messages.send(player, MessageKey.VAULT_NOT_FOUND);
            return true;
        }

        // Use shared inventory manager to prevent duplication exploits
        SharedVaultInventoryManager.SharedVaultInventory shared = inventoryManager.getOrCreateInventory(
                result.vault(), result.canDeposit(), result.canWithdraw());
        player.openInventory(shared.getInventory());

        Messages.send(player, MessageKey.VAULT_OPENED);

        return true;
    }

    private boolean handleInfo(Player player) {
        Optional<Vault> vaultOpt = vaultService.getGuildVault(player.getUniqueId());

        if (vaultOpt.isEmpty()) {
            Messages.send(player, MessageKey.VAULT_NOT_FOUND);
            return true;
        }

        Vault vault = vaultOpt.get();

        Messages.send(player, MessageKey.LIST_HEADER, "Guild Vault");
        Messages.send(player, MessageKey.INFO_HEADER, "Location: " +
                vault.getWorld() + " at " + vault.getOriginX() + ", " + vault.getOriginY() + ", " + vault.getOriginZ());
        Messages.send(player, MessageKey.INFO_HEADER, "Created: " + new Date(vault.getCreatedAt()).toString());
        Messages.send(player, MessageKey.INFO_HEADER, "Storage: " + Vault.STORAGE_SIZE + " slots");

        // Count items in vault
        int itemCount = 0;
        if (vault.getContents() != null) {
            for (var item : vault.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    itemCount++;
                }
            }
        }
        Messages.send(player, MessageKey.INFO_HEADER, "Used Slots: " + itemCount + "/" + Vault.STORAGE_SIZE);

        return true;
    }

    private boolean handleDestroy(Player player, String[] args) {
        Optional<Vault> vaultOpt = vaultService.getGuildVault(player.getUniqueId());

        if (vaultOpt.isEmpty()) {
            Messages.send(player, MessageKey.VAULT_NOT_FOUND);
            return true;
        }

        Vault vault = vaultOpt.get();

        if (!vaultService.canDestroyVault(player.getUniqueId(), vault)) {
            Messages.send(player, MessageKey.VAULT_OWNER_ONLY);
            return true;
        }

        // Require confirmation
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Messages.send(player, MessageKey.VAULT_CONFIRM_DESTROY);
            return true;
        }

        // Drop items
        Location dropLoc = new Location(
                org.bukkit.Bukkit.getWorld(vault.getWorld()),
                vault.getOriginX(),
                vault.getOriginY(),
                vault.getOriginZ()
        );

        if (dropLoc.getWorld() != null && vault.getContents() != null) {
            for (var item : vault.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, item);
                }
            }
        }

        vaultService.destroyVault(vault);
        Messages.send(player, MessageKey.VAULT_DESTROYED);

        return true;
    }
}

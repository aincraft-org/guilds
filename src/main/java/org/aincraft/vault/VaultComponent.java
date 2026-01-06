package org.aincraft.vault;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(sender, "<error>Player only</error>");
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
        Mint.sendMessage(player, "<primary>=== Vault Commands ===</primary>");
        Mint.sendMessage(player, "<info>/g vault - Open the guild vault</info>");
        Mint.sendMessage(player, "<info>/g vault info - Show vault information</info>");
        Mint.sendMessage(player, "<info>/g vault destroy confirm - Destroy the vault (owner only)</info>");
        Mint.sendMessage(player, "<info>/g log vault [page] - View vault transaction history</info>");
    }

    private boolean handleOpen(Player player) {
        VaultService.VaultAccessResult result = vaultService.openVault(player);

        if (!result.success()) {
            Mint.sendMessage(player, "<error>Vault not found</error>");
            return true;
        }

        // Use shared inventory manager to prevent duplication exploits
        SharedVaultInventoryManager.SharedVaultInventory shared = inventoryManager.getOrCreateInventory(
                result.vault(), result.canDeposit(), result.canWithdraw());
        player.openInventory(shared.getInventory());

        Mint.sendMessage(player, "<success>Vault opened</success>");

        return true;
    }

    private boolean handleInfo(Player player) {
        Optional<Vault> vaultOpt = vaultService.getGuildVault(player.getUniqueId());

        if (vaultOpt.isEmpty()) {
            Mint.sendMessage(player, "<error>Vault not found</error>");
            return true;
        }

        Vault vault = vaultOpt.get();

        Mint.sendMessage(player, "<primary>=== Guild Vault ===</primary>");
        Mint.sendMessage(player, "<info>Location: <secondary>" + vault.getWorld() + "</secondary> at <accent>" + vault.getOriginX() + "</accent>, <accent>" + vault.getOriginY() + "</accent>, <accent>" + vault.getOriginZ() + "</accent></info>");
        Mint.sendMessage(player, "<info>Created: <secondary>" + new Date(vault.getCreatedAt()).toString() + "</secondary></info>");
        Mint.sendMessage(player, "<info>Storage: <accent>" + Vault.STORAGE_SIZE + "</accent> slots</info>");

        // Count items in vault
        int itemCount = 0;
        if (vault.getContents() != null) {
            for (var item : vault.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    itemCount++;
                }
            }
        }
        Mint.sendMessage(player, "<info>Used Slots: <primary>" + itemCount + "</primary>/<accent>" + Vault.STORAGE_SIZE + "</accent></info>");

        return true;
    }

    private boolean handleDestroy(Player player, String[] args) {
        Optional<Vault> vaultOpt = vaultService.getGuildVault(player.getUniqueId());

        if (vaultOpt.isEmpty()) {
            Mint.sendMessage(player, "<error>Vault not found</error>");
            return true;
        }

        Vault vault = vaultOpt.get();

        if (!vaultService.canDestroyVault(player.getUniqueId(), vault)) {
            Mint.sendMessage(player, "<warning>Only the vault owner can destroy it</warning>");
            return true;
        }

        // Require confirmation
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Mint.sendMessage(player, "<warning>Use /g vault destroy confirm to destroy the vault</warning>");
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
        Mint.sendMessage(player, "<success>Vault destroyed</success>");

        return true;
    }
}

package org.aincraft.vault;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use vault commands"));
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
        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Vault Commands", ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g vault", "Open the guild vault"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g vault info", "Show vault information"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g vault destroy confirm", "Destroy the vault (owner only)"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE, "/g log vault [page]", "View vault transaction history"));
    }

    private boolean handleOpen(Player player) {
        VaultService.VaultAccessResult result = vaultService.openVault(player);

        if (!result.success()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.errorMessage()));
            return true;
        }

        // Use shared inventory manager to prevent duplication exploits
        SharedVaultInventoryManager.SharedVaultInventory shared = inventoryManager.getOrCreateInventory(
                result.vault(), result.canDeposit(), result.canWithdraw());
        player.openInventory(shared.getInventory());

        // Show permission info
        StringBuilder perms = new StringBuilder();
        if (result.canDeposit()) perms.append("deposit");
        if (result.canDeposit() && result.canWithdraw()) perms.append(", ");
        if (result.canWithdraw()) perms.append("withdraw");

        player.sendMessage(MessageFormatter.deserialize(
                "<gray>Vault opened. Permissions: <gold>" + perms + "</gold></gray>"));

        return true;
    }

    private boolean handleInfo(Player player) {
        Optional<Vault> vaultOpt = vaultService.getGuildVault(player.getUniqueId());

        if (vaultOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Your guild does not have a vault"));
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Build a 3x3x3 iron block structure with a chest in the center inside a bank subregion.</gray>"));
            return true;
        }

        Vault vault = vaultOpt.get();

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Guild Vault", ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Location",
                vault.getWorld() + " at " + vault.getOriginX() + ", " + vault.getOriginY() + ", " + vault.getOriginZ()));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Created",
                new Date(vault.getCreatedAt()).toString()));
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Storage",
                Vault.STORAGE_SIZE + " slots"));

        // Count items in vault
        int itemCount = 0;
        if (vault.getContents() != null) {
            for (var item : vault.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    itemCount++;
                }
            }
        }
        player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Used Slots",
                itemCount + "/" + Vault.STORAGE_SIZE));

        return true;
    }

    private boolean handleDestroy(Player player, String[] args) {
        Optional<Vault> vaultOpt = vaultService.getGuildVault(player.getUniqueId());

        if (vaultOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Your guild does not have a vault"));
            return true;
        }

        Vault vault = vaultOpt.get();

        if (!vaultService.canDestroyVault(player.getUniqueId(), vault)) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only the guild owner can destroy the vault"));
            return true;
        }

        // Require confirmation
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.WARNING,
                    "This will destroy the vault and drop all items!"));
            player.sendMessage(MessageFormatter.deserialize(
                    "<gray>Type <yellow>/g vault destroy confirm</yellow> to confirm.</gray>"));
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
        player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
                "Vault destroyed. All items have been dropped at the vault location."));

        return true;
    }
}

package org.aincraft.commands.components;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.mintychochip.mint.Mint;
import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultService;
import org.aincraft.vault.VaultTransaction;
import org.bukkit.entity.Player;

/**
 * Displays vault transaction history.
 * Extracted from VaultComponent to support unified /g log command structure.
 */
public class VaultLogSubcomponent {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");
    private static final int TRANSACTION_PAGE_SIZE = 10;
    private static final int UUID_DISPLAY_LENGTH = 8;

    private final VaultService vaultService;

    public VaultLogSubcomponent(VaultService vaultService) {
        this.vaultService = Objects.requireNonNull(vaultService, "Vault service cannot be null");
    }

    public boolean execute(Player player, String[] args) {
        Optional<Vault> vaultOpt = vaultService.getGuildVault(player.getUniqueId());

        if (vaultOpt.isEmpty()) {
            Mint.sendMessage(player, "<error>Vault not found</error>");
            return true;
        }

        Vault vault = vaultOpt.get();
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        int offset = (page - 1) * TRANSACTION_PAGE_SIZE;

        List<VaultTransaction> transactions = vaultService.getRecentTransactions(vault.getId(), TRANSACTION_PAGE_SIZE * page);

        if (transactions.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
            return true;
        }

        Mint.sendMessage(player, "<info>Vault Transactions (Page <primary>" + page + "</primary>)</info>");

        int shown = 0;
        for (int i = offset; i < transactions.size() && shown < TRANSACTION_PAGE_SIZE; i++) {
            VaultTransaction tx = transactions.get(i);
            String playerName = org.bukkit.Bukkit.getOfflinePlayer(tx.playerId()).getName();
            if (playerName == null) playerName = tx.playerId().toString().substring(0, UUID_DISPLAY_LENGTH);

            String action = tx.action() == VaultTransaction.TransactionType.DEPOSIT ? "<success>+</success>" : "<error>-</error>";
            String itemName = tx.itemType().name().toLowerCase().replace("_", " ");
            String time = DATE_FORMAT.format(new Date(tx.timestamp()));

            Mint.sendMessage(player, "<neutral>" + time + " " + action + " <secondary>" + tx.amount() + "x " + itemName + "</secondary> by " + playerName + "</neutral>");
            shown++;
        }

        if (transactions.size() > page * TRANSACTION_PAGE_SIZE) {
            Mint.sendMessage(player, "<neutral>Use <secondary>/g log vault " + (page + 1) + "</secondary> for more</neutral>");
        }

        return true;
    }
}

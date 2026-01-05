package org.aincraft.commands.components;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(player, MessageKey.VAULT_NOT_FOUND);
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
            Messages.send(player, MessageKey.LIST_EMPTY);
            return true;
        }

        player.sendMessage(Messages.get(MessageKey.LIST_HEADER, "Vault Transactions (Page " + page + ")"));

        int shown = 0;
        for (int i = offset; i < transactions.size() && shown < TRANSACTION_PAGE_SIZE; i++) {
            VaultTransaction tx = transactions.get(i);
            String playerName = org.bukkit.Bukkit.getOfflinePlayer(tx.playerId()).getName();
            if (playerName == null) playerName = tx.playerId().toString().substring(0, UUID_DISPLAY_LENGTH);

            String action = tx.action() == VaultTransaction.TransactionType.DEPOSIT ? "<green>+</green>" : "<red>-</red>";
            String itemName = tx.itemType().name().toLowerCase().replace("_", " ");
            String time = DATE_FORMAT.format(new Date(tx.timestamp()));

            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>" + time + " " + action + " <gold>" + tx.amount() + "x " + itemName + "</gold> by " + playerName + "</gray>"));
            shown++;
        }

        if (transactions.size() > page * TRANSACTION_PAGE_SIZE) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gray>Use <yellow>/g log vault " + (page + 1) + "</yellow> for more</gray>"));
        }

        return true;
    }
}

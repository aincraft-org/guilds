package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.EconomyService;
import org.aincraft.towny.services.TownService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Implementation of EconomyService wrapping the Vault Economy API.
 * Falls back to town balance field when Vault is not installed.
 */
@Singleton
public class EconomyServiceImpl implements EconomyService {

    private final TownyPlugin plugin;
    private final DatabaseManager databaseManager;
    private final TownService townService;

    private Economy vaultEconomy = null;
    private boolean vaultAvailable = false;

    @Inject
    public EconomyServiceImpl(TownyPlugin plugin, DatabaseManager databaseManager, TownService townService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.townService = townService;
        setupVault();
    }

    /**
     * Attempt to hook into Vault's economy provider.
     */
    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found — economy features will use town bank fallback.");
            vaultAvailable = false;
            return;
        }

        try {
            var rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                vaultEconomy = rsp.getProvider();
                vaultAvailable = vaultEconomy != null;
                if (vaultAvailable) {
                    plugin.getLogger().info("Vault economy integration enabled via " + vaultEconomy.getName());
                }
            } else {
                plugin.getLogger().warning("Vault found but no economy provider registered.");
                vaultAvailable = false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to hook into Vault economy: " + e.getMessage(), e);
            vaultAvailable = false;
        }
    }

    @Override
    public boolean isAvailable() {
        return vaultAvailable;
    }

    @Override
    public String format(double amount) {
        if (vaultAvailable && vaultEconomy != null) {
            return vaultEconomy.format(amount);
        }
        return String.format("$%.2f", amount);
    }

    // ── Player operations ──────────────────────────────────────────────

    @Override
    public void depositPlayer(UUID playerUuid, double amount) {
        if (amount <= 0) return;

        if (vaultAvailable) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            vaultEconomy.depositPlayer(player, amount);
            logTransaction(null, playerUuid.toString(), "deposit_player", amount, "Player deposit");
        }
        // No fallback needed — without Vault, player balances don't exist
    }

    @Override
    public void withdrawPlayer(UUID playerUuid, double amount) {
        if (amount <= 0) return;

        if (vaultAvailable) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            EconomyResponse resp = vaultEconomy.withdrawPlayer(player, amount);
            if (resp.transactionSuccess()) {
                logTransaction(null, playerUuid.toString(), "withdraw_player", amount, "Player withdrawal");
            }
        }
    }

    @Override
    public double getPlayerBalance(UUID playerUuid) {
        if (vaultAvailable) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return vaultEconomy.getBalance(player);
        }
        return 0.0;
    }

    @Override
    public boolean has(UUID playerUuid, double amount) {
        if (vaultAvailable) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            return vaultEconomy.has(player, amount);
        }
        return false;
    }

    // ── Town operations ────────────────────────────────────────────────

    @Override
    public void depositTown(String townId, double amount) {
        if (amount <= 0) return;

        if (vaultAvailable) {
            // Use Vault bank for the town
            // Vault's bank feature uses player UUIDs, so we derive a fake UUID from town ID
            OfflinePlayer townHolder = Bukkit.getOfflinePlayer(getTownBankUuid(townId));
            vaultEconomy.bankDeposit(townId, amount);
            logTransaction(townId, null, "deposit_town", amount, "Town bank deposit");
        } else {
            // Fallback: update town balance field directly
            townService.getTownById(townId).ifPresent(town -> {
                town.addFunds(amount);
                townService.updateTown(town);
            });
        }
    }

    @Override
    public void withdrawTown(String townId, double amount) {
        if (amount <= 0) return;

        if (vaultAvailable) {
            EconomyResponse resp = vaultEconomy.bankWithdraw(townId, amount);
            if (resp.transactionSuccess()) {
                logTransaction(townId, null, "withdraw_town", amount, "Town bank withdrawal");
            }
        } else {
            townService.getTownById(townId).ifPresent(town -> {
                if (town.withdrawFunds(amount)) {
                    townService.updateTown(town);
                }
            });
        }
    }

    @Override
    public double getTownBalance(String townId) {
        if (vaultAvailable) {
            return vaultEconomy.bankBalance(townId).balance;
        }

        // Fallback: use town's balance field
        return townService.getTownById(townId)
                .map(Town::getBalance)
                .orElse(0.0);
    }

    @Override
    public boolean townHas(String townId, double amount) {
        return getTownBalance(townId) >= amount;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Derive a consistent UUID for a town's bank account.
     */
    private UUID getTownBankUuid(String townId) {
        return UUID.nameUUIDFromBytes(("town-bank:" + townId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Log a transaction to the economy_transactions table for auditing.
     */
    private void logTransaction(String townId, String playerUuid, String type, double amount, String description) {
        String sql = """
            INSERT INTO economy_transactions (id, town_id, player_uuid, type, amount, description, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, townId);
            ps.setString(3, playerUuid);
            ps.setString(4, type);
            ps.setDouble(5, amount);
            ps.setString(6, description);
            ps.setString(7, LocalDateTime.now().toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to log economy transaction: " + e.getMessage(), e);
        }
    }
}

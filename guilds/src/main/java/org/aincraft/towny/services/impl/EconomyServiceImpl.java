package org.aincraft.towny.services.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.services.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Implementation of EconomyService wrapping the Vault Economy API.
 * Economy operations are unavailable until Vault and an economy provider are present.
 */
@Singleton
public class EconomyServiceImpl implements EconomyService {

    private final TownyPlugin plugin;
    private final DatabaseManager databaseManager;

    private Economy vaultEconomy = null;
    private boolean vaultAvailable = false;

    @Inject
    public EconomyServiceImpl(TownyPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        setupVault();
    }

    /**
     * Attempt to hook into Vault's economy provider.
     */
    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found — economy operations are unavailable.");
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
        if (amount <= 0 || !vaultAvailable) return;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        EconomyResponse resp = vaultEconomy.depositPlayer(player, amount);
        if (resp.transactionSuccess()) {
            logTransaction(null, playerUuid.toString(), "deposit_player", amount, "Player deposit");
        }
    }

    @Override
    public void withdrawPlayer(UUID playerUuid, double amount) {
        if (amount <= 0 || !vaultAvailable) return;

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        EconomyResponse resp = vaultEconomy.withdrawPlayer(player, amount);
        if (resp.transactionSuccess()) {
            logTransaction(null, playerUuid.toString(), "withdraw_player", amount, "Player withdrawal");
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
        if (amount <= 0 || !vaultAvailable) return;

        EconomyResponse resp = vaultEconomy.bankDeposit(townId, amount);
        if (resp.transactionSuccess()) {
            logTransaction(townId, null, "deposit_town", amount, "Town bank deposit");
        }
    }

    @Override
    public void withdrawTown(String townId, double amount) {
        if (amount <= 0 || !vaultAvailable) return;

        EconomyResponse resp = vaultEconomy.bankWithdraw(townId, amount);
        if (resp.transactionSuccess()) {
            logTransaction(townId, null, "withdraw_town", amount, "Town bank withdrawal");
        }
    }

    @Override
    public double getTownBalance(String townId) {
        if (vaultAvailable) {
            return vaultEconomy.bankBalance(townId).balance;
        }
        return 0.0;
    }

    @Override
    public boolean townHas(String townId, double amount) {
        return vaultAvailable && getTownBalance(townId) >= amount;
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

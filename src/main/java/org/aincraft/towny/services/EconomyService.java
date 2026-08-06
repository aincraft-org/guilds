package org.aincraft.towny.services;

import java.util.UUID;

/**
 * Service interface for Vault economy integration.
 * Provides player and town balance operations with graceful fallback when Vault is absent.
 */
public interface EconomyService {

    /**
     * Check if Vault economy is available.
     */
    boolean isAvailable();

    /**
     * Format a monetary amount using Vault's economy formatter.
     */
    String format(double amount);

    // ── Player operations ──────────────────────────────────────────────

    /**
     * Deposit money into a player's account.
     */
    void depositPlayer(UUID playerUuid, double amount);

    /**
     * Withdraw money from a player's account.
     */
    void withdrawPlayer(UUID playerUuid, double amount);

    /**
     * Get a player's current balance.
     */
    double getPlayerBalance(UUID playerUuid);

    /**
     * Check if a player has at least the given amount.
     */
    boolean has(UUID playerUuid, double amount);

    // ── Town operations ────────────────────────────────────────────────

    /**
     * Deposit money into a town's bank.
     */
    void depositTown(String townId, double amount);

    /**
     * Withdraw money from a town's bank.
     */
    void withdrawTown(String townId, double amount);

    /**
     * Get a town's current bank balance.
     */
    double getTownBalance(String townId);

    /**
     * Check if a town has at least the given amount.
     */
    boolean townHas(String townId, double amount);
}

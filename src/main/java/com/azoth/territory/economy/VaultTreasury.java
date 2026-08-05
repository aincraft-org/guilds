package com.azoth.territory.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Vault-backed payment rail. Territory balances live in Vault bank accounts
 * identified by territory id and owned by a stable Azoth service account.
 * Settlement withdraws first, deposits second, and refunds on deposit failure.
 */
public final class VaultTreasury implements PaymentRail {

    private static final String SERVICE_OWNER = "AzothTerritory-Service";

    private final Economy economy;
    private final Function<UUID, OfflinePlayer> offlinePlayerLookup;

    public VaultTreasury(Economy economy, Function<UUID, OfflinePlayer> offlinePlayerLookup) {
        this.economy = economy;
        this.offlinePlayerLookup = Objects.requireNonNull(offlinePlayerLookup, "offlinePlayerLookup");
    }

    /**
     * Provisions missing territory banks. Existing banks are left untouched.
     * Returns the number of territory ids that could not be provisioned.
     */
    public int provisionTerritories(Collection<String> territoryIds) {
        if (territoryIds == null || territoryIds.isEmpty()) {
            return 0;
        }
        if (economy == null || !economy.hasBankSupport()) {
            return territoryIds.size();
        }

        int failed = 0;
        for (String territoryId : territoryIds) {
            if (territoryId == null || territoryId.isBlank()) {
                failed++;
                continue;
            }
            EconomyResponse existing = economy.bankBalance(territoryId.trim());
            if (success(existing)) {
                continue;
            }
            EconomyResponse created = economy.createBank(territoryId.trim(), SERVICE_OWNER);
            if (!success(created)) {
                failed++;
            }
        }
        return failed;
    }

    @Override
    public SettlementResult settle(UUID payerId, String territoryId, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive and finite, got " + amount);
        }
        if (economy == null || !economy.hasBankSupport()) {
            return result(PaymentRail.SettlementStatus.VAULT_UNAVAILABLE);
        }
        if (territoryId == null || territoryId.isBlank() || !bankExists(territoryId.trim())) {
            return result(PaymentRail.SettlementStatus.VAULT_UNAVAILABLE);
        }
        if (payerId == null) {
            return result(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
        }

        OfflinePlayer payer = offlinePlayerLookup.apply(payerId);
        if (payer == null || !economy.hasAccount(payer)) {
            return result(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
        }
        if (!economy.has(payer, amount)) {
            return result(PaymentRail.SettlementStatus.INSUFFICIENT_FUNDS);
        }

        EconomyResponse withdrawal = economy.withdrawPlayer(payer, amount);
        if (!success(withdrawal)) {
            return result(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
        }

        EconomyResponse deposit = economy.bankDeposit(territoryId.trim(), amount);
        if (success(deposit)) {
            return result(PaymentRail.SettlementStatus.SETTLED);
        }

        EconomyResponse refund = economy.depositPlayer(payer, amount);
        if (success(refund)) {
            return result(PaymentRail.SettlementStatus.COMPENSATED_FAILURE);
        }
        return result(PaymentRail.SettlementStatus.RECONCILIATION_REQUIRED);
    }

    @Override
    public boolean available() {
        return economy != null && economy.hasBankSupport();
    }

    /** Returns a provisioned territory bank balance, or zero if unavailable. */
    public double bankBalance(String territoryId) {
        if (economy == null || !economy.hasBankSupport() || territoryId == null || territoryId.isBlank()) {
            return 0.0;
        }
        EconomyResponse response = economy.bankBalance(territoryId.trim());
        return success(response) ? response.balance : 0.0;
    }

    private boolean bankExists(String territoryId) {
        return success(economy.bankBalance(territoryId));
    }

    private static boolean success(EconomyResponse response) {
        return response != null && response.transactionSuccess();
    }

    private static SettlementResult result(PaymentRail.SettlementStatus status) {
        return new SettlementResult(status);
    }
}

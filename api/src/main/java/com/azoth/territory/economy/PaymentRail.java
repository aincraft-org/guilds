package com.azoth.territory.economy;

import java.util.UUID;

/**
 * Money-movement seam. An implementation owns the complete settlement sequence,
 * including compensation and reconciliation when a transfer is only partially successful.
 */
public interface PaymentRail {

    /** Outcomes of settling a payment through the rail. */
    enum SettlementStatus {
        /** The payer had insufficient funds. */
        INSUFFICIENT_FUNDS,
        /** The payer was unavailable. */
        PAYER_UNAVAILABLE,
        /** The payment provider was unavailable. */
        PROVIDER_UNAVAILABLE,
        /** The payment was settled. */
        SETTLED,
        /** The failed payment was compensated. */
        COMPENSATED_FAILURE,
        /** Reconciliation is required. */
        RECONCILIATION_REQUIRED
    }

    /**
     * Settles a tax transfer from a payer to a territory treasury.
     *
     * @param payerId identifier of the payer
     * @param territoryId identifier of the territory
     * @param amount transfer amount
     * @return the settlement outcome
     */
    SettlementResult settle(UUID payerId, String territoryId, double amount);

    /**
     * Withdraws money from a territory treasury to an external sink.
     * This operation never touches a payer account.
     *
     * @param territoryId identifier of the territory
     * @param amount debit amount
     * @return the treasury debit outcome
     */
    TreasuryDebitResult debitTreasury(String territoryId, double amount);

    /**
     * Whether this rail can currently move money.
     *
     * @return {@code true} when the rail can move money
     */
    boolean available();
}

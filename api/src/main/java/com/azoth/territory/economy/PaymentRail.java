package com.azoth.territory.economy;

import java.util.UUID;

/**
 * Money-movement seam. An implementation owns the complete settlement sequence,
 * including compensation and reconciliation when a transfer is only partially successful.
 */
public interface PaymentRail {

    enum SettlementStatus {
        INSUFFICIENT_FUNDS,
        PAYER_UNAVAILABLE,
        VAULT_UNAVAILABLE,
        SETTLED,
        COMPENSATED_FAILURE,
        RECONCILIATION_REQUIRED
    }

    /** Settles a tax transfer from a payer to a territory treasury. */
    SettlementResult settle(UUID payerId, String territoryId, double amount);

    /**
     * Withdraws money from a territory treasury to an external sink.
     * This operation never touches a payer account.
     */
    TreasuryDebitResult debitTreasury(String territoryId, double amount);

    /** Whether this rail can currently move money. */
    boolean available();
}

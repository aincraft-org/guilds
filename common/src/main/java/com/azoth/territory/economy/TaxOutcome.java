package com.azoth.territory.economy;

/** Result of a sale report. Outcomes before settlement mutate nothing. */
public enum TaxOutcome {
    TAXED,
    SIMULATED_TAXED,
    NO_TERRITORY,
    NO_GOVERNMENT,
    NO_TAX,
    UNKNOWN_GOOD,
    INVALID_QUANTITY,
    INVALID_AMOUNT,
    PAYER_UNAVAILABLE,
    VAULT_UNAVAILABLE,
    MINT_UNAVAILABLE,
    MINT_REJECTED,
    MINT_RECONCILIATION_REQUIRED,
    INSUFFICIENT_FUNDS,
    SETTLEMENT_FAILED,
    SETTLEMENT_RECONCILIATION_REQUIRED
}

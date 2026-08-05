package com.azoth.territory.economy;

/** Result of a sale report. Outcomes before settlement mutate nothing. */
public enum TaxOutcome {
    TAXED,
    SIMULATED_TAXED,
    NO_TERRITORY,
    NO_GOVERNMENT,
    NO_TAX,
    UNKNOWN_GOOD,
    INVALID_AMOUNT,
    PAYER_UNAVAILABLE,
    VAULT_UNAVAILABLE,
    INSUFFICIENT_FUNDS,
    SETTLEMENT_FAILED,
    SETTLEMENT_RECONCILIATION_REQUIRED
}

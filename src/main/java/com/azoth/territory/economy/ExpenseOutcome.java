package com.azoth.territory.economy;

/** Public outcomes of a settlement treasury expense request. */
public enum ExpenseOutcome {
    DEBITED,
    ALREADY_APPLIED,
    NO_TERRITORY,
    NO_GOVERNMENT,
    INSUFFICIENT_FUNDS,
    VAULT_UNAVAILABLE,
    INVALID_AMOUNT,
    RECONCILIATION_REQUIRED
}

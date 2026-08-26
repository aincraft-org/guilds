package dev.mintychochip.territory.economy;

/** Outcomes of withdrawing money from a settlement treasury. */
public enum TreasuryDebitStatus {
    /** The treasury was debited. */
    DEBITED,
    /** The treasury had insufficient funds. */
    INSUFFICIENT_FUNDS,
    /** The payment provider was unavailable. */
    PROVIDER_UNAVAILABLE,
    /** The debit amount was invalid. */
    INVALID_AMOUNT
}

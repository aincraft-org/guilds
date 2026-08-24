package org.aincraft.guilds.territory.economy;

/** Outcomes of withdrawing money from a settlement treasury. */
public enum TreasuryDebitStatus {
    DEBITED,
    INSUFFICIENT_FUNDS,
    PROVIDER_UNAVAILABLE,
    INVALID_AMOUNT
}

package dev.mintychochip.territory.economy;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of an asynchronous tax settlement attempt.
 *
 * @param status settlement outcome
 * @param diagnosticCode optional diagnostic code
 * @param receiptIdentifier optional receipt identifier
 */
public record AsyncSettlementResult(Status status, Optional<String> diagnosticCode,
                                    Optional<String> receiptIdentifier) {
    /** Settlement outcomes for an asynchronous tax settlement. */
    public enum Status {
        /** The settlement was committed. */
        COMMITTED,
        /** The payer had insufficient funds. */
        INSUFFICIENT_FUNDS,
        /** The settlement provider was unavailable. */
        UNAVAILABLE,
        /** The settlement was rejected. */
        REJECTED,
        /** Reconciliation is required to determine the final outcome. */
        RECONCILIATION_REQUIRED
    }

    /** Normalizes optional settlement diagnostics and receipt identifiers. */
    public AsyncSettlementResult {
        diagnosticCode = diagnosticCode.map(AsyncSettlementResult::normalize);
        receiptIdentifier = receiptIdentifier.map(AsyncSettlementResult::normalize);
    }

    /**
     * Creates a settlement result from nullable diagnostic and receipt strings.
     *
     * @param status settlement outcome
     * @param diagnosticCode nullable diagnostic code
     * @param receiptIdentifier nullable receipt identifier
     */
    public AsyncSettlementResult(Status status, String diagnosticCode, String receiptIdentifier) {
        this(status, Optional.ofNullable(normalize(diagnosticCode)),
                Optional.ofNullable(normalize(receiptIdentifier)));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        var normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

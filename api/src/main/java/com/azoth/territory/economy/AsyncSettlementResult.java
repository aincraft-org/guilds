package com.azoth.territory.economy;

import java.util.Objects;
import java.util.Optional;

/** Immutable outcome of an asynchronous tax settlement attempt. */
public record AsyncSettlementResult(Status status, Optional<String> diagnosticCode,
                                    Optional<String> receiptIdentifier) {
    public enum Status {
        COMMITTED,
        INSUFFICIENT_FUNDS,
        UNAVAILABLE,
        REJECTED,
        RECONCILIATION_REQUIRED
    }

    public AsyncSettlementResult {
        diagnosticCode = diagnosticCode.map(AsyncSettlementResult::normalize);
        receiptIdentifier = receiptIdentifier.map(AsyncSettlementResult::normalize);
    }

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

package com.azoth.territory.storage;

import java.util.Objects;

/**
 * Opaque serialized item payload carried by the storage layer.
 * <p>
 * The territory plugin never interprets {@code payloadJson}; it only stores,
 * copies, and validates the payload as an opaque blob. {@code schema} identifies
 * the payload format and {@code fingerprint} a stable content hash preserved
 * end to end (e.g. by PostgreSQL slots/audit tables).
 */
public record OpaqueItemPayload(String schema, String payloadJson, String fingerprint) {

    public OpaqueItemPayload {
        schema = trimRequired(schema, "schema");
        payloadJson = requireNonBlank(payloadJson, "payloadJson");
        fingerprint = trimRequired(fingerprint, "fingerprint");
    }

    private static String trimRequired(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

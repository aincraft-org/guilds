package dev.mintychochip.territory.storage;

/**
 * Versioned item bytes supplied by the Paper codec. Territory never inspects
 * item semantics.
 *
 * @param schema codec identifier
 * @param fingerprint integrity hash from the codec
 * @param payload opaque encoded item
 */
public record OpaqueItemPayload(String schema, String fingerprint, String payload) {
    /**
     * Validates required payload fields.
     *
     * @throws IllegalArgumentException if any field is blank
     */
    public OpaqueItemPayload {
        schema = requireText(schema, "schema");
        fingerprint = requireText(fingerprint, "fingerprint");
        payload = requireText(payload, "payload");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}

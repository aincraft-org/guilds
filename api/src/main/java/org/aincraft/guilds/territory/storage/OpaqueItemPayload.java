package org.aincraft.guilds.territory.storage;

import java.util.Objects;

/**
 * Paper-free opaque item payload stored in guild storage slots.
 */
public record OpaqueItemPayload(String schema, String fingerprint, String payload) {
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

    @Override
    public String toString() {
        return "OpaqueItemPayload{schema='" + schema + "', fingerprint='" + fingerprint + "'}";
    }
}

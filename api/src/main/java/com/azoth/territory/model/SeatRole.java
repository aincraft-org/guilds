package com.azoth.territory.model;

/**
 * Role of a seat within a government form's sovereignty structure.
 * <p>
 * One role per distinct form — no parallel labels for the same seat pattern.
 */
public enum SeatRole {
    /** Monarchy: the single ruler. */
    SOVEREIGN,
    /** Oligarchy: council member. */
    COUNCILOR,
    /** Democracy: elected representative. */
    REPRESENTATIVE;

    /** Parses a persisted seat role.
     * @param raw persisted role name
     * @return the matching role
     * @throws IllegalArgumentException if {@code raw} is blank or invalid
     */
    public static SeatRole fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("seat role is required");
        }
        return SeatRole.valueOf(raw.trim().toUpperCase());
    }
}

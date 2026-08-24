package org.aincraft.guilds.territory.model;

/**
 * How sovereignty is structured for a territory government.
 * <p>
 * Only forms that differ in decision mechanics are included — not flavor renames
 * of the same structure (e.g. no separate dictatorship/theocracy clones of monarchy).
 * Seat schemas and policy decision rules differ by form; see {@link Government}
 * and {@link PolicyRules}.
 */
public enum GovernmentForm {
    /** No formal authority; cannot adopt policies. */
    ANARCHY,
    /** Single sovereign seat; policies by decree. */
    MONARCHY,
    /** Multi-seat council; majority of filled council seats. */
    OLIGARCHY,
    /** Multi-seat elected representatives; majority of filled seats (optional terms). */
    DEMOCRACY;

    public static GovernmentForm fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANARCHY;
        }
        String key = raw.trim().toUpperCase();
        // Legacy persist label
        if ("NONE".equals(key)) {
            return ANARCHY;
        }
        return GovernmentForm.valueOf(key);
    }

    public boolean isAssigned() {
        return this != ANARCHY;
    }

    /**
     * How this form adopts policies.
     */
    public DecisionStyle decisionStyle() {
        return switch (this) {
            case ANARCHY -> DecisionStyle.NONE;
            case MONARCHY -> DecisionStyle.DECREE;
            case OLIGARCHY, DEMOCRACY -> DecisionStyle.MAJORITY_SEATS;
        };
    }

    /**
     * Seat role that forms the electorate / decisive authority for this form.
     */
    public SeatRole authorityRole() {
        return switch (this) {
            case ANARCHY -> throw new IllegalStateException("ANARCHY has no authority role");
            case MONARCHY -> SeatRole.SOVEREIGN;
            case OLIGARCHY -> SeatRole.COUNCILOR;
            case DEMOCRACY -> SeatRole.REPRESENTATIVE;
        };
    }

    public enum DecisionStyle {
        /** No decisions allowed. */
        NONE,
        /** Single seat-holder decree (pass or reject). */
        DECREE,
        /** Majority among filled seats of the authority role. */
        MAJORITY_SEATS
    }
}

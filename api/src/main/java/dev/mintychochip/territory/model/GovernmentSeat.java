package dev.mintychochip.territory.model;

import java.util.Objects;
import java.util.Optional;

/**
 * One seat in a territory government.
 * <p>
 * {@code holderId} is an opaque string (player UUID, company id, faction id, …)
 * for later systems to wire — this plugin does not resolve Bukkit entities.
 */
public final class GovernmentSeat {
    private final String seatId;
    private final SeatRole role;
    private final String holderId;
    /**
     * Optional term end epoch millis (democracy placeholder); null = no term metadata.
     */
    private final Long termEndsAtEpochMs;

    /**
     * Creates a seat without term-end metadata.
     *
     * @param seatId stable identifier for the seat
     * @param role role assigned to the seat
     * @param holderId optional opaque identifier of the current holder
     */
    public GovernmentSeat(String seatId, SeatRole role, String holderId) {
        this(seatId, role, holderId, null);
    }

    /**
     * Creates a government seat.
     *
     * @param seatId stable identifier for the seat
     * @param role role assigned to the seat
     * @param holderId optional opaque identifier of the current holder
     * @param termEndsAtEpochMs optional term end as epoch milliseconds
     */
    public GovernmentSeat(String seatId, SeatRole role, String holderId, Long termEndsAtEpochMs) {
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId is required");
        }
        this.seatId = seatId.trim();
        this.role = Objects.requireNonNull(role, "role");
        this.holderId = holderId == null || holderId.isBlank() ? null : holderId.trim();
        this.termEndsAtEpochMs = termEndsAtEpochMs;
    }

    /**
     * Returns this seat's identifier.
     *
     * @return stable seat identifier
     */
    public String seatId() {
        return seatId;
    }

    /**
     * Returns this seat's role.
     *
     * @return seat role
     */
    public SeatRole role() {
        return role;
    }

    /**
     * Returns the holder identifier, when occupied.
     *
     * @return optional opaque holder identifier
     */
    public Optional<String> holderId() {
        return Optional.ofNullable(holderId);
    }

    /**
     * Indicates whether this seat has no holder.
     *
     * @return {@code true} when vacant
     */
    public boolean isVacant() {
        return holderId == null;
    }

    /**
     * Returns the optional term end.
     *
     * @return term end epoch milliseconds, when configured
     */
    public Optional<Long> termEndsAtEpochMs() {
        return Optional.ofNullable(termEndsAtEpochMs);
    }

    /**
     * Returns a copy with a different holder.
     *
     * @param newHolderId optional opaque identifier for the new holder
     * @return seat containing the new holder
     */
    public GovernmentSeat withHolder(String newHolderId) {
        return new GovernmentSeat(seatId, role, newHolderId, termEndsAtEpochMs);
    }

    /**
     * Returns a copy with a different term end.
     *
     * @param epochMs optional term end epoch milliseconds
     * @return seat containing the new term metadata
     */
    public GovernmentSeat withTermEndsAt(Long epochMs) {
        return new GovernmentSeat(seatId, role, holderId, epochMs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GovernmentSeat that)) {
            return false;
        }
        return seatId.equals(that.seatId)
                && role == that.role
                && Objects.equals(holderId, that.holderId)
                && Objects.equals(termEndsAtEpochMs, that.termEndsAtEpochMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seatId, role, holderId, termEndsAtEpochMs);
    }

    @Override
    public String toString() {
        return "GovernmentSeat{id='" + seatId + "', role=" + role
                + ", holder=" + holderId + ", termEnds=" + termEndsAtEpochMs + '}';
    }
}

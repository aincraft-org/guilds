package org.aincraft.guilds.territory.model;

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

    public GovernmentSeat(String seatId, SeatRole role, String holderId) {
        this(seatId, role, holderId, null);
    }

    public GovernmentSeat(String seatId, SeatRole role, String holderId, Long termEndsAtEpochMs) {
        if (seatId == null || seatId.isBlank()) {
            throw new IllegalArgumentException("seatId is required");
        }
        this.seatId = seatId.trim();
        this.role = Objects.requireNonNull(role, "role");
        this.holderId = holderId == null || holderId.isBlank() ? null : holderId.trim();
        this.termEndsAtEpochMs = termEndsAtEpochMs;
    }

    public String seatId() {
        return seatId;
    }

    public SeatRole role() {
        return role;
    }

    public Optional<String> holderId() {
        return Optional.ofNullable(holderId);
    }

    public boolean isVacant() {
        return holderId == null;
    }

    public Optional<Long> termEndsAtEpochMs() {
        return Optional.ofNullable(termEndsAtEpochMs);
    }

    public GovernmentSeat withHolder(String newHolderId) {
        return new GovernmentSeat(seatId, role, newHolderId, termEndsAtEpochMs);
    }

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

package org.aincraft.guilds.territory.building;

import java.util.Objects;
import org.aincraft.guilds.territory.model.SettlementFacility;

/**
 * Runtime reconciliation result for a transport facility.
 *
 * <p>The persisted facility remains an immutable location record.  This value
 * deliberately carries no state that is written back to that record; it is a
 * snapshot of whether the record can currently be used.</p>
 */
public final class FastTravelFacilityState {
    private final SettlementFacility facility;
    private final boolean active;
    private final AnchorStatus reason;

    public FastTravelFacilityState(SettlementFacility facility, boolean active, AnchorStatus reason) {
        this.facility = Objects.requireNonNull(facility, "facility");
        this.active = active;
        this.reason = Objects.requireNonNull(reason, "reason");
        if (active && reason != AnchorStatus.ACTIVE) {
            throw new IllegalArgumentException("active facility state must have ACTIVE reason");
        }
        if (!active && reason == AnchorStatus.ACTIVE) {
            throw new IllegalArgumentException("inactive facility state must have a failure reason");
        }
    }

    public SettlementFacility facility() {
        return facility;
    }

    public boolean active() {
        return active;
    }

    public boolean isActive() {
        return active;
    }

    public AnchorStatus reason() {
        return reason;
    }

    public AnchorStatus status() {
        return reason;
    }

    public static FastTravelFacilityState active(SettlementFacility facility) {
        return new FastTravelFacilityState(facility, true, AnchorStatus.ACTIVE);
    }

    public static FastTravelFacilityState inactive(SettlementFacility facility, AnchorStatus reason) {
        return new FastTravelFacilityState(facility, false, reason);
    }
}

package org.aincraft.guilds.territory.model;

import java.util.Optional;

/** Compatibility modes used to authorize fast-travel endpoints. */
public enum FastTravelMode {
    WAYSTONE,
    CRYSTAL,
    LOCAL_TERMINAL,
    BOAT,
    AIRSHIP;

    /**
     * Returns the transport mode represented by a facility type.
     * Non-transport facilities do not have a compatibility mode.
     */
    public static Optional<FastTravelMode> fromFacilityType(FacilityType type) {
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case WAYSTONE -> Optional.of(WAYSTONE);
            case GUILD_CRYSTAL -> Optional.of(CRYSTAL);
            case TELEPORT_TERMINAL -> Optional.of(LOCAL_TERMINAL);
            case BOAT -> Optional.of(BOAT);
            case AIRSHIP -> Optional.of(AIRSHIP);
            case TRADING_POST, STORAGE, BANK -> Optional.empty();
        };
    }
}

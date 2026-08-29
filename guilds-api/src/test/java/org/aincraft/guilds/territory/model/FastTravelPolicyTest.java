package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastTravelPolicyTest {

    @Test
    void mapsEveryFacilityTypeToItsCompatibilityMode() {
        assertEquals(FastTravelMode.WAYSTONE,
                FastTravelMode.fromFacilityType(FacilityType.WAYSTONE).orElseThrow());
        assertEquals(FastTravelMode.CRYSTAL,
                FastTravelMode.fromFacilityType(FacilityType.GUILD_CRYSTAL).orElseThrow());
        assertEquals(FastTravelMode.LOCAL_TERMINAL,
                FastTravelMode.fromFacilityType(FacilityType.TELEPORT_TERMINAL).orElseThrow());
        assertEquals(FastTravelMode.BOAT,
                FastTravelMode.fromFacilityType(FacilityType.BOAT).orElseThrow());
        assertEquals(FastTravelMode.AIRSHIP,
                FastTravelMode.fromFacilityType(FacilityType.AIRSHIP).orElseThrow());
        assertTrue(FastTravelMode.fromFacilityType(FacilityType.TRADING_POST).isEmpty());
        assertTrue(FastTravelMode.fromFacilityType(FacilityType.STORAGE).isEmpty());
        assertTrue(FastTravelMode.fromFacilityType(FacilityType.BANK).isEmpty());
    }

    @Test
    void defaultsAreUnlimitedAndPermitRemoteTransportModes() {
        FastTravelPolicy policy = FastTravelPolicy.defaults();

        assertEquals(FastTravelPolicy.UNLIMITED_QUOTA, policy.quotaFor(FacilityType.BOAT));
        assertEquals(FastTravelPolicy.UNLIMITED_QUOTA, policy.quotaFor(FacilityType.GUILD_CRYSTAL));
        assertTrue(policy.allowsCrossTerritory(FastTravelMode.CRYSTAL));
        assertTrue(policy.allowsCrossTerritory(FastTravelMode.BOAT));
        assertTrue(policy.allowsCrossTerritory(FastTravelMode.AIRSHIP));
        assertFalse(policy.allowsCrossTerritory(FastTravelMode.LOCAL_TERMINAL));
    }

    @Test
    void copiesCollectionsAndKeepsQuotaAndBoundarySettingsIndependent() {
        EnumMap<FacilityType, Integer> quotas = new EnumMap<>(FacilityType.class);
        quotas.put(FacilityType.BOAT, 2);
        EnumSet<FastTravelMode> modes = EnumSet.of(FastTravelMode.BOAT);
        FastTravelPolicy policy = new FastTravelPolicy(quotas, modes);

        quotas.put(FacilityType.BOAT, 9);
        modes.add(FastTravelMode.AIRSHIP);

        assertEquals(2, policy.quotaFor(FacilityType.BOAT));
        assertTrue(policy.allowsCrossTerritory(FastTravelMode.BOAT));
        assertFalse(policy.allowsCrossTerritory(FastTravelMode.AIRSHIP));
        assertThrows(UnsupportedOperationException.class,
                () -> policy.facilityQuotas().put(FacilityType.BOAT, 3));
        assertThrows(UnsupportedOperationException.class,
                () -> policy.crossTerritoryModes().add(FastTravelMode.AIRSHIP));
    }

    @Test
    void rejectsInvalidQuotaAndBoundaryEntries() {
        assertThrows(NullPointerException.class,
                () -> new FastTravelPolicy(null, Set.of()));
        assertThrows(NullPointerException.class,
                () -> new FastTravelPolicy(Map.of(FacilityType.BOAT, null), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new FastTravelPolicy(Map.of(FacilityType.BOAT, -1), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new FastTravelPolicy(Map.of(FacilityType.BANK, 1), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new FastTravelPolicy(Map.of(), Set.of(FastTravelMode.LOCAL_TERMINAL)));
        assertThrows(NullPointerException.class,
                () -> new FastTravelPolicy(Map.of(), Set.of((FastTravelMode) null)));
    }
}

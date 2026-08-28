package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryFastTravelPolicyTest {

    @Test
    void oldConstructorPathsUseTheCompatibleDefaultPolicy() {
        Territory territory = territory();

        assertEquals(FastTravelPolicy.defaults(), territory.fastTravelPolicy());
        assertEquals(FastTravelPolicy.UNLIMITED_QUOTA,
                territory.fastTravelPolicy().quotaFor(FacilityType.AIRSHIP));
        assertTrueCrossTerritoryDefaults(territory.fastTravelPolicy());
    }

    @Test
    void policySurvivesTerritoryCopiesAndCanBeReplaced() {
        FastTravelPolicy policy = new FastTravelPolicy(
                Map.of(FacilityType.BOAT, 2), Set.of(FastTravelMode.BOAT));
        Territory territory = territory().withFastTravelPolicy(policy);

        assertEquals(policy, territory.fastTravelPolicy());
        assertEquals(policy, territory.withGoverningGuild("guild-1").fastTravelPolicy());
        assertEquals(policy, territory.withoutGoverningGuild().fastTravelPolicy());
        assertEquals(policy, territory.withPolicies(List.of()).fastTravelPolicy());
        assertEquals(policy, territory.withFastTravelPolicy(policy).fastTravelPolicy());
    }

    private static Territory territory() {
        return new Territory("t1", "Territory", "world", Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100))));
    }

    private static void assertTrueCrossTerritoryDefaults(FastTravelPolicy policy) {
        assertTrue(policy.allowsCrossTerritory(FastTravelMode.CRYSTAL));
        assertTrue(policy.allowsCrossTerritory(FastTravelMode.BOAT));
        assertTrue(policy.allowsCrossTerritory(FastTravelMode.AIRSHIP));
    }
}

package com.azoth.territory.building;

import com.azoth.territory.model.FacilityType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingPlacementSessionsTest {
    @Test
    void sessionExpiresCancelsAndCompletesIndependently() {
        BuildingPlacementSessions sessions = new BuildingPlacementSessions(60_000L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        sessions.begin(first, FacilityType.WAYSTONE, "north", "North Gate", 1_000L);
        sessions.begin(second, FacilityType.TRADING_POST, "market", "Market", 1_000L);

        assertEquals("north", sessions.current(first, 60_999L).orElseThrow().id());
        assertTrue(sessions.current(first, 61_000L).isEmpty());
        assertTrue(sessions.current(second, 2_000L).isPresent());
        sessions.complete(second);
        assertTrue(sessions.current(second, 2_000L).isEmpty());
        assertFalse(sessions.cancel(second));
    }

    @Test
    void beginReplacesExistingSessionAndNormalizesId() {
        BuildingPlacementSessions sessions = new BuildingPlacementSessions(1_000L);
        UUID player = UUID.randomUUID();
        sessions.begin(player, FacilityType.WAYSTONE, "North", "", 0L);
        sessions.begin(player, FacilityType.TRADING_POST, "market", "Central Market", 1L);

        BuildingPlacement placement = sessions.current(player, 2L).orElseThrow();
        assertEquals(FacilityType.TRADING_POST, placement.type());
        assertEquals("market", placement.id());
        assertEquals("Central Market", placement.name());
        assertTrue(sessions.cancel(player));
    }
}

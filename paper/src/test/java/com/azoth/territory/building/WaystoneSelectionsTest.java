package com.azoth.territory.building;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaystoneSelectionsTest {
    @Test
    void selectionExpiresAndClears() {
        WaystoneSelections selections = new WaystoneSelections(1_000L);
        UUID player = UUID.randomUUID();
        selections.select(player, "north", 5_000L);
        assertEquals("north", selections.origin(player, 5_999L).orElseThrow());
        assertTrue(selections.origin(player, 6_000L).isEmpty());
        selections.select(player, "south", 6_000L);
        selections.clear(player);
        assertTrue(selections.origin(player, 6_001L).isEmpty());
    }
}

package com.azoth.territory;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.model.SeatRole;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minimal consumer of the public government + territory API:
 * exact form + seat holder id for a fixture (not merely non-null).
 */
class GovernmentSmokeTest {

    @Test
    void publicApi_exactMonarchyFormAndSovereignHolder() {
        TerritoryRegistry registry = new TerritoryRegistry();
        Territory fixture = new Territory(
                "everfall-crown",
                "Everfall Crown",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(400, 0),
                        new BlockPos(400, 400),
                        new BlockPos(0, 400)
                )),
                List.of(),
                ZoneType.WILDERNESS,
                Government.monarchy("faction:everfall-royals")
        );
        registry.register(fixture);

        LookupResult hit = registry.resolve("world", 100, 100);
        assertTrue(hit.isContained());
        assertEquals("everfall-crown", hit.territoryId().orElse(null));
        assertEquals(GovernmentForm.MONARCHY, hit.governmentForm().orElse(null));
        assertEquals("faction:everfall-royals",
                hit.government().orElseThrow().sovereignHolderId().orElse(null));
        assertEquals(SeatRole.SOVEREIGN,
                hit.government().orElseThrow().seats().get(0).role());

        System.out.println("GOVERNMENT_SMOKE territoryId=" + hit.territoryId().orElseThrow()
                + " form=" + hit.governmentForm().orElseThrow()
                + " sovereignHolder=" + hit.government().orElseThrow().sovereignHolderId().orElseThrow()
                + " seatRole=" + hit.government().orElseThrow().seats().get(0).role());
    }

    @Test
    void governmentFormNamesOnPublicApiSurface() {
        // Static/surface check for later wiring
        assertEquals("MONARCHY", GovernmentForm.MONARCHY.name());
        assertEquals("OLIGARCHY", GovernmentForm.OLIGARCHY.name());
        assertEquals("DEMOCRACY", GovernmentForm.DEMOCRACY.name());
        assertEquals("ANARCHY", GovernmentForm.ANARCHY.name());
        assertEquals("com.azoth.territory.model.Government", Government.class.getName());
    }
}

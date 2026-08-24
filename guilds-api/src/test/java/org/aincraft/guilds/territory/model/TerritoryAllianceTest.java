package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Territory alliances are formed with an assigned government (not ANARCHY)
 * and optional member territory ids.
 */
class TerritoryAllianceTest {

    @Test
    void form_requiresAssignedGovernment() {
        Government democracy = Government.democracy(List.of("r1", "r2", "r3"));
        TerritoryAlliance alliance = TerritoryAlliance.form(
                "northern-pact",
                "Northern Pact",
                democracy
        );

        assertEquals("northern-pact", alliance.id());
        assertEquals("Northern Pact", alliance.name());
        assertEquals(GovernmentForm.DEMOCRACY, alliance.governmentForm());
        assertEquals(democracy, alliance.government());
        assertTrue(alliance.government().isAssigned());
        assertTrue(alliance.territoryIds().isEmpty());
    }

    @Test
    void form_withTerritoryMembers_preservesOrderAndDropsBlanks() {
        TerritoryAlliance alliance = TerritoryAlliance.form(
                "trade-league",
                "Trade League",
                Government.oligarchy(List.of("c1", "c2")),
                Arrays.asList("everfall", "  ", "windsward", null, "everfall")
        );

        assertEquals(GovernmentForm.OLIGARCHY, alliance.governmentForm());
        assertEquals(List.of("everfall", "windsward"), alliance.territoryIds());
        assertTrue(alliance.containsTerritory("everfall"));
        assertFalse(alliance.containsTerritory("brightwood"));
    }

    @Test
    void form_rejectsNoneOrNullGovernment() {
        assertThrows(IllegalArgumentException.class,
                () -> TerritoryAlliance.form("a1", "A", Government.anarchy()));
        assertThrows(IllegalArgumentException.class,
                () -> TerritoryAlliance.form("a1", "A", null));
    }

    @Test
    void form_rejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> TerritoryAlliance.form("  ", "Name", Government.monarchy("d1")));
        assertThrows(IllegalArgumentException.class,
                () -> TerritoryAlliance.form(null, "Name", Government.monarchy("d1")));
    }

    @Test
    void blankName_defaultsToId() {
        TerritoryAlliance alliance = TerritoryAlliance.form(
                "pact", null, Government.oligarchy(List.of("c1", "c2"))
        );
        assertEquals("pact", alliance.name());
    }

    @Test
    void withGovernment_replacesAssignedGovernment() {
        TerritoryAlliance alliance = TerritoryAlliance.form(
                "a", "A", Government.monarchy("k1"), List.of("t1")
        );
        TerritoryAlliance next = alliance.withGovernment(
                Government.democracy(List.of("r1", "r2", "r3"))
        );
        assertEquals(GovernmentForm.DEMOCRACY, next.governmentForm());
        assertEquals(List.of("t1"), next.territoryIds());
        assertEquals("a", next.id());
    }

    @Test
    void withGovernment_rejectsNone() {
        TerritoryAlliance alliance = TerritoryAlliance.form("a", "A", Government.monarchy("k1"));
        assertThrows(IllegalArgumentException.class,
                () -> alliance.withGovernment(Government.anarchy()));
    }

    @Test
    void withTerritory_addsAndIsIdempotent() {
        TerritoryAlliance alliance = TerritoryAlliance.form(
                "a", "A", Government.oligarchy(List.of("c1", "c2"))
        );
        TerritoryAlliance with = alliance.withTerritory("crownlands").withTerritory("crownlands");
        assertEquals(List.of("crownlands"), with.territoryIds());
        assertFalse(alliance.containsTerritory("crownlands"));
    }

    @Test
    void withoutTerritory_removesMember() {
        TerritoryAlliance alliance = TerritoryAlliance.form(
                "a", "A",
                Government.oligarchy(List.of("c1", "c2", "c3")),
                List.of("t1", "t2")
        );
        TerritoryAlliance next = alliance.withoutTerritory("t1");
        assertEquals(List.of("t2"), next.territoryIds());
        assertTrue(alliance.containsTerritory("t1"));
    }

    @Test
    void equals_byValue() {
        Government g = Government.monarchy("k");
        TerritoryAlliance a = TerritoryAlliance.form("id", "Name", g, List.of("t1"));
        TerritoryAlliance b = TerritoryAlliance.form("id", "Name", g, List.of("t1"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

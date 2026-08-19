package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives real {@link Government} factories — form structure must differ by form.
 */
class GovernmentTest {

    @Test
    void monarchy_singleSovereignSeat() {
        Government g = Government.monarchy("player:king-arthur");
        assertEquals(GovernmentForm.MONARCHY, g.form());
        assertEquals(1, g.seatCount());
        assertEquals(SeatRole.SOVEREIGN, g.seats().get(0).role());
        assertEquals("player:king-arthur", g.sovereignHolderId().orElseThrow());
        assertEquals(List.of("player:king-arthur"), g.holderIds());
        assertTrue(g.seatsByRole(SeatRole.COUNCILOR).isEmpty());
        assertTrue(g.seatsByRole(SeatRole.REPRESENTATIVE).isEmpty());
    }

    @Test
    void oligarchy_multiCouncilSeats() {
        Government g = Government.oligarchy(List.of("company:a", "company:b", "company:c"));
        assertEquals(GovernmentForm.OLIGARCHY, g.form());
        assertTrue(g.seatCount() >= Government.MIN_OLIGARCHY_SEATS);
        assertEquals(3, g.seatCount()); // default 3 with 3 holders
        for (GovernmentSeat s : g.seats()) {
            assertEquals(SeatRole.COUNCILOR, s.role());
        }
        assertEquals(List.of("company:a", "company:b", "company:c"), g.holderIds());
        assertTrue(g.sovereignHolderId().isEmpty());
        assertEquals(3, g.seatsByRole(SeatRole.COUNCILOR).size());
    }

    @Test
    void democracy_representativesWithOptionalTerms() {
        long term = 1_700_000_000_000L;
        Government g = Government.democracy(
                3,
                List.of("player:rep1", "player:rep2"),
                List.of(term, term + 1000)
        );
        assertEquals(GovernmentForm.DEMOCRACY, g.form());
        assertEquals(3, g.seatCount());
        for (GovernmentSeat s : g.seats()) {
            assertEquals(SeatRole.REPRESENTATIVE, s.role());
        }
        assertEquals("player:rep1", g.seats().get(0).holderId().orElseThrow());
        assertEquals(term, g.seats().get(0).termEndsAtEpochMs().orElseThrow());
        assertTrue(g.seats().get(2).isVacant());
        assertTrue(g.seats().get(2).termEndsAtEpochMs().isEmpty());
        assertEquals(3, g.seatsByRole(SeatRole.REPRESENTATIVE).size());
        assertTrue(g.seatsByRole(SeatRole.SOVEREIGN).isEmpty());
    }

    @Test
    void formsAreDistinguishable() {
        Government m = Government.monarchy("x");
        Government o = Government.oligarchy(List.of("a", "b"));
        Government d = Government.democracy(List.of("r1"));
        Government n = Government.anarchy();
        assertEquals(GovernmentForm.MONARCHY, m.form());
        assertEquals(GovernmentForm.OLIGARCHY, o.form());
        assertEquals(GovernmentForm.DEMOCRACY, d.form());
        assertEquals(GovernmentForm.ANARCHY, n.form());
        assertFalse(n.isAssigned());
        assertTrue(m.isAssigned());
        // Structure differs: monarchy has SOVEREIGN only; oligarchy COUNCILOR; democracy REPRESENTATIVE
        assertEquals(SeatRole.SOVEREIGN, m.seats().get(0).role());
        assertEquals(SeatRole.COUNCILOR, o.seats().get(0).role());
        assertEquals(SeatRole.REPRESENTATIVE, d.seats().get(0).role());
        assertTrue(m.seatCount() != o.seatCount() || m.seats().get(0).role() != o.seats().get(0).role());
    }

    @Test
    void invalidStructure_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Government.of(GovernmentForm.MONARCHY, List.of(
                        new GovernmentSeat("a", SeatRole.SOVEREIGN, "1"),
                        new GovernmentSeat("b", SeatRole.SOVEREIGN, "2")
                )));
        assertThrows(IllegalArgumentException.class,
                () -> Government.of(GovernmentForm.OLIGARCHY, List.of(
                        new GovernmentSeat("only", SeatRole.COUNCILOR, "1")
                )));
        assertThrows(IllegalArgumentException.class,
                () -> Government.of(GovernmentForm.MONARCHY, List.of(
                        new GovernmentSeat("wrong", SeatRole.COUNCILOR, "1")
                )));
    }

    @Test
    void withSeatHolder_updatesOpaqueId() {
        Government g = Government.monarchy(null);
        assertTrue(g.sovereignHolderId().isEmpty());
        Government filled = g.withSeatHolder("sovereign", "player:new-king");
        assertEquals("player:new-king", filled.sovereignHolderId().orElseThrow());
        assertEquals(GovernmentForm.MONARCHY, filled.form());
    }
}

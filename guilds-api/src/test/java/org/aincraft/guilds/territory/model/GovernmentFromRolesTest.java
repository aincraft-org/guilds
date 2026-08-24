package org.aincraft.guilds.territory.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Role-derived government construction: the governance form IS the permission
 * structure — the same role holders map to different seats per form.
 */
class GovernmentFromRolesTest {

    private static final List<String> ROLES = List.of("mayor", "assistant-1", "assistant-2",
            "resident-1", "resident-2", "resident-3");

    @Test
    void monarchy_usesFirstIdAsSovereign() {
        Government g = Government.fromRoles(GovernmentForm.MONARCHY, ROLES);
        assertEquals(GovernmentForm.MONARCHY, g.form());
        assertEquals(1, g.seatCount());
        assertEquals(SeatRole.SOVEREIGN, g.seats().get(0).role());
        assertEquals("mayor", g.sovereignHolderId().orElseThrow());
    }

    @Test
    void monarchy_noIds_vacantSovereignSeat() {
        Government g = Government.fromRoles(GovernmentForm.MONARCHY, List.of());
        assertEquals(GovernmentForm.MONARCHY, g.form());
        assertTrue(g.seats().get(0).isVacant());
        assertTrue(PolicyRules.electorate(g).isEmpty());
    }

    @Test
    void oligarchy_allIdsBecomeCouncilors() {
        Government g = Government.fromRoles(GovernmentForm.OLIGARCHY, ROLES);
        assertEquals(GovernmentForm.OLIGARCHY, g.form());
        assertEquals(6, g.seatCount());
        assertEquals(SeatRole.COUNCILOR, g.seats().get(0).role());
        assertEquals(List.of("mayor", "assistant-1", "assistant-2",
                        "resident-1", "resident-2", "resident-3"),
                g.holderIds());
    }

    @Test
    void oligarchy_fewerThanTwoIds_stillTwoSeats() {
        Government g = Government.fromRoles(GovernmentForm.OLIGARCHY, List.of("mayor"));
        assertEquals(2, g.seatCount());
        assertEquals(List.of("mayor"), g.holderIds());
    }

    @Test
    void democracy_allIdsBecomeRepresentatives() {
        Government g = Government.fromRoles(GovernmentForm.DEMOCRACY, ROLES);
        assertEquals(GovernmentForm.DEMOCRACY, g.form());
        assertEquals(6, g.seatCount());
        assertEquals(SeatRole.REPRESENTATIVE, g.seats().get(0).role());
        assertEquals(ROLES, g.holderIds());
    }

    @Test
    void democracy_noIds_stillOneVacantSeat() {
        Government g = Government.fromRoles(GovernmentForm.DEMOCRACY, List.of());
        assertEquals(1, g.seatCount());
        assertTrue(g.seats().get(0).isVacant());
    }

    @Test
    void anarchy_noSeats() {
        Government g = Government.fromRoles(GovernmentForm.ANARCHY, ROLES);
        assertEquals(GovernmentForm.ANARCHY, g.form());
        assertEquals(0, g.seatCount());
        assertFalse(g.isAssigned());
    }

    @Test
    void blanksAreDropped() {
        Government g = Government.fromRoles(GovernmentForm.MONARCHY,
                java.util.Arrays.asList("  ", "mayor", null));
        assertEquals("mayor", g.sovereignHolderId().orElseThrow());
    }

    @Test
    void formChangesPermissionStructure() {
        // Same holder list: monarchy gives only the first formal authority,
        // democracy gives everyone authority.
        Government monarchy = Government.fromRoles(GovernmentForm.MONARCHY, ROLES);
        Government democracy = Government.fromRoles(GovernmentForm.DEMOCRACY, ROLES);
        assertTrue(PolicyRules.canDecree(monarchy, "mayor"));
        assertFalse(PolicyRules.canDecree(monarchy, "resident-1"));
        assertTrue(PolicyRules.canPropose(democracy, "resident-3"));
        assertFalse(PolicyRules.canPropose(monarchy, "resident-3"));
    }
}

package org.aincraft.guilds.territory.permission;

import org.aincraft.guilds.territory.model.Government;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Form-differing formal authority: anarchy grants none; monarchy only sovereign;
 * multi-seat forms only filled authority-role holders.
 */
class PermissionRulesTest {

    @Test
    void anarchy_grantsNoFormalAuthority() {
        Government g = Government.anarchy();
        for (SovereignAction action : SovereignAction.values()) {
            assertFalse(PermissionRules.allows(g, "anyone", action), action.name());
            assertFalse(PermissionRules.allows(g, null, action), action.name());
        }
    }

    @Test
    void monarchy_onlySovereignHasAuthority() {
        Government g = Government.monarchy("king:arthur");
        assertTrue(PermissionRules.allows(g, "king:arthur", SovereignAction.MANAGE_MEMBERSHIP));
        assertTrue(PermissionRules.allows(g, "king:arthur", SovereignAction.SET_POLICY));
        assertTrue(PermissionRules.allows(g, "king:arthur", SovereignAction.BREAK_BLOCK));
        assertTrue(PermissionRules.allows(g, "king:arthur", SovereignAction.PLACE_BLOCK));
        assertFalse(PermissionRules.allows(g, "peasant:bob", SovereignAction.BREAK_BLOCK));
        assertFalse(PermissionRules.allows(g, "peasant:bob", SovereignAction.MANAGE_MEMBERSHIP));
        assertFalse(PermissionRules.allows(g, "peasant:bob", SovereignAction.SET_POLICY));
    }

    @Test
    void oligarchy_filledCouncilorsOnly() {
        Government g = Government.oligarchy(List.of("c1", "c2", "c3"));
        assertTrue(PermissionRules.allows(g, "c2", SovereignAction.BREAK_BLOCK));
        assertTrue(PermissionRules.allows(g, "c1", SovereignAction.SET_POLICY));
        assertTrue(PermissionRules.allows(g, "c3", SovereignAction.MANAGE_MEMBERSHIP));
        assertFalse(PermissionRules.allows(g, "outsider", SovereignAction.PLACE_BLOCK));
        assertFalse(PermissionRules.allows(g, "outsider", SovereignAction.SET_POLICY));
    }

    @Test
    void democracy_filledRepresentativesOnly() {
        Government g = Government.democracy(3, List.of("r1", "r2"), null);
        assertTrue(PermissionRules.allows(g, "r1", SovereignAction.PLACE_BLOCK));
        assertTrue(PermissionRules.allows(g, "r2", SovereignAction.MANAGE_MEMBERSHIP));
        // vacant third seat — no phantom authority
        assertFalse(PermissionRules.allows(g, "r3", SovereignAction.BREAK_BLOCK));
        assertFalse(PermissionRules.allows(g, "outsider", SovereignAction.BREAK_BLOCK));
    }

    @Test
    void vacantMonarchySeat_grantsNothing() {
        Government g = Government.monarchy(null);
        assertFalse(PermissionRules.allows(g, "usurper", SovereignAction.BREAK_BLOCK));
        assertFalse(PermissionRules.allows(g, "usurper", SovereignAction.SET_POLICY));
    }
}

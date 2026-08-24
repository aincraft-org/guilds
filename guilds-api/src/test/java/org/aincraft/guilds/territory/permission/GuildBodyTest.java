package org.aincraft.guilds.territory.permission;

import org.aincraft.guilds.territory.model.Government;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Governing-entity DTO behavior: membership, effective member permissions,
 * public toggle, and empty-permissions safety.
 */
class GuildBodyTest {

    @Test
    void containsMember_trimsAndIgnoresBlanks() {
        GuildBody body = new GuildBody("t1", "Town", Government.monarchy("m1"),
                List.of("m1", "r1"), GuildToggles.defaults(), Map.of());

        assertTrue(body.containsMember("m1"));
        assertTrue(body.containsMember("  m1 "));
        assertFalse(body.containsMember("stranger"));
        assertFalse(body.containsMember(null));
        assertFalse(body.containsMember("  "));
    }

    @Test
    void permissionsOf_onlyForMembers() {
        MemberPermissions perms = MemberPermissions.of(List.of(
                SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK));
        GuildBody body = new GuildBody("t1", "Town", Government.monarchy("m1"),
                List.of("m1", "r1"), GuildToggles.defaults(),
                Map.of("m1", perms));

        assertTrue(body.permissionsOf("m1").isPresent());
        assertTrue(body.permissionsOf("r1").isEmpty());
        assertTrue(body.permissionsOf("stranger").isEmpty());
    }

    @Test
    void memberPermissions_allowsAndBypass() {
        MemberPermissions basic = MemberPermissions.of(List.of(
                SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK));
        assertTrue(basic.allows(SovereignAction.BREAK_BLOCK));
        assertTrue(basic.allows(SovereignAction.PLACE_BLOCK));
        assertFalse(basic.allows(SovereignAction.INTERACT));

        MemberPermissions bypass = MemberPermissions.fullBypass();
        assertTrue(bypass.allows(SovereignAction.BREAK_BLOCK));
        assertTrue(bypass.allows(SovereignAction.INTERACT));
        assertTrue(bypass.allows(SovereignAction.SET_POLICY));
    }

    @Test
    void emptyPermissions_safe() {
        MemberPermissions none = MemberPermissions.none();
        assertFalse(none.allows(SovereignAction.BREAK_BLOCK));
        assertFalse(none.allows(SovereignAction.INTERACT));
        // withGranted on an empty set must not throw (EnumSet.copyOf empty trap)
        MemberPermissions granted = none.withGranted(List.of(SovereignAction.INTERACT));
        assertTrue(granted.allows(SovereignAction.INTERACT));
        assertFalse(granted.allows(SovereignAction.BREAK_BLOCK));
        assertEquals(1, granted.grantedActions().size());
    }

    @Test
    void isPublic_reflectsToggle() {
        assertFalse(new GuildBody("t1", "T", Government.monarchy("m"),
                List.of("m"), GuildToggles.defaults(), Map.of()).isPublic());
        assertTrue(new GuildBody("t1", "T", Government.monarchy("m"),
                List.of("m"), new GuildToggles(false, false, false, true, true), Map.of()).isPublic());
    }

    @Test
    void allianceBody_membershipByGuild() {
        AllianceBody alliance = new AllianceBody("a1", "Pact", Government.monarchy("k1"),
                List.of("town-1", "town-2"));
        assertTrue(alliance.containsGuild("town-1"));
        assertTrue(alliance.containsGuild("  town-2  "));
        assertFalse(alliance.containsGuild("town-3"));
    }
}

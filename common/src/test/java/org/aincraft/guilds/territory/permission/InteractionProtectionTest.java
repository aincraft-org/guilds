package org.aincraft.guilds.territory.permission;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.ZoneType;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interaction-vector domain checks: interact gate, entity interact, boundary
 * crossing, PvP / friendly-fire, and teleport authorization.
 */
class InteractionProtectionTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private BlockProtection protection;

    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    @BeforeEach
    void setUp() {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        protection = new BlockProtection(governance);
    }

    /**
     * Guild-governed monarchy: the guild's mayor is the sovereign; the named
     * members get the basic build actions.
     */
    private void registerMonarchyTerritory(String id, int min, int max, String king) {
        territories.register(new Territory(
                id, id, "world", square(min, max),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), id + "-town"
        ));
        MemberPermissions member = MemberPermissions.of(List.of(
                SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT));
        source.putGuild(new GuildBody(id + "-town", "Town of " + id,
                Government.monarchy(king), List.of(king, "member:" + id),
                GuildToggles.defaults(), Map.of(king, member, "member:" + id, member)));
    }

    @Test
    void interact_uncontainedAnarchy_anyone() {
        assertTrue(protection.canInteract("world", 0, 0, "stranger"));
        assertTrue(protection.canInteractWithEntity("world", 0, 0, "stranger"));

        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertTrue(protection.canInteract("world", 10, 10, "outsider"));
        assertTrue(protection.canInteractWithEntity("world", 10, 10, "outsider"));
    }

    @Test
    void interact_monarchy_sovereignAndMembers() {
        registerMonarchyTerritory("crownlands", 0, 100, "king:arthur");

        assertTrue(protection.canInteract("world", 40, 40, "king:arthur"));
        assertTrue(protection.canInteractWithEntity("world", 40, 40, "king:arthur"));
        // Member of the governing guild (basic actions by role default)
        assertTrue(protection.canInteract("world", 40, 40, "member:crownlands"));
        assertTrue(protection.canInteractWithEntity("world", 40, 40, "member:crownlands"));
        // Outsider denied (guild not public)
        assertFalse(protection.canInteract("world", 40, 40, "peasant:bob"));
        assertFalse(protection.canInteractWithEntity("world", 40, 40, "peasant:bob"));
    }

    @Test
    void boundary_flowsOutOfClaim_blocked() {
        registerMonarchyTerritory("crownlands", 0, 100, "king:arthur");

        // Outside -> inside: flow entering governed land is blocked.
        assertTrue(protection.crossesBoundary("world", 200, 50, 50, 50));
        // Inside -> outside: draining governed land is also a boundary crossing.
        assertTrue(protection.crossesBoundary("world", 50, 50, 200, 50));
        // Both outside: unrestricted.
        assertFalse(protection.crossesBoundary("world", 200, 200, 300, 300));
        // Both inside: unrestricted.
        assertFalse(protection.crossesBoundary("world", 30, 30, 40, 40));
    }

    @Test
    void boundary_anarchyNoCrossBoundaryRule() {
        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertFalse(protection.crossesBoundary("world", 10, 10, 100, 100));
    }

    @Test
    void pvp_uncontainedAndAnarchy_allowed() {
        assertTrue(protection.allowsPvp("world", 0, 0, "a", "b"));

        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertTrue(protection.allowsPvp("world", 10, 10, "a", "b"));
    }

    @Test
    void pvp_governed_authorityCanDamage() {
        registerMonarchyTerritory("crownlands", 0, 100, "king:arthur");

        // Authority (sovereign) may attack; toggle off blocks member-vs-member.
        assertTrue(protection.allowsPvp("world", 40, 40, "king:arthur", "member:crownlands"));
        assertFalse(protection.allowsPvp("world", 40, 40, "member:crownlands", "king:arthur"));
    }

    @Test
    void teleport_uncontainedAnarchy_anyone() {
        assertTrue(protection.canTeleportInto("world", 0, 0, "stranger"));

        territories.register(new Territory(
                "free", "Free", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        assertTrue(protection.canTeleportInto("world", 10, 10, "outsider"));
    }

    @Test
    void teleport_governed_authorityAndMembersOnly() {
        registerMonarchyTerritory("crownlands", 0, 100, "king:arthur");

        assertTrue(protection.canTeleportInto("world", 40, 40, "king:arthur"));
        assertTrue(protection.canTeleportInto("world", 40, 40, "member:crownlands"));
        assertFalse(protection.canTeleportInto("world", 40, 40, "peasant:bob"));
    }
}

package org.aincraft.guilds.territory.permission;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.ZoneType;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public matrix smoke: monarchy (alliance-governed) / oligarchy (guild-governed)
 * / anarchy break-place outcomes on the shipped BlockProtection path.
 */
class FormPermissionsMatrixSmokeTest {

    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    @Test
    void matrix_monarchyOligarchyAnarchy_blockOutcomes() {
        TerritoryRegistry territories = new TerritoryRegistry();
        FakeGovernanceSource source = new FakeGovernanceSource();
        GovernanceRegistry governance = new GovernanceRegistry(territories, source);
        BlockProtection protection = new BlockProtection(governance);

        territories.register(new Territory(
                "mon-land", "Mon", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "crown-town"
        ));
        territories.register(new Territory(
                "oli-land", "Oli", "world", square(50, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "council-town"
        ));
        territories.register(new Territory(
                "an-land", "An", "world", square(100, 150),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));

        // Alliance (nation) governs mon-land with a monarchy (king seat)
        source.putGuild(new GuildBody("crown-town", "Crown Town",
                Government.monarchy("mayor:1"), List.of("mayor:1", "citizen:1"),
                GuildToggles.defaults(),
                Map.of("mayor:1", MemberPermissions.of(List.of(
                        SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT)),
                        "citizen:1", MemberPermissions.of(List.of(
                                SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT)))));
        source.putAlliance(new AllianceBody("royal", "Royal",
                Government.monarchy("king:1"), List.of("crown-town")));

        // Guild (guild) governs oli-land with an oligarchy (council seats)
        source.putGuild(new GuildBody("council-town", "Council Town",
                Government.oligarchy(List.of("c1", "c2", "c3")),
                List.of("c1", "c2", "c3", "member:1"), GuildToggles.defaults(),
                Map.of("c1", MemberPermissions.of(List.of(
                        SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT)),
                        "c2", MemberPermissions.of(List.of(
                                SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT)),
                        "c3", MemberPermissions.of(List.of(
                                SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT)),
                        "member:1", MemberPermissions.of(List.of(
                                SovereignAction.BREAK_BLOCK, SovereignAction.PLACE_BLOCK, SovereignAction.INTERACT)))));

        // Monarchy via alliance: king breaks, outsider denied, member allowed
        assertTrue(protection.canBreak("world", 25, 25, "king:1"));
        assertFalse(protection.canBreak("world", 25, 25, "outsider"));
        assertTrue(protection.canBreak("world", 25, 25, "citizen:1"));
        System.out.println("FORM_MATRIX monarchy break king=ALLOW member=ALLOW outsider=DENY");

        // Oligarchy via guild: councilor and member place, outsider denied
        assertTrue(protection.canPlace("world", 75, 75, "c2"));
        assertTrue(protection.canPlace("world", 75, 75, "member:1"));
        assertFalse(protection.canPlace("world", 75, 75, "outsider"));
        System.out.println("FORM_MATRIX oligarchy place councilor=ALLOW member=ALLOW outsider=DENY");

        // Anarchy: no formal lockdown
        assertTrue(protection.canBreak("world", 125, 125, "anyone"));
        assertFalse(PermissionRules.allows(
                Government.anarchy(), "anyone", SovereignAction.SET_POLICY));
        System.out.println("FORM_MATRIX anarchy break=ALLOW formalPolicy=DENY");

        // Uncontained
        assertTrue(protection.canBreak("world", 9000, 9000, "wanderer"));
        System.out.println("FORM_MATRIX uncontained break=ALLOW");
    }
}

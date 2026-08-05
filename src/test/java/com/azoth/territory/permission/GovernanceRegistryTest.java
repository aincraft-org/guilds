package com.azoth.territory.permission;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;
import com.azoth.territory.model.RegionGuild;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.TerritoryAlliance;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceRegistryTest {

    private TerritoryRegistry territories;
    private GovernanceRegistry governance;

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
        governance = new GovernanceRegistry(territories);
        territories.register(new Territory(
                "everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        territories.register(new Territory(
                "windsward", "Windsward", "world", square(100, 200),
                List.of(), ZoneType.WILDERNESS, Government.monarchy("local:lord")
        ));
    }

    @Test
    void resolveForTerritory_usesAllianceGovernmentWhenMember() {
        TerritoryAlliance pact = TerritoryAlliance.form(
                "northern-pact",
                "Northern Pact",
                Government.oligarchy(List.of("c1", "c2", "c3")),
                List.of("everfall", "windsward")
        );
        governance.putAlliance(pact);

        GoverningBody body = governance.resolveForTerritory("everfall");
        assertEquals(GoverningBody.Kind.ALLIANCE, body.kind());
        assertEquals("northern-pact", body.bodyId().orElseThrow());
        assertEquals(GovernmentForm.OLIGARCHY, body.governmentForm());
        assertEquals(GovernmentForm.OLIGARCHY,
                governance.effectiveGovernmentForTerritory("windsward").form());
    }

    @Test
    void resolveForTerritory_fallsBackToLocalGovernment() {
        GoverningBody body = governance.resolveForTerritory("windsward");
        assertEquals(GoverningBody.Kind.TERRITORY, body.kind());
        assertEquals(GovernmentForm.MONARCHY, body.governmentForm());
        assertEquals("local:lord",
                body.government().primaryAuthorityHolderId().orElseThrow());
    }

    @Test
    void resolveForHolder_usesGuildGovernment() {
        RegionGuild guild = RegionGuild.form(
                "iron-hand",
                "Iron Hand",
                Government.monarchy("guild:master"),
                List.of("player:a", "player:b")
        );
        governance.putGuild(guild);

        GoverningBody body = governance.resolveForHolder("player:b");
        assertEquals(GoverningBody.Kind.GUILD, body.kind());
        assertEquals(GovernmentForm.MONARCHY, body.governmentForm());
        assertTrue(PermissionRules.allows(
                body.government(), "guild:master", SovereignAction.MANAGE_MEMBERSHIP));
        assertFalse(PermissionRules.allows(
                body.government(), "player:b", SovereignAction.MANAGE_MEMBERSHIP));
    }

    @Test
    void resolveForHolder_unknownMember_isNone() {
        assertEquals(GoverningBody.Kind.NONE, governance.resolveForHolder("nobody").kind());
        assertFalse(governance.resolveForHolder("nobody").hasAssignedGovernment());
    }

    @Test
    void resolveAt_spatialToAllianceOrLocal() {
        governance.putAlliance(TerritoryAlliance.form(
                "pact", "Pact",
                Government.democracy(List.of("r1", "r2", "r3")),
                List.of("everfall")
        ));

        GoverningBody onEverfall = governance.resolveAt("world", 50, 50);
        assertEquals(GoverningBody.Kind.ALLIANCE, onEverfall.kind());
        assertEquals(GovernmentForm.DEMOCRACY, onEverfall.governmentForm());

        GoverningBody onWindsward = governance.resolveAt("world", 150, 150);
        assertEquals(GoverningBody.Kind.TERRITORY, onWindsward.kind());
        assertEquals(GovernmentForm.MONARCHY, onWindsward.governmentForm());

        assertEquals(GoverningBody.Kind.NONE, governance.resolveAt("world", 9999, 9999).kind());
    }
}

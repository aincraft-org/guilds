package com.azoth.territory.permission;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.TerritoryAlliance;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public matrix smoke: monarchy / oligarchy / anarchy break-place outcomes
 * on the shipped BlockProtection path.
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
        GovernanceRegistry governance = new GovernanceRegistry(territories);
        BlockProtection protection = new BlockProtection(governance);

        territories.register(new Territory(
                "mon-land", "Mon", "world", square(0, 50),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));
        territories.register(new Territory(
                "oli-land", "Oli", "world", square(50, 100),
                List.of(), ZoneType.WILDERNESS,
                Government.oligarchy(List.of("c1", "c2", "c3"))
        ));
        territories.register(new Territory(
                "an-land", "An", "world", square(100, 150),
                List.of(), ZoneType.WILDERNESS, Government.anarchy()
        ));

        governance.putAlliance(TerritoryAlliance.form(
                "royal", "Royal",
                Government.monarchy("king:1"),
                List.of("mon-land")
        ));

        // Monarchy via alliance
        assertTrue(protection.canBreak("world", 25, 25, "king:1"));
        assertFalse(protection.canBreak("world", 25, 25, "outsider"));
        System.out.println("FORM_MATRIX monarchy break king=ALLOW outsider=DENY");

        // Oligarchy local
        assertTrue(protection.canPlace("world", 75, 75, "c2"));
        assertFalse(protection.canPlace("world", 75, 75, "outsider"));
        System.out.println("FORM_MATRIX oligarchy place councilor=ALLOW outsider=DENY");

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

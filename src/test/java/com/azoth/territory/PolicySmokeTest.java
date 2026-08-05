package com.azoth.territory;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;
import com.azoth.territory.model.PolicyStatus;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public path: propose + decree on a fixture territory → exact PASSED + form.
 */
class PolicySmokeTest {

    @Test
    void publicProposeDecreePath_exactPassedAndMonarchyForm() {
        long now = 1_701_000_000_000L;
        TerritoryRegistry registry = new TerritoryRegistry();
        Territory t = new Territory(
                "everfall",
                "Everfall",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(500, 0),
                        new BlockPos(500, 500),
                        new BlockPos(0, 500)
                )),
                List.of(),
                ZoneType.WILDERNESS,
                Government.monarchy("faction:everfall-crown")
        );
        t = t.proposePolicy(
                "open-markets",
                "Open Markets",
                "Remove internal tariffs",
                "faction:everfall-crown",
                now
        );
        t = t.decreePolicy("open-markets", "faction:everfall-crown", true, now + 10);
        registry.register(t);

        assertEquals(PolicyStatus.PASSED,
                registry.get("everfall").orElseThrow().policy("open-markets").orElseThrow().status());
        assertEquals(GovernmentForm.MONARCHY,
                registry.get("everfall").orElseThrow().governmentForm());
        assertEquals(GovernmentForm.MONARCHY,
                registry.resolve("world", 100, 100).governmentForm().orElseThrow());

        System.out.println("POLICY_SMOKE territoryId=everfall form="
                + registry.get("everfall").orElseThrow().governmentForm()
                + " policy=open-markets status="
                + registry.get("everfall").orElseThrow().policy("open-markets").orElseThrow().status());
    }

    @Test
    void formCatalogIsOnlyMechanicallyDistinctForms() {
        assertEquals(4, GovernmentForm.values().length);
        assertTrue(GovernmentForm.MONARCHY.isAssigned());
        assertTrue(GovernmentForm.OLIGARCHY.isAssigned());
        assertTrue(GovernmentForm.DEMOCRACY.isAssigned());
        assertEquals("decree by SOVEREIGN seat holder",
                com.azoth.territory.model.PolicyRules.describeDecisionPath(GovernmentForm.MONARCHY));
        assertEquals("majority YES among filled COUNCILOR seats",
                com.azoth.territory.model.PolicyRules.describeDecisionPath(GovernmentForm.OLIGARCHY));
    }
}

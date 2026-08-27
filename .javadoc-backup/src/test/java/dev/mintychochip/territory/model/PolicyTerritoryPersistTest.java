package dev.mintychochip.territory.model;

import dev.mintychochip.territory.persist.TerritoryJson;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Territory path: propose/vote/decree → register → save → load → same statuses.
 */
class PolicyTerritoryPersistTest {


    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    @Test
    void saveLoad_preservesPolicyStatusAndForm() throws Exception {
        long now = 1_700_100_000_000L;
        Territory mon = new Territory(
                "crown", "Crown", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS,
                Government.monarchy("king:1")
        ).proposePolicy("royal-decree", "Royal Decree", "Ban night raids", "king:1", now)
                .decreePolicy("royal-decree", "king:1", true, now + 1);

        Territory oli = new Territory(
                "council-land", "Council", "world", square(100, 200),
                List.of(), ZoneType.WILDERNESS,
                Government.oligarchy(List.of("c1", "c2", "c3"))
        ).proposePolicy("quota", "Quota", "Set trade quota", "c1", now)
                .castPolicyVote("quota", "c1", VoteChoice.YES, now)
                .castPolicyVote("quota", "c2", VoteChoice.YES, now + 1);

        Territory dem = new Territory(
                "free-city", "Free City", "world", square(200, 300),
                List.of(), ZoneType.WILDERNESS,
                Government.democracy(3, List.of("r1", "r2", "r3"), null)
        ).proposePolicy("festival", "Festival", "Spring festival funding", "r1", now)
                .castPolicyVote("festival", "r1", VoteChoice.YES, now)
                .castPolicyVote("festival", "r2", VoteChoice.YES, now + 1);

        assertEquals(PolicyStatus.PASSED, mon.policy("royal-decree").orElseThrow().status());
        assertEquals(PolicyStatus.PASSED, oli.policy("quota").orElseThrow().status());
        assertEquals(PolicyStatus.PASSED, dem.policy("festival").orElseThrow().status());

        TerritoryRegistry original = new TerritoryRegistry();
        original.register(mon);
        original.register(oli);
        original.register(dem);

        TerritoryJson json = new TerritoryJson();
        TerritoryRegistry reloaded = new TerritoryRegistry();
        reloaded.replaceAll(json.registryFromJson(json.registryToJson(original)));
        assertEquals(3, reloaded.size());

        assertEquals(GovernmentForm.MONARCHY, reloaded.get("crown").orElseThrow().governmentForm());
        assertEquals(PolicyStatus.PASSED,
                reloaded.get("crown").orElseThrow().policy("royal-decree").orElseThrow().status());
        assertEquals("king:1",
                reloaded.get("crown").orElseThrow().policy("royal-decree").orElseThrow().proposerId());

        assertEquals(GovernmentForm.OLIGARCHY, reloaded.get("council-land").orElseThrow().governmentForm());
        assertEquals(PolicyStatus.PASSED,
                reloaded.get("council-land").orElseThrow().policy("quota").orElseThrow().status());
        assertEquals(2, reloaded.get("council-land").orElseThrow().policy("quota").orElseThrow().yesCount());

        assertEquals(GovernmentForm.DEMOCRACY, reloaded.get("free-city").orElseThrow().governmentForm());
        assertEquals(PolicyStatus.PASSED,
                reloaded.get("free-city").orElseThrow().policy("festival").orElseThrow().status());

        // Spatial resolve still works
        LookupResult r = reloaded.resolve("world", 50, 50);
        assertTrue(r.isContained());
        assertEquals("crown", r.territoryId().orElseThrow());
        assertEquals(GovernmentForm.MONARCHY, r.governmentForm().orElseThrow());
    }
}

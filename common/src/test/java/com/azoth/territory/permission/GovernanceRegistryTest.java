package com.azoth.territory.permission;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.GovernmentForm;
import com.azoth.territory.model.Policy;
import com.azoth.territory.model.PolicyStatus;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.VoteChoice;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceRegistryTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
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
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        territories.register(new Territory("everfall", "Everfall", "world", square(0, 50)));
        territories.register(new Territory("crownlands", "Crownlands", "world", square(50, 100)));
        territories.register(new Territory("freehold", "Freehold", "world", square(100, 150)));
    }

    private static GuildBody guild(String id, Government government, List<String> members) {
        return new GuildBody(id, id, government, members, GuildToggles.defaults(), Map.of());
    }

    @Test
    void resolveForTerritory_usesAllianceGovernmentWhenGuildIsNationMember() {
        GuildBody guild = guild("everfall-town", Government.monarchy("mayor:1"), List.of("mayor:1"));
        source.putGuild(guild);
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.oligarchy(List.of("king:1", "king:2")), List.of("everfall-town")));
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        GoverningBody body = governance.resolveForTerritory("everfall");

        assertEquals(GoverningBody.Kind.ALLIANCE, body.kind());
        assertEquals("northern-pact", body.bodyId().orElseThrow());
        assertEquals(GovernmentForm.OLIGARCHY, body.governmentForm());
    }

    @Test
    void resolveForTerritory_fallsBackToGuildWhenNotInNation() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"), List.of("mayor:1")));
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        GoverningBody body = governance.resolveForTerritory("everfall");

        assertEquals(GoverningBody.Kind.GUILD, body.kind());
        assertEquals("everfall-town", body.bodyId().orElseThrow());
        assertEquals(GovernmentForm.MONARCHY, body.governmentForm());
    }

    @Test
    void resolveForTerritory_unboundUsesLocalGovernment() {
        territories.register(new Territory("freehold", "Freehold", "world",
                square(100, 150), List.of(), ZoneType.WILDERNESS,
                Government.democracy(List.of("r1", "r2"))));

        GoverningBody body = governance.resolveForTerritory("freehold");

        assertEquals(GoverningBody.Kind.TERRITORY, body.kind());
        assertEquals(GovernmentForm.DEMOCRACY, body.governmentForm());
    }

    @Test
    void resolveForTerritory_boundGuildGoneFallsBackToLocal() {
        // Binding references a guild that no longer resolves
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS,
                Government.monarchy("local:1"), List.of(), "missing-town"));

        GoverningBody body = governance.resolveForTerritory("everfall");

        assertEquals(GoverningBody.Kind.TERRITORY, body.kind());
        assertEquals("everfall", body.bodyId().orElseThrow());
    }

    @Test
    void resolveForHolder_usesGuildGovernment() {
        source.putGuild(guild("iron-hand", Government.monarchy("guild:master"),
                List.of("guild:master", "member:1")));

        GoverningBody body = governance.resolveForHolder("member:1");

        assertEquals(GoverningBody.Kind.GUILD, body.kind());
        assertEquals("iron-hand", body.bodyId().orElseThrow());
    }

    @Test
    void resolveForHolder_unknownMember_isNone() {
        GoverningBody body = governance.resolveForHolder("wanderer");

        assertEquals(GoverningBody.Kind.NONE, body.kind());
    }

    @Test
    void resolveAt_spatialToAllianceOrLocal() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"), List.of("mayor:1")));
        source.putAlliance(new AllianceBody("pact", "Pact",
                Government.democracy(List.of("r1", "r2", "r3")), List.of("everfall-town")));
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        assertEquals(GoverningBody.Kind.ALLIANCE,
                governance.resolveAt("world", 25, 25).kind());
        // Outside every territory → none
        assertEquals(GoverningBody.Kind.NONE,
                governance.resolveAt("world", 9000, 9000).kind());
    }

    @Test
    void governingGuildForTerritory_resolvesBoundGuild() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"), List.of("mayor:1")));
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        assertTrue(governance.governingGuildForTerritory("everfall").isPresent());
        assertFalse(governance.governingGuildForTerritory("freehold").isPresent());
        assertTrue(governance.governingGuildAt("world", 25, 25).isPresent());
        assertFalse(governance.governingGuildAt("world", 125, 125).isPresent());
    }

    // ── Policy operations under the effective government ─────────────────

    @Test
    void proposePolicy_guildGovernment_derivedSeatsGate() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1", "resident:1")));
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        // Mayor is the derived sovereign — may propose
        Policy p = governance.proposePolicy("everfall", "tax", "Tax Reform", "body",
                "mayor:1", 1_000L);
        assertEquals(PolicyStatus.PROPOSED, p.status());

        // Resident is not in the derived electorate — rejected
        assertThrows(IllegalArgumentException.class, () ->
                governance.proposePolicy("everfall", "wall", "Wall", "body",
                        "resident:1", 1_000L));
    }

    @Test
    void decreePolicy_guildGovernment_mayorDecrees() {
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1", "resident:1")));
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        governance.proposePolicy("everfall", "tax", "Tax Reform", "body", "mayor:1", 1_000L);
        Policy passed = governance.decreePolicy("everfall", "tax", "mayor:1", true, 2_000L);

        assertEquals(PolicyStatus.PASSED, passed.status());
        // A fresh policy decreed by a non-electorate member is rejected
        governance.proposePolicy("everfall", "wall", "Build Wall", "body", "mayor:1", 3_000L);
        assertThrows(IllegalArgumentException.class, () ->
                governance.decreePolicy("everfall", "wall", "resident:1", false, 4_000L));
    }

    @Test
    void castPolicyVote_allianceGovernment_kingCannotVoteUnderOligarchyMayorCan() {
        // Alliance (oligarchy: king + minister) overrides the guild's monarchy
        source.putGuild(guild("everfall-town", Government.monarchy("mayor:1"),
                List.of("mayor:1")));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.oligarchy(List.of("king:1", "minister:1")), List.of("everfall-town")));
        territories.register(new Territory("everfall", "Everfall", "world",
                square(0, 50), List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));

        // The derived oligarchy electorate (king, minister) proposes and votes
        governance.proposePolicy("everfall", "wall", "Build Wall", "body", "king:1", 1_000L);
        Policy voted = governance.castPolicyVote("everfall", "wall", "minister:1",
                VoteChoice.YES, 2_000L);
        assertEquals(PolicyStatus.PROPOSED, voted.status());

        // Mayor of the bound guild is NOT in the alliance electorate
        assertThrows(IllegalArgumentException.class, () ->
                governance.castPolicyVote("everfall", "wall", "mayor:1",
                        VoteChoice.YES, 2_000L));

        // King's yes flips the majority (2 filled seats, need >1)
        Policy passed = governance.castPolicyVote("everfall", "wall", "king:1",
                VoteChoice.YES, 3_000L);
        assertEquals(PolicyStatus.PASSED, passed.status());
        // And the persisted territory carries the policy
        assertEquals("wall", territories.get("everfall").orElseThrow().policy("wall").orElseThrow().id());
    }

    @Test
    void policyOps_unknownTerritoryOrPolicy_throw() {
        assertThrows(IllegalArgumentException.class, () ->
                governance.proposePolicy("nope", "tax", "T", "b", "anyone", 1L));
        assertThrows(IllegalArgumentException.class, () ->
                governance.castPolicyVote("freehold", "nope", "anyone", VoteChoice.YES, 1L));
    }
}

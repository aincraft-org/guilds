package com.azoth.territory.influence;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.permission.AllianceBody;
import com.azoth.territory.permission.FakeGovernanceSource;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.permission.GuildToggles;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceEngineAccrualTest {

    @TempDir
    Path tempDir;

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private InfluenceConfig config;
    private InfluenceStore store;
    private InfluenceEngine engine;
    private long now;

    private static Boundary square(int min, int max) {
        return Boundary.ofPolygon(List.of(
                new BlockPos(min, min),
                new BlockPos(max, min),
                new BlockPos(max, max),
                new BlockPos(min, max)
        ));
    }

    private static GuildBody guild(String id) {
        return new GuildBody(id, id, Government.monarchy("m:" + id), List.of("m:" + id),
                GuildToggles.defaults(), Map.of());
    }

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        config = InfluenceConfig.defaults();
        store = new InfluenceStore(tempDir.resolve("influence.json"));
        engine = new InfluenceEngine(governance, config, store, (t, g) -> { }, Logger.getLogger("test"));
        now = 1_000_000L;
    }

    private void registerTerritory(String id, String ownerGuildId) {
        territories.register(new Territory(id, id, "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    private void setupEverfallContest() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("everfall", "everfall-town");
    }

    @Test
    void accrue_unknownTerritory_isNoOp() {
        Optional<InfluenceBar> result = engine.accrue("nope", "rival-guild",
                InfluenceSource.PVP_KILL, now, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void accrue_ungovernedTerritory_isNoOp() {
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("freehold", null);
        assertTrue(engine.accrue("freehold", "rival-guild",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_ownerGuildActivity_isNoOp() {
        setupEverfallContest();
        assertTrue(engine.accrue("everfall", "everfall-town",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_unaffiliatedAttacker_isNoOp() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("loner"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        registerTerritory("everfall", "everfall-town");
        assertTrue(engine.accrue("everfall", "loner",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_unaffiliatedOwner_isNoOp() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("everfall", "everfall-town");
        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_sameAllianceAttacker_isNoOp() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("cousin-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town", "cousin-guild")));
        registerTerritory("everfall", "everfall-town");
        assertTrue(engine.accrue("everfall", "cousin-guild",
                InfluenceSource.PVP_KILL, now, null).isEmpty());
    }

    @Test
    void accrue_eligibleAttacker_addsSourceValue() {
        setupEverfallContest();
        Optional<InfluenceBar> bar = engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now, null);
        assertTrue(bar.isPresent());
        assertEquals("rival-guild", bar.get().guildId());
        assertEquals(10.0, bar.get().value(), 0.001);
    }

    @Test
    void accrue_allSourcesUseTheirValues() {
        setupEverfallContest();
        for (InfluenceSource s : InfluenceSource.values()) {
            engine.accrue("everfall", "rival-guild", s, now, null);
        }
        double expected = config.pvpKill() + config.pveKill() + config.blockBreak()
                + config.blockPlace() + config.craft();
        Optional<InfluenceBar> bar = engine.influence("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("rival-guild")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(expected, bar.get().value(), 0.001);
    }

    @Test
    void accrue_clampsAtCap() {
        setupEverfallContest();
        for (int i = 0; i < 15; i++) {
            engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        }
        Optional<InfluenceBar> bar = engine.influence("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("rival-guild")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(config.cap(), bar.get().value(), 0.001);
    }

    @Test
    void accrue_pvpKillSameAllianceVictim_isNoOp() {
        setupEverfallContest();
        source.putGuild(guild("cousin-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild", "cousin-guild")));
        assertTrue(engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL,
                now, "cousin-guild").isEmpty());
    }

    @Test
    void accrue_pvpKillDifferentAllianceVictim_counts() {
        setupEverfallContest();
        assertTrue(engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL,
                now, "some-other-guild").isPresent());
    }

    @Test
    void accrue_defenderSubtractsFromEveryAttackerBar() {
        setupEverfallContest();
        source.putGuild(guild("third-guild"));
        source.putAlliance(new AllianceBody("third-pact", "Third Pact",
                Government.anarchy(), List.of("third-guild")));
        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        engine.accrue("everfall", "third-guild", InfluenceSource.PVP_KILL, now, null);

        // Defender block-break (0.1) pushes both bars down from 10.0.
        engine.accrue("everfall", "everfall-town", InfluenceSource.BLOCK_BREAK, now, null);

        double expected = config.pvpKill() - config.defenderValueOf(InfluenceSource.BLOCK_BREAK);
        var bars = engine.influence("everfall").orElseThrow().bars();
        assertEquals(2, bars.size(), "both attacker bars must survive");
        for (InfluenceBar bar : bars) {
            assertEquals(expected, bar.value(), 0.001, "bar " + bar.guildId());
        }
    }

    @Test
    void accrue_defenderSubtractNeverGoesBelowZero() {
        setupEverfallContest();
        for (int i = 0; i < 3; i++) {
            engine.accrue("everfall", "rival-guild", InfluenceSource.BLOCK_BREAK, now, null);
        }
        for (int i = 0; i < 3; i++) {
            engine.accrue("everfall", "everfall-town", InfluenceSource.BLOCK_BREAK, now, null);
        }
        assertTrue(engine.influence("everfall").orElseThrow().bars().isEmpty(),
                "bar must hit exactly zero and be dropped, never negative");
    }

    @Test
    void accrue_ownerRebindResetsBars() {
        setupEverfallContest();
        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);

        // Admin rebinds the territory to a third guild; old race must be discarded.
        source.putGuild(guild("new-owner"));
        source.putAlliance(new AllianceBody("third-pact", "Third Pact",
                Government.anarchy(), List.of("new-owner")));
        territories.register(new Territory("everfall", "everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "new-owner"));

        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now + 1, null).isPresent());
        var bars = engine.influence("everfall").orElseThrow().bars();
        assertEquals(1, bars.size(), "old race must be discarded, only the new bar remains");
        assertEquals(10.0, bars.get(0).value(), 0.001);
    }
}

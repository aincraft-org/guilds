package com.azoth.territory.influence;

import com.azoth.territory.PostgresTestDatabase;
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
import com.azoth.territory.persist.PostgresDatabase;
import com.azoth.territory.registry.TerritoryRegistry;
import com.azoth.territory.standing.StandingService;
import com.azoth.territory.standing.StandingTier;
import com.azoth.territory.standing.TerritoryStandingState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceEngineStandingHookTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private PostgresDatabase database;
    private PostgresInfluenceStore store;
    private InfluenceEngine engine;

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
        database = PostgresTestDatabase.open();
        store = new PostgresInfluenceStore(database);
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private void registerTerritory(String id, String ownerGuildId) {
        territories.register(new Territory(id, id, "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    private void setupContest() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        registerTerritory("everfall", "everfall-town");
    }

    private static final class FakeStanding implements StandingService {
        private final double influenceMultiplier;

        FakeStanding(double influenceMultiplier) {
            this.influenceMultiplier = influenceMultiplier;
        }

        @Override public Optional<TerritoryStandingState> standing(String territoryId) { return Optional.empty(); }
        @Override public List<TerritoryStandingState> all() { return List.of(); }
        @Override public double harvestMultiplierFor(String territoryId, String guildId) { return 1.0; }
        @Override public double influenceMultiplierFor(String guildId) { return influenceMultiplier; }
        @Override public Optional<StandingTier> tierFor(String territoryId, String guildId) { return Optional.empty(); }
        @Override public boolean adminSet(String territoryId, String guildId, double value) { return false; }
        @Override public boolean adminReset(String territoryId) { return false; }
    }

    @Test
    void accrual_multipliedByStandingInfluenceMultiplier() {
        setupContest();
        engine = new InfluenceEngine(governance, InfluenceConfig.defaults(), store,
                (t, g) -> { }, Logger.getLogger("test"), new FakeStanding(1.5));

        Optional<InfluenceBar> bar = engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, 1_000_000L, null);
        assertTrue(bar.isPresent());
        assertEquals(15.0, bar.get().value(), 0.001);  // 10 * 1.5
    }

    @Test
    void accrual_multiplierAppliesToSourceOnlyNotAccumulatedBar() {
        // Bar at 50, then a PVP kill with multiplier 1.5:
        // expected = 50 + (10 * 1.5) = 65, not (50 + 10) * 1.5 = 90.
        setupContest();
        engine = new InfluenceEngine(governance, InfluenceConfig.defaults(), store,
                (t, g) -> { }, Logger.getLogger("test"), new FakeStanding(1.5));
        engine.adminSet("everfall", "rival-guild", 50.0, 1_000_000L);

        Optional<InfluenceBar> bar = engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, 1_000_000L, null);
        assertTrue(bar.isPresent());
        assertEquals(65.0, bar.get().value(), 0.001);
    }

    @Test
    void accrual_withoutStandingService_usesDefaultMultiplier() {
        setupContest();
        engine = new InfluenceEngine(governance, InfluenceConfig.defaults(), store,
                (t, g) -> { }, Logger.getLogger("test"));

        Optional<InfluenceBar> bar = engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, 1_000_000L, null);
        assertTrue(bar.isPresent());
        assertEquals(10.0, bar.get().value(), 0.001);  // 10 * 1.0
    }
}

package com.azoth.territory.standing;

import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.permission.FakeGovernanceSource;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.permission.GuildToggles;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingEngineTierTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private StandingEngine engine;

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
    void setUp() {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        // Store is nullable in the engine (only touched by flush/recover),
        // so tests that never persist construct with null.
        engine = new StandingEngine(governance, StandingConfig.defaults(), null, Logger.getLogger("test"));
    }

    private void registerTerritory(String id, String ownerGuildId) {
        territories.register(new Territory(id, id, "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    private void accrueTimes(String territoryId, String guildId, StandingSource source, int times) {
        for (int i = 0; i < times; i++) {
            engine.accrue(territoryId, guildId, source);
        }
    }

    private void registerSecondTerritory(String id, String ownerGuildId) {
        // Same world; must not overlap the default square(0, 100).
        territories.register(new Territory(id, id, "world", square(200, 300),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), ownerGuildId));
    }

    @Test
    void tierFor_returnsSaturatingTier() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("everfall", "everfall-town");
        accrueTimes("everfall", "everfall-town", StandingSource.PVP_KILL, 30); // 300
        Optional<StandingTier> tier = engine.tierFor("everfall", "everfall-town");
        assertEquals(3, tier.orElseThrow().level());
    }

    @Test
    void harvestMultiplierFor_nonOwnerGuild_isOne() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("outsider"));
        registerTerritory("everfall", "everfall-town");
        assertEquals(1.0, engine.harvestMultiplierFor("everfall", "outsider"), 0.001);
    }

    @Test
    void harvestMultiplierFor_ownerReflectsTier() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("everfall", "everfall-town");
        accrueTimes("everfall", "everfall-town", StandingSource.PVP_KILL, 30); // 300 → tier 3
        assertEquals(1.5, engine.harvestMultiplierFor("everfall", "everfall-town"), 0.001);
    }

    @Test
    void influenceMultiplierFor_noTerritories_isOne() {
        assertEquals(1.0, engine.influenceMultiplierFor("loner"), 0.001);
    }

    @Test
    void influenceMultiplierFor_takesMaxOverGovernedTerritories() {
        source.putGuild(guild("g1"));
        registerTerritory("t1", "g1");
        registerSecondTerritory("t2", "g1");
        accrueTimes("t1", "g1", StandingSource.PVP_KILL, 10);  // 100 → tier 2 (1.1)
        accrueTimes("t2", "g1", StandingSource.PVP_KILL, 30);  // 300 → tier 3 (1.25)
        assertEquals(1.25, engine.influenceMultiplierFor("g1"), 0.001);
    }

    @Test
    void influenceMultiplierFor_ignoresTerritoriesGuildDoesNotGovern() {
        source.putGuild(guild("g1"));
        source.putGuild(guild("g2"));
        registerTerritory("t1", "g1");
        registerSecondTerritory("t2", "g2");
        accrueTimes("t1", "g1", StandingSource.PVP_KILL, 30); // g1 at 300 → 1.25
        accrueTimes("t2", "g2", StandingSource.PVP_KILL, 30); // g2 at 300 → 1.25
        assertEquals(1.25, engine.influenceMultiplierFor("g1"), 0.001);
    }
}

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
import com.azoth.territory.persist.PostgresDatabase;
import com.azoth.territory.PostgresTestDatabase;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingEngineAccrualTest {

    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private StandingConfig config;
    private PostgresDatabase database;
    private PostgresStandingStore store;
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
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        config = StandingConfig.defaults();
        database = PostgresTestDatabase.open();
        store = new PostgresStandingStore(database);
        engine = new StandingEngine(governance, config, store, Logger.getLogger("test"));
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

    private void setupEverfall() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("everfall", "everfall-town");
    }

    @Test
    void accrue_unknownTerritory_isNoOp() {
        assertTrue(engine.accrue("nope", "everfall-town", StandingSource.PVP_KILL).isEmpty());
    }

    @Test
    void accrue_ungovernedTerritory_isNoOp() {
        source.putGuild(guild("everfall-town"));
        registerTerritory("freehold", null);
        assertTrue(engine.accrue("freehold", "everfall-town", StandingSource.PVP_KILL).isEmpty());
    }

    @Test
    void accrue_nonOwnerGuild_isNoOp() {
        setupEverfall();
        source.putGuild(guild("outsider"));
        assertTrue(engine.accrue("everfall", "outsider", StandingSource.PVP_KILL).isEmpty());
    }

    @Test
    void accrue_ownerGuild_addsSourceValue() {
        setupEverfall();
        Optional<StandingBar> bar = engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);
        assertTrue(bar.isPresent());
        assertEquals("everfall-town", bar.get().guildId());
        assertEquals(10.0, bar.get().value(), 0.001);
    }

    @Test
    void accrue_allSourcesUseTheirValues() {
        setupEverfall();
        for (StandingSource s : StandingSource.values()) {
            engine.accrue("everfall", "everfall-town", s);
        }
        double expected = config.valueOf(StandingSource.PVP_KILL)
                + config.valueOf(StandingSource.PVE_KILL)
                + config.valueOf(StandingSource.BLOCK_BREAK);
        Optional<StandingBar> bar = engine.standing("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("everfall-town")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(expected, bar.get().value(), 0.001);
    }

    @Test
    void accrue_clampsAtCap() {
        setupEverfall();
        for (int i = 0; i < 60; i++) {
            engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);
        }
        Optional<StandingBar> bar = engine.standing("everfall").orElseThrow().bars().stream()
                .filter(b -> b.guildId().equals("everfall-town")).findFirst();
        assertTrue(bar.isPresent());
        assertEquals(config.cap(), bar.get().value(), 0.001);
    }

    @Test
    void accrue_ownerRebindResetsBar() {
        setupEverfall();
        engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);

        source.putGuild(guild("new-owner"));
        territories.register(new Territory("everfall", "everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "new-owner"));

        Optional<StandingBar> bar = engine.accrue("everfall", "new-owner", StandingSource.PVP_KILL);
        assertTrue(bar.isPresent());
        assertEquals(10.0, bar.get().value(), 0.001);
        assertEquals(1, engine.standing("everfall").orElseThrow().bars().size());
    }
}

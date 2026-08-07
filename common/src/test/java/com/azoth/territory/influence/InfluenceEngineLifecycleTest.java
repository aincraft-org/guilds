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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceEngineLifecycleTest {


    private TerritoryRegistry territories;
    private FakeGovernanceSource source;
    private GovernanceRegistry governance;
    private InfluenceConfig config;
    private PostgresInfluenceStore store;
    private PostgresDatabase database;
    private InfluenceEngine engine;
    private List<InfluenceEngine.TerritoryFlip> flips;
    private List<String> persistedOwnership;
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

    private void freshEngine() {
        engine = new InfluenceEngine(governance, config, store, (t, g) -> persistedOwnership.add(t + "->" + g),
                Logger.getLogger("lifecycle-test"));
    }

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        source = new FakeGovernanceSource();
        governance = new GovernanceRegistry(territories, source);
        config = InfluenceConfig.defaults();
        database = PostgresTestDatabase.open();
        store = new PostgresInfluenceStore(database);
        flips = new ArrayList<>();
        persistedOwnership = new ArrayList<>();
        freshEngine();
        now = 1_000_000L;
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private void setupEverfallContest() {
        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        territories.register(new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));
    }

    private void pushRivalToCap() {
        for (int i = 0; i < 10; i++) {
            engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        }
    }

    // ── declare ───────────────────────────────────────────────────────────

    @Test
    void declare_unknownTerritory() {
        assertEquals(DeclareStatus.TERRITORY_UNKNOWN,
                engine.declare("nope", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_ungovernedTerritory() {
        territories.register(new Territory("freehold", "Freehold", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), null));
        assertEquals(DeclareStatus.UNGOVERNABLE,
                engine.declare("freehold", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_requiresEligibility() {
        setupEverfallContest();
        pushRivalToCap();
        // Rival joins the owner's alliance (and leaves its own) — same-alliance
        // guilds may not contest.
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town", "rival-guild")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of()));
        assertEquals(DeclareStatus.NOT_ELIGIBLE,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_requiresCap() {
        setupEverfallContest();
        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        assertEquals(DeclareStatus.NOT_AT_CAP,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_requiresAuthority() {
        setupEverfallContest();
        pushRivalToCap();
        assertEquals(DeclareStatus.NOT_AUTHORIZED,
                engine.declare("everfall", "rival-guild", "some-rando", now).status());
        assertEquals(DeclareStatus.DECLARED,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    @Test
    void declare_succeedsAndLocksRace() {
        setupEverfallContest();
        pushRivalToCap();
        DeclareResult result = engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        assertEquals(DeclareStatus.DECLARED, result.status());
        assertTrue(result.isSuccess());

        // Race locked: no accrual for anyone.
        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now + 1, null).isEmpty());
        assertTrue(engine.accrue("everfall", "everfall-town",
                InfluenceSource.PVP_KILL, now + 1, null).isEmpty());
        // No second declaration.
        assertEquals(DeclareStatus.RACE_ACTIVE,
                engine.declare("everfall", "rival-guild", "m:rival-guild", now + 1).status());
    }

    @Test
    void declare_persistsSynchronously() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        InfluenceState loaded = store.load();
        TerritoryEntry entry = loaded.entries.get("everfall");
        assertEquals("rival-guild", entry.declaration.guildId());
        assertEquals(now + config.declareCountdownEpochMs(), entry.declaration.flipAtEpochMs());
    }

    @Test
    void cancelDeclaration_requiresOwnershipAndAuthority() {
        setupEverfallContest();
        pushRivalToCap();
        source.putGuild(guild("third-guild"));
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        assertEquals(DeclareStatus.NOT_AUTHORIZED,
                engine.cancelDeclaration("everfall", "rival-guild", "some-rando", now).status());
        assertEquals(DeclareStatus.NOT_AUTHORIZED,
                engine.cancelDeclaration("everfall", "third-guild", "m:third-guild", now).status());
    }

    @Test
    void cancelDeclaration_resumesRace() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        DeclareResult cancelled = engine.cancelDeclaration("everfall", "rival-guild", "m:rival-guild", now);
        assertEquals(DeclareStatus.CANCELLED, cancelled.status());
        assertNull(engine.influence("everfall").orElseThrow().declaration());
        assertTrue(engine.accrue("everfall", "rival-guild",
                InfluenceSource.PVP_KILL, now + 1, null).isPresent());
    }

    @Test
    void cancelDeclaration_noActiveDeclaration() {
        setupEverfallContest();
        assertEquals(DeclareStatus.RACE_ACTIVE,
                engine.cancelDeclaration("everfall", "rival-guild", "m:rival-guild", now).status());
    }

    // ── tickFlips ─────────────────────────────────────────────────────────

    @Test
    void tickFlips_notDue_doesNothing() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);

        List<InfluenceEngine.TerritoryFlip> flips = engine.tickFlips(now + 1);
        assertTrue(flips.isEmpty());
        assertEquals("everfall-town", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
    }

    @Test
    void tickFlips_appliesDueFlip() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        long flipTime = now + config.declareCountdownEpochMs();

        List<InfluenceEngine.TerritoryFlip> flipped = engine.tickFlips(flipTime);

        assertEquals(1, flipped.size());
        assertEquals("everfall-town", flipped.get(0).oldOwnerGuildId());
        assertEquals("rival-guild", flipped.get(0).newOwnerGuildId());
        assertEquals(List.of("everfall->rival-guild"), persistedOwnership);
        // Owner rebound in the registry.
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        // Bars reset, cooldown set (starts at flip completion), declaration gone.
        TerritoryInfluenceState state = engine.influence("everfall").orElseThrow();
        assertTrue(state.bars().isEmpty());
        assertNull(state.declaration());
        assertEquals(flipTime + config.postFlipCooldownEpochMs(), state.cooldownUntilEpochMs());
        // Journal final state persisted.
        TerritoryEntry persisted = store.load().entries.get("everfall");
        assertEquals("rival-guild", persisted.ownerGuildId);
        assertNull(persisted.pendingFlip);
        assertNull(persisted.declaration);
        assertEquals(flipTime + config.postFlipCooldownEpochMs(), persisted.cooldownUntilEpochMs);
    }

    @Test
    void tickFlips_invalidAtFlipTime_cancelsDeclaration() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        // Attacker leaves its alliance before the flip — the takeover is void.
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of()));

        List<InfluenceEngine.TerritoryFlip> flipped = engine.tickFlips(now + config.declareCountdownEpochMs());

        assertTrue(flipped.isEmpty());
        assertEquals("everfall-town", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty());
        assertNull(engine.influence("everfall").orElseThrow().declaration());
    }

    @Test
    void tickFlips_secondRaceAfterCooldown() {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        engine.tickFlips(now + config.declareCountdownEpochMs());

        // After the cooldown, the new owner is contestable again by a rival
        // from a different alliance (rival-guild's own alliance-mates cannot).
        long later = now + config.declareCountdownEpochMs() + config.postFlipCooldownEpochMs() + 1;
        source.putGuild(guild("other-rival"));
        source.putAlliance(new AllianceBody("eastern-pact", "Eastern Pact",
                Government.anarchy(), List.of("other-rival")));

        assertTrue(engine.accrue("everfall", "other-rival", InfluenceSource.PVP_KILL, later, null).isPresent());
    }

    // ── recover ───────────────────────────────────────────────────────────

    @Test
    void recover_appliesOverdueDeclaration() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        long flipTime = now + config.declareCountdownEpochMs();

        // Simulate restart: fresh engine recovers from disk with the flip due.
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(flipTime);

        assertEquals(1, flipped.size());
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertEquals(List.of("everfall->rival-guild"), persistedOwnership);
        assertTrue(engine.influence("everfall").orElseThrow().cooldownUntilEpochMs() > now);
    }

    @Test
    void recover_invalidOverdueDeclaration_cancelsWithoutFlip() throws IOException {
        setupEverfallContest();
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        long flipTime = now + config.declareCountdownEpochMs();

        // Attacker leaves its alliance while the server was down.
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of()));
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(flipTime);

        assertTrue(flipped.isEmpty());
        assertEquals("everfall-town", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty());
        assertNull(store.load().entries.get("everfall").declaration);
    }

    @Test
    void recover_voidsStaleMarkerWhenOwnerMovedOn() throws IOException {
        // Simulate: marker written, then owner externally rebound, then restart.
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.pendingFlip = new PendingFlip("everfall", "everfall-town", "rival-guild",
                now + 1, now + config.postFlipCooldownEpochMs());
        state.entries.put("everfall", entry);
        store.save(state);

        source.putGuild(guild("everfall-town"));
        source.putGuild(guild("rival-guild"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild")));
        // Territory owner changed externally to a third guild while down.
        source.putGuild(guild("new-owner"));
        source.putAlliance(new AllianceBody("third-pact", "Third Pact",
                Government.anarchy(), List.of("new-owner")));
        territories.register(new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "new-owner"));

        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(now + 5);

        assertTrue(flipped.isEmpty());
        assertEquals("new-owner", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty());
        engine.flush(); // the void is batched, not sync
        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertNull(recovered.pendingFlip, "stale marker must be voided");
        assertEquals(0L, recovered.cooldownUntilEpochMs, "no cooldown for a voided flip");
    }

    @Test
    void recover_appliesValidMarker() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.pendingFlip = new PendingFlip("everfall", "everfall-town", "rival-guild",
                now + 1, now + config.postFlipCooldownEpochMs());
        state.entries.put("everfall", entry);
        store.save(state);

        setupEverfallContest();
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(now + 5);

        assertEquals(1, flipped.size());
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertEquals(List.of("everfall->rival-guild"), persistedOwnership);
        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertEquals("rival-guild", recovered.ownerGuildId);
        assertEquals(now + config.postFlipCooldownEpochMs(), recovered.cooldownUntilEpochMs);
        assertNull(recovered.pendingFlip);
    }

    @Test
    void recover_ownerMismatchResetsBarsKeepsCooldown() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "old-owner";
        entry.cooldownUntilEpochMs = now + 5;
        entry.bars.put("attacker", 42.0);
        state.entries.put("everfall", entry);
        store.save(state);

        setupEverfallContest();
        freshEngine();
        engine.recover(now);
        engine.flush(); // mismatch/drop resets are batched, not sync

        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertEquals("everfall-town", recovered.ownerGuildId);
        assertTrue(recovered.bars.isEmpty(), "bars reset on owner mismatch");
        assertEquals(now + 5, recovered.cooldownUntilEpochMs, "cooldown kept");
    }

    @Test
    void recover_dropsEntryForMissingTerritory() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "some-guild";
        entry.bars.put("attacker", 5.0);
        state.entries.put("ghost", entry);
        store.save(state);

        freshEngine();
        engine.recover(now);
        engine.flush(); // the drop is batched, not sync

        assertFalse(store.load().entries.containsKey("ghost"));
    }


    @Test
    void recover_finalizesMarkerWhenOwnershipAlreadyApplied() throws IOException {
        // Crash after journal step 2: ownership persisted, finalize not.
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.pendingFlip = new PendingFlip("everfall", "everfall-town", "rival-guild",
                now + 1, now + config.postFlipCooldownEpochMs());
        state.entries.put("everfall", entry);
        store.save(state);

        setupEverfallContest();
        // Territory already shows the new owner (step 2 done before the crash).
        territories.register(new Territory("everfall", "Everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "rival-guild"));
        freshEngine();
        List<InfluenceEngine.TerritoryFlip> flipped = engine.recover(now + 5);

        assertEquals(1, flipped.size(), "the completed takeover must finalize and broadcast");
        assertEquals("rival-guild", territories.get("everfall").orElseThrow().governedByGuildId().orElseThrow());
        assertTrue(persistedOwnership.isEmpty(), "ownership already applied — persister must not run again");
        TerritoryEntry recovered = store.load().entries.get("everfall");
        assertEquals("rival-guild", recovered.ownerGuildId);
        assertEquals(now + config.postFlipCooldownEpochMs(), recovered.cooldownUntilEpochMs);
        assertNull(recovered.pendingFlip, "marker finalized");
    }


    @Test
    void accrue_duringCooldown_isNoOp() throws IOException {
        source.putGuild(guild("everfall-town"));
        source.putAlliance(new AllianceBody("northern-pact", "Northern Pact",
                Government.anarchy(), List.of("everfall-town")));
        territories.register(new Territory("everfall", "everfall", "world", square(0, 100),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "rival-guild"));

        // Force a cooldown by persisting state directly.
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "rival-guild";
        entry.cooldownUntilEpochMs = now + config.postFlipCooldownEpochMs();
        state.entries.put("everfall", entry);
        store.save(state);
        engine.recover(now);

        assertTrue(engine.accrue("everfall", "everfall-town",
                InfluenceSource.PVP_KILL, now + 1, null).isEmpty());
    }

    // ── queries, admin, flush ─────────────────────────────────────────────

    @Test
    void influence_reportsStateSorted() {
        setupEverfallContest();
        source.putGuild(guild("zeta-guild"));
        source.putGuild(guild("alpha-guild"));
        source.putAlliance(new AllianceBody("southern-pact", "Southern Pact",
                Government.anarchy(), List.of("rival-guild", "zeta-guild", "alpha-guild")));
        engine.accrue("everfall", "zeta-guild", InfluenceSource.PVP_KILL, now, null);
        engine.accrue("everfall", "alpha-guild", InfluenceSource.PVP_KILL, now, null);

        TerritoryInfluenceState state = engine.influence("everfall").orElseThrow();
        assertEquals(List.of("alpha-guild", "zeta-guild"),
                state.bars().stream().map(InfluenceBar::guildId).toList());
        assertEquals("everfall-town", state.ownerGuildId());
    }

    @Test
    void influence_unknownTerritory_isEmpty() {
        assertTrue(engine.influence("nope").isEmpty());
    }

    @Test
    void isDeclarable_requiresCapEligibilityAndOpenRace() {
        setupEverfallContest();
        assertFalse(engine.isDeclarable("everfall", "rival-guild", now));
        pushRivalToCap();
        assertTrue(engine.isDeclarable("everfall", "rival-guild", now));
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        assertFalse(engine.isDeclarable("everfall", "rival-guild", now + 1));
    }

    @Test
    void isCooldownActive_afterFlip() {
        setupEverfallContest();
        assertFalse(engine.isCooldownActive("everfall", now));
        pushRivalToCap();
        engine.declare("everfall", "rival-guild", "m:rival-guild", now);
        engine.tickFlips(now + config.declareCountdownEpochMs());
        assertTrue(engine.isCooldownActive("everfall", now + config.declareCountdownEpochMs() + 1));
        assertFalse(engine.isCooldownActive("everfall",
                now + config.declareCountdownEpochMs() + config.postFlipCooldownEpochMs() + 1));
    }

    @Test
    void adminSet_writesBarAndAdminResetDropsEntry() throws IOException {
        setupEverfallContest();
        assertTrue(engine.adminSet("everfall", "rival-guild", 55.0, now));
        assertEquals(55.0, engine.influence("everfall").orElseThrow().bars().get(0).value(), 0.001);
        assertTrue(engine.adminReset("everfall"));
        assertTrue(engine.influence("everfall").isEmpty());

        // Reset must survive a restart: flush and reload from disk.
        engine.flush();
        assertTrue(store.load().entries.isEmpty(), "admin reset must persist");
    }

    @Test
    void flush_persistsOnlyWhenDirty() throws IOException {
        setupEverfallContest();
        engine.flush();

        engine.accrue("everfall", "rival-guild", InfluenceSource.PVP_KILL, now, null);
        engine.flush();
        assertEquals(10.0, store.load().entries.get("everfall").bars.get("rival-guild"), 0.001);

        engine.flush();
        assertEquals(10.0, store.load().entries.get("everfall").bars.get("rival-guild"), 0.001);
    }
}

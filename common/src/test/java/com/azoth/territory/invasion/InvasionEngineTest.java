package com.azoth.territory.invasion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvasionEngineTest {
    private static final long NOW = 1_000L;
    private static final InvasionConfig CONFIG = new InvasionConfig(
            4,
            List.of(
                    new Wave(List.of(new MobEntry("zombie", 2))),
                    new Wave(List.of(new MobEntry("skeleton", 1))),
                    new Wave(List.of(new MobEntry("creeper", 1)))));

    @Test
    void startsOneActiveInvasionPerGuild() {
        InvasionEngine engine = engine(new MemoryStore());
        assertEquals(InvasionStartStatus.STARTED,
                engine.start("guild-a", "Guild A", "world", 8.5, 70, 8.5, NOW).status());
        assertEquals(InvasionStartStatus.ALREADY_ACTIVE,
                engine.start("guild-a", "Guild A", "world", 8.5, 70, 8.5, NOW).status());
        assertEquals(InvasionStartStatus.STARTED,
                engine.start("guild-b", "Guild B", "world", 40.5, 70, 40.5, NOW).status());
    }

    @Test
    void tracksEntitiesAndProgressesThroughWavesToDefended() {
        InvasionEngine engine = engine(new MemoryStore());
        UUID invasion = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(List.of(InvasionTransition.NO_CHANGE), engine.mobRemoved(invasion, UUID.randomUUID(), NOW));
        engine.mobSpawned(invasion, first);
        engine.mobSpawned(invasion, second);
        assertEquals(List.of(InvasionTransition.NO_CHANGE), engine.mobRemoved(invasion, first, NOW));
        assertEquals(List.of(InvasionTransition.WAVE_CLEARED, InvasionTransition.NEXT_WAVE), engine.mobRemoved(invasion, second, NOW));
        UUID third = UUID.randomUUID();
        engine.mobSpawned(invasion, third);
        assertEquals(List.of(InvasionTransition.WAVE_CLEARED, InvasionTransition.NEXT_WAVE), engine.mobRemoved(invasion, third, NOW));
        UUID fourth = UUID.randomUUID();
        engine.mobSpawned(invasion, fourth);
        assertEquals(List.of(InvasionTransition.WAVE_CLEARED, InvasionTransition.DEFENDED), engine.mobRemoved(invasion, fourth, NOW));
        assertEquals(InvasionStatus.DEFENDED, engine.status("guild-a").orElseThrow().status());
    }

    @Test
    void damageSaturatesAndAccumulatesAcrossRecords() {
        InvasionEngine engine = engine(new MemoryStore());
        UUID first = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        for (int i = 0; i < 5; i++) engine.recordDestroyedBlock(first, NOW + i);
        assertEquals(100, engine.status("guild-a").orElseThrow().damage().percent());
        engine.cancel("guild-a", NOW + 10);
        UUID second = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW + 11).invasionId();
        engine.recordDestroyedBlock(second, NOW + 12);
        assertEquals(100, engine.status("guild-a").orElseThrow().damage().percent());
    }

    @Test
    void cancelAndRecoverActiveRecordsToCancelled() {
        MemoryStore store = new MemoryStore();
        InvasionEngine engine = engine(store);
        UUID invasion = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        assertEquals(InvasionTransition.CANCELLED, engine.cancel("guild-a", NOW + 1));
        assertEquals(InvasionStatus.CANCELLED, engine.status("guild-a").orElseThrow().status());
        store.records = List.of(new InvasionRecord(invasion, "guild-b", "Guild B", "world", 1, 2, 3,
                InvasionStatus.ACTIVE, 0, List.of(), new GuildDamage(0, 0), NOW));
        InvasionEngine recovered = engine(store);
        recovered.recover(NOW + 2);
        assertEquals(InvasionStatus.CANCELLED, recovered.status("guild-b").orElseThrow().status());
    }

    @Test
    void failedPersistenceRollsBackMutation() {
        FailingStore store = new FailingStore();
        InvasionEngine engine = engine(store);
        assertEquals(InvasionStartStatus.PERSISTENCE_FAILED,
                engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).status());
        assertTrue(engine.status("guild-a").isEmpty());
    }

    @Test
    void cancelFailureRestoresActiveGuildIndex() {
        ToggleStore store = new ToggleStore();
        InvasionEngine engine = engine(store);
        UUID invasion = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        store.fail = true;

        assertEquals(InvasionTransition.NO_CHANGE, engine.cancel("guild-a", NOW + 1));
        assertEquals(InvasionStatus.ACTIVE, engine.status("guild-a").orElseThrow().status());
        assertEquals(1, engine.activeInvasions().size());
        assertEquals(invasion, engine.activeInvasions().getFirst().invasionId());
    }

    @Test
    void finalRemovalFailureRestoresActiveGuildIndex() {
        ToggleStore store = new ToggleStore();
        InvasionEngine engine = engine(store);
        UUID invasion = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        UUID mob = UUID.randomUUID();
        engine.mobSpawned(invasion, mob);
        store.fail = true;

        assertEquals(List.of(InvasionTransition.NO_CHANGE), engine.mobRemoved(invasion, mob, NOW + 1));
        assertEquals(InvasionStatus.ACTIVE, engine.status("guild-a").orElseThrow().status());
        assertEquals(invasion, engine.activeInvasions().getFirst().invasionId());
    }
    @Test
    void finalRemovalPersistenceFailureReturnsNoChangeAndPreservesActiveState() {
        ToggleStore store = new ToggleStore();
        InvasionEngine engine = engine(store);
        UUID invasion = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        UUID mob = UUID.randomUUID();
        engine.mobSpawned(invasion, mob);
        store.fail = true;

        assertEquals(List.of(InvasionTransition.NO_CHANGE), engine.mobRemoved(invasion, mob, NOW + 1));
        assertEquals(InvasionStatus.ACTIVE, engine.status("guild-a").orElseThrow().status());
        assertEquals(invasion, engine.activeInvasions().getFirst().invasionId());
    }

    @Test
    void devastatedPersistenceFailureReturnsNoChangeAndRollsBackDamage() {
        ToggleStore store = new ToggleStore();
        InvasionEngine engine = engine(store);
        UUID invasion = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        for (int i = 0; i < 3; i++) engine.recordDestroyedBlock(invasion, NOW + i);
        store.fail = true;

        assertEquals(InvasionTransition.NO_CHANGE, engine.recordDestroyedBlock(invasion, NOW + 3));
        InvasionState state = engine.status("guild-a").orElseThrow();
        assertEquals(InvasionStatus.ACTIVE, state.status());
        assertEquals(75, state.damage().percent());
        assertEquals(1, engine.activeInvasions().size());
    }

    @Test
    void devastationFailureRestoresHistoricalGuildMaximum() {
        ToggleStore store = new ToggleStore();
        UUID invasion = UUID.randomUUID();
        store.records = List.of(
                new InvasionRecord(UUID.randomUUID(), "guild-a", "Guild A", "world", 1, 2, 3,
                        InvasionStatus.DEFENDED, 2, List.of(), new GuildDamage(5, 100), NOW - 2),
                new InvasionRecord(invasion, "guild-a", "Guild A", "world", 1, 2, 3,
                        InvasionStatus.ACTIVE, 0, List.of(), new GuildDamage(2, 50), NOW));
        InvasionEngine engine = engine(store);
        store.fail = true;

        assertEquals(InvasionStatus.ACTIVE, engine.status("guild-a").orElseThrow().status());
        assertEquals(new GuildDamage(2, 50), engine.status("guild-a").orElseThrow().damage());
    }

    @Test
    void invasionStateSnapshotsEntityListAndRejectsNull() {
        List<UUID> entities = new ArrayList<>();
        UUID entity = UUID.randomUUID();
        entities.add(entity);
        InvasionState state = new InvasionState(UUID.randomUUID(), "guild-a", "Guild A", "world",
                1, 2, 3, InvasionStatus.ACTIVE, 0, entities, new GuildDamage(0, 0), NOW);

        entities.clear();

        assertEquals(List.of(entity), state.currentWaveEntities());
        assertThrows(NullPointerException.class, () -> new InvasionState(
                UUID.randomUUID(), "guild-a", "Guild A", "world", 1, 2, 3,
                InvasionStatus.ACTIVE, 0, null, new GuildDamage(0, 0), NOW));
    }

    @Test
    void recoverFailureRestoresActiveRecordsAndIndexes() {
        ToggleStore store = new ToggleStore();
        UUID invasion = UUID.randomUUID();
        store.records = List.of(new InvasionRecord(invasion, "guild-a", "Guild A", "world", 1, 2, 3,
                InvasionStatus.ACTIVE, 0, List.of(), new GuildDamage(0, 0), NOW));
        InvasionEngine engine = engine(store);
        store.fail = true;

        engine.recover(NOW + 1);

        assertEquals(InvasionStatus.ACTIVE, engine.status("guild-a").orElseThrow().status());
        assertEquals(invasion, engine.activeInvasions().getFirst().invasionId());
    }

    @Test
    void reportsWaveClearedBeforeNextWave() {
        InvasionEngine engine = engine(new MemoryStore());
        UUID invasion = engine.start("guild-a", "Guild A", "world", 1, 2, 3, NOW).invasionId();
        UUID mob = UUID.randomUUID();
        engine.mobSpawned(invasion, mob);

        assertEquals(List.of(InvasionTransition.WAVE_CLEARED, InvasionTransition.NEXT_WAVE),
                engine.mobRemoved(invasion, mob, NOW + 1));
    }

    private static InvasionEngine engine(InvasionStore store) {
        return new InvasionEngine(CONFIG, store);
    }

    private static final class MemoryStore implements InvasionStore {
        private Collection<InvasionRecord> records = List.of();
        public Collection<InvasionRecord> load() { return records; }
        public void save(Collection<InvasionRecord> records) { this.records = List.copyOf(records); }
    }

    private static final class FailingStore implements InvasionStore {
        public Collection<InvasionRecord> load() { return List.of(); }
        public void save(Collection<InvasionRecord> records) { throw new IllegalStateException("fail"); }
    }

    private static final class ToggleStore implements InvasionStore {
        private Collection<InvasionRecord> records = List.of();
        private boolean fail;
        public Collection<InvasionRecord> load() { return records; }
        public void save(Collection<InvasionRecord> records) {
            if (fail) throw new IllegalStateException("fail");
            this.records = List.copyOf(records);
        }
    }
}

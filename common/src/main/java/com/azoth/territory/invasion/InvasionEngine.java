package com.azoth.territory.invasion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public final class InvasionEngine {
    private final InvasionConfig config;
    private final InvasionStore store;
    private final Map<UUID, InvasionRecord> byId = new LinkedHashMap<>();
    private final Map<String, UUID> activeByGuild = new LinkedHashMap<>();
    private final Map<String, GuildDamage> guildDamage = new LinkedHashMap<>();

    public InvasionEngine(InvasionConfig config, InvasionStore store) {
        this.config = config;
        this.store = store;
        for (InvasionRecord record : store.load()) {
            byId.put(record.invasionId(), record);
            guildDamage.merge(record.guildId(), record.damage(), InvasionEngine::mergeDamage);
            if (record.status() == InvasionStatus.ACTIVE) activeByGuild.put(record.guildId(), record.invasionId());
        }
    }

    public synchronized InvasionStartResult start(String guildId, String guildName, String worldId, double x, double y, double z, long now) {
        UUID existing = activeByGuild.get(guildId);
        if (existing != null) return new InvasionStartResult(InvasionStartStatus.ALREADY_ACTIVE, existing);
        UUID id = UUID.randomUUID();
        InvasionRecord record = new InvasionRecord(id, guildId, guildName, worldId, x, y, z, InvasionStatus.ACTIVE, 0, List.of(), guildDamage.getOrDefault(guildId, new GuildDamage(0, 0)), now);
        byId.put(id, record); activeByGuild.put(guildId, id);
        if (!persist()) { byId.remove(id); activeByGuild.remove(guildId); return new InvasionStartResult(InvasionStartStatus.PERSISTENCE_FAILED); }
        return new InvasionStartResult(InvasionStartStatus.STARTED, id);
    }
    public synchronized void mobSpawned(UUID invasionId, UUID entityId) {
        InvasionRecord record = byId.get(invasionId);
        if (record == null || record.status() != InvasionStatus.ACTIVE || record.currentWaveEntities().contains(entityId)) return;
        mutate(record, copy(record, record.status(), record.wave(), add(record.currentWaveEntities(), entityId), record.damage(), record.updatedAt()));
    }
    public synchronized InvasionTransition mobRemoved(UUID invasionId, UUID entityId, long now) {
        List<InvasionTransition> sequence = mobRemovedSequence(invasionId, entityId, now);
        return sequence.isEmpty() ? InvasionTransition.NO_CHANGE : sequence.getLast();
    }

    public synchronized List<InvasionTransition> mobRemovedSequence(UUID invasionId, UUID entityId, long now) {
        InvasionRecord record = byId.get(invasionId);
        if (record == null || record.status() != InvasionStatus.ACTIVE || !record.currentWaveEntities().contains(entityId)) return List.of();
        List<UUID> remaining = new ArrayList<>(record.currentWaveEntities()); remaining.remove(entityId);
        if (!remaining.isEmpty()) { mutate(record, copy(record, record.status(), record.wave(), remaining, record.damage(), now)); return List.of(InvasionTransition.NO_CHANGE); }
        if (record.wave() < 2) {
            InvasionRecord next = copy(record, record.status(), record.wave() + 1, List.of(), record.damage(), now);
            byId.put(invasionId, next);
            if (!persist()) { byId.put(invasionId, record); return List.of(InvasionTransition.WAVE_CLEARED); }
            return List.of(InvasionTransition.WAVE_CLEARED, InvasionTransition.NEXT_WAVE);
        }
        InvasionRecord next = copy(record, InvasionStatus.DEFENDED, record.wave(), List.of(), record.damage(), now);
        byId.put(invasionId, next); activeByGuild.remove(record.guildId());
        if (!persist()) { byId.put(invasionId, record); activeByGuild.put(record.guildId(), invasionId); return List.of(InvasionTransition.WAVE_CLEARED); }
        return List.of(InvasionTransition.WAVE_CLEARED, InvasionTransition.DEFENDED);
    }
    public synchronized InvasionTransition recordDestroyedBlock(UUID invasionId, long now) {
        InvasionRecord record = byId.get(invasionId);
        if (record == null || record.status() != InvasionStatus.ACTIVE) return InvasionTransition.NO_CHANGE;
        GuildDamage damage = guildDamage.getOrDefault(record.guildId(), record.damage());
        long blocks = damage.destroyedBlocks() + 1;
        GuildDamage nextDamage = new GuildDamage(blocks, (int) Math.min(100, blocks * 100 / config.blockBudget()));
        InvasionStatus status = nextDamage.percent() >= 100 ? InvasionStatus.DEVASTATED : InvasionStatus.ACTIVE;
        InvasionRecord next = copy(record, status, record.wave(), record.currentWaveEntities(), nextDamage, now);
        byId.put(invasionId, next); guildDamage.put(record.guildId(), nextDamage);
        if (status != InvasionStatus.ACTIVE) activeByGuild.remove(record.guildId());
        if (!persist()) { byId.put(invasionId, record); guildDamage.put(record.guildId(), damage); if (status == InvasionStatus.DEVASTATED) activeByGuild.put(record.guildId(), invasionId); }
        return status == InvasionStatus.DEVASTATED ? InvasionTransition.DEVASTATED : InvasionTransition.NO_CHANGE;
    }

    public synchronized InvasionTransition cancel(String guildId, long now) {
        UUID id = activeByGuild.get(guildId); if (id == null) return InvasionTransition.NO_CHANGE;
        InvasionRecord record = byId.get(id);
        InvasionRecord next = copy(record, InvasionStatus.CANCELLED, record.wave(), record.currentWaveEntities(), record.damage(), now);
        byId.put(id, next); activeByGuild.remove(guildId);
        if (!persist()) { byId.put(id, record); activeByGuild.put(guildId, id); return InvasionTransition.NO_CHANGE; }
        return InvasionTransition.CANCELLED;
    }

    public synchronized Optional<InvasionState> status(String guildId) {
        UUID id = activeByGuild.get(guildId);
        if (id == null) return byId.values().stream().filter(r -> r.guildId().equals(guildId)).reduce((a, b) -> b).map(InvasionState::from);
        return Optional.of(InvasionState.from(byId.get(id)));
    }

    public synchronized void recover(long now) {
        Map<UUID, InvasionRecord> oldRecords = new LinkedHashMap<>(byId);
        Map<String, UUID> oldIndexes = new LinkedHashMap<>(activeByGuild);
        for (InvasionRecord record : List.copyOf(byId.values())) if (record.status() == InvasionStatus.ACTIVE) {
            byId.put(record.invasionId(), copy(record, InvasionStatus.CANCELLED, record.wave(), record.currentWaveEntities(), record.damage(), now));
            activeByGuild.remove(record.guildId());
        }
        if (!persist()) { byId.clear(); byId.putAll(oldRecords); activeByGuild.clear(); activeByGuild.putAll(oldIndexes); }
    }

    public synchronized List<InvasionState> activeInvasions() { return activeByGuild.values().stream().map(byId::get).map(InvasionState::from).toList(); }

    private boolean mutate(InvasionRecord old, InvasionRecord next) {
        byId.put(next.invasionId(), next);
        if (!persist()) { byId.put(old.invasionId(), old); return false; }
        return true;
    }

    private boolean persist() { try { store.save(List.copyOf(byId.values())); return true; } catch (RuntimeException ex) { return false; } }
    private static List<UUID> add(List<UUID> source, UUID id) { List<UUID> copy = new ArrayList<>(source); copy.add(id); return List.copyOf(copy); }
    private static GuildDamage mergeDamage(GuildDamage a, GuildDamage b) { return a.destroyedBlocks() >= b.destroyedBlocks() ? a : b; }
    private static InvasionRecord copy(InvasionRecord r, InvasionStatus s, int w, List<UUID> e, GuildDamage d, long now) { return new InvasionRecord(r.invasionId(), r.guildId(), r.guildName(), r.worldId(), r.x(), r.y(), r.z(), s, w, e, d, now); }
}

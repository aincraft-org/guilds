package com.azoth.territory.invasion;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Paper orchestration boundary for the persistence-backed invasion engine. */
public final class InvasionRuntime {
    private final Plugin plugin;
    private final InvasionEngine engine;
    private final GuildInvasionTargetResolver resolver;
    private final InvasionMobSpawner spawner;
    private final InvasionBossBars bossBars;
    private final InvasionConfig config;
    private final int radius;
    private final int attempts;
    private final long waveDelayTicks;
    private final Predicate<Location> claim;
    private final Map<UUID, Set<UUID>> entities = new HashMap<>();
    private final Map<UUID, BukkitTask> schedules = new HashMap<>();
    private final Map<UUID, InvasionRecord> records = new HashMap<>();
    private final Set<UUID> failedInvasions = new HashSet<>();
    private boolean disabled;

    public InvasionRuntime(Plugin plugin, InvasionEngine engine, GuildInvasionTargetResolver resolver,
                           InvasionMobSpawner spawner, InvasionBossBars bossBars, InvasionConfig config,
                           int radius, int attempts, long waveDelayTicks, Predicate<Location> claim) {
        this.plugin = plugin; this.engine = engine; this.resolver = resolver; this.spawner = spawner;
        this.bossBars = bossBars; this.config = config; this.radius = radius; this.attempts = attempts;
        this.waveDelayTicks = waveDelayTicks; this.claim = claim;
    }

    public synchronized InvasionStartResult start(String guildName, long now) {
        if (disabled) return new InvasionStartResult(InvasionStartStatus.PERSISTENCE_FAILED);
        GuildInvasionTargetResolver.Resolution target = resolver.resolve(guildName);
        if (target.isRejected()) return new InvasionStartResult(InvasionStartStatus.PERSISTENCE_FAILED);
        var t = target.target().orElseThrow();
        var result = engine.start(t.guildId(), t.guildName(), t.center().getWorld(), t.center().getX(), t.center().getY(), t.center().getZ(), now);
        if (result.status() != InvasionStartStatus.STARTED) return result;
        UUID id = result.invasionId();
        InvasionState started = engine.status(t.guildId()).orElseThrow(
                () -> new IllegalStateException("started invasion missing engine state"));
        records.put(id, record(started));
        if (!spawnWave(id, started.wave(), now)) {
            failClosed(id, now, "required invasion wave could not spawn");
            return new InvasionStartResult(InvasionStartStatus.PERSISTENCE_FAILED);
        }
        return result;
    }

    public synchronized void recover(long now) {
        engine.recover(now);
        for (InvasionState state : engine.activeInvasions()) {
            records.put(state.invasionId(), record(state));
        }
        for (Entity entity : Bukkit.getWorlds().stream().flatMap(world -> world.getEntities().stream()).toList()) {
            OptionalTag tag = tag(entity);
            if (tag.id != null && (engine.status(tag.guild).isEmpty()
                    || engine.status(tag.guild).map(s -> !s.invasionId().equals(tag.id) || s.status() != InvasionStatus.ACTIVE).orElse(true))) {
                entity.remove();
            }
        }
    }


    public synchronized void onEntityDeath(Entity entity, long now) { onEntityRemoved(entity, now); }
    public synchronized void onEntityRemoved(Entity entity, long now) {
        if (entity == null) return;
        OptionalTag tag = tag(entity);
        if (tag.id == null || tag.guild == null) return;
        Set<UUID> known = entities.get(tag.id);
        if (known == null || !known.remove(entity.getUniqueId())) return;
        InvasionMutationResult result = engine.mobRemovedResult(tag.id, entity.getUniqueId(), now);
        if (result == InvasionMutationResult.PERSISTENCE_FAILED) {
            failedInvasions.add(tag.id);
            failClosed(tag.id, now, "failed to persist invasion mob removal");
            return;
        }
        List<InvasionTransition> transitions = engine.mobRemoved(tag.id, entity.getUniqueId(), now);
        handleTransitions(tag.id, transitions, now);
        engine.status(tag.guild).ifPresent(state -> bossBars.update(record(state), config.waves().size()));
    }

    public synchronized void disable(long now) {
        disabled = true;
        for (BukkitTask task : schedules.values()) task.cancel();
        schedules.clear();
        for (InvasionState state : engine.activeInvasions()) cancel(state.guildId(), now);
    }

    public synchronized void cancel(String guildId, long now) {
        engine.cancel(guildId, now);
        engine.status(guildId).ifPresent(state -> cleanup(state.invasionId(), state));
    }
    public synchronized void finish(String guildId, UUID invasionId) {
        InvasionState state = engine.status(guildId).orElse(null);
        if (state != null && state.invasionId().equals(invasionId)) cleanup(invasionId, state);
    }

    public synchronized java.util.Optional<InvasionState> status(String guildId) { return engine.status(guildId); }
    public synchronized boolean owns(UUID invasionId, UUID entityId, String guildId) {
        return entities.getOrDefault(invasionId, Set.of()).contains(entityId)
                && engine.status(guildId).map(s -> s.invasionId().equals(invasionId) && s.status() == InvasionStatus.ACTIVE).orElse(false);
    }
    public synchronized boolean canDestroy(UUID invasionId, String guildId) {
        return !failedInvasions.contains(invasionId)
                && engine.status(guildId).map(s -> s.invasionId().equals(invasionId) && s.status() == InvasionStatus.ACTIVE).orElse(false);
    }

    private boolean spawnWave(UUID id, int wave, long now) {
        InvasionRecord record = records.get(id);
        if (record == null) return false;
        InvasionState state = engine.status(record.guildId()).orElse(null);
        if (state == null || state.status() != InvasionStatus.ACTIVE) return false;
        List<UUID> spawned = spawner.spawn(record(state), config.waves().get(wave), radius, attempts, claim);
        if (spawned.isEmpty()) return false;
        Set<UUID> known = entities.computeIfAbsent(id, ignored -> new HashSet<>());
        for (UUID entity : spawned) {
            InvasionMutationResult result = engine.mobSpawned(id, entity);
            if (result == InvasionMutationResult.PERSISTENCE_FAILED) {
                failedInvasions.add(id);
                return false;
            }
            known.add(entity);
        }
        engine.status(record.guildId()).ifPresent(next -> bossBars.open(record(next), config.waves().size(), spawned.size()));
        return true;
    }
    private void handleTransitions(UUID id, List<InvasionTransition> transitions, long now) {
        if (transitions.contains(InvasionTransition.NEXT_WAVE)) {
            BukkitTask old = schedules.remove(id); if (old != null) old.cancel();
            schedules.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                synchronized (this) {
                    schedules.remove(id);
                    InvasionState state = engine.status(records.get(id).guildId()).orElse(null);
                    if (state == null || state.status() != InvasionStatus.ACTIVE || !spawnWave(id, state.wave(), now)) failClosed(id, now, "required invasion wave could not spawn");
                }
            }, waveDelayTicks));
        }
        if (transitions.contains(InvasionTransition.DEFENDED) || transitions.contains(InvasionTransition.DEVASTATED)) {
            InvasionState state = engine.status(records.get(id).guildId()).orElse(null);
            cleanup(id, state);
        }
    }

    private void failClosed(UUID id, long now, String message) {
        failedInvasions.add(id);
        plugin.getLogger().log(Level.SEVERE, message + " for invasion " + id);
        InvasionRecord record = records.get(id);
        InvasionState state = record == null ? null : engine.status(record.guildId()).orElse(null);
        if (record != null) engine.cancel(record.guildId(), now);
        cleanup(id, state);
    }

    private void cleanup(UUID id, InvasionState state) {
        BukkitTask task = schedules.remove(id); if (task != null) task.cancel();
        for (UUID entityId : entities.getOrDefault(id, Set.of())) { Entity entity = Bukkit.getEntity(entityId); if (entity != null) entity.remove(); }
        entities.remove(id);
        if (state != null) bossBars.remove(record(state));
        records.remove(id);
    }

    private static InvasionRecord record(InvasionState s) { return new InvasionRecord(s.invasionId(), s.guildId(), s.guildName(), s.worldId(), s.x(), s.y(), s.z(), s.status(), s.wave(), s.currentWaveEntities(), s.damage(), s.updatedAt()); }
    private static OptionalTag tag(Entity entity) { return new OptionalTag(InvasionMobTags.invasionId(entity.getPersistentDataContainer()).orElse(null), InvasionMobTags.guildId(entity.getPersistentDataContainer()).orElse(null)); }
    private record OptionalTag(UUID id, String guild) {}
}

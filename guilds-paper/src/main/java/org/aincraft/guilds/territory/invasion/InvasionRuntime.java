package org.aincraft.guilds.territory.invasion;

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
import java.util.function.Function;
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
    private final java.util.function.BiPredicate<Location, String> claim;
    private final Function<String, Set<UUID>> residents;
    private final Map<UUID, Set<UUID>> entities = new HashMap<>();
    private final Map<UUID, BukkitTask> schedules = new HashMap<>();
    private final Map<UUID, InvasionRecord> records = new HashMap<>();
    private final Set<UUID> failedInvasions = new HashSet<>();
    private boolean disabled;

    public InvasionRuntime(Plugin plugin, InvasionEngine engine, GuildInvasionTargetResolver resolver,
                           InvasionMobSpawner spawner, InvasionBossBars bossBars, InvasionConfig config,
                           int radius, int attempts, long waveDelayTicks,
                           java.util.function.BiPredicate<Location, String> claim) {
        this(plugin, engine, resolver, spawner, bossBars, config, radius, attempts, waveDelayTicks,
                claim, ignored -> Set.of());
    }

    public InvasionRuntime(Plugin plugin, InvasionEngine engine, GuildInvasionTargetResolver resolver,
                           InvasionMobSpawner spawner, InvasionBossBars bossBars, InvasionConfig config,
                           int radius, int attempts, long waveDelayTicks,
                           java.util.function.BiPredicate<Location, String> claim,
                           Function<String, Set<UUID>> residents) {
        this.plugin = plugin; this.engine = engine; this.resolver = resolver; this.spawner = spawner;
        this.bossBars = bossBars; this.config = config; this.radius = radius; this.attempts = attempts;
        this.waveDelayTicks = waveDelayTicks; this.claim = claim; this.residents = residents;
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
        List<InvasionState> activeBeforeRecovery = engine.activeInvasions();
        InvasionMutationResult result = engine.recover(now);
        if (result == InvasionMutationResult.PERSISTENCE_FAILED) {
            for (InvasionState state : activeBeforeRecovery) {
                failedInvasions.add(state.invasionId());
            }
            plugin.getLogger().severe("Failed to cancel active invasions during startup recovery; destruction remains disabled");
        }
        for (Entity entity : Bukkit.getWorlds().stream().flatMap(world -> world.getEntities().stream()).toList()) {
            removeIfStale(entity);
        }
    }

    public synchronized void removeIfStale(Entity entity) {
        OptionalTag tag = tag(entity);
        if (tag.id == null || tag.guild == null) return;
        boolean active = !failedInvasions.contains(tag.id)
                && engine.status(tag.guild).map(s -> s.invasionId().equals(tag.id)
                && s.status() == InvasionStatus.ACTIVE).orElse(false);
        if (!active) entity.remove();
    }


    public synchronized void onEntityDeath(Entity entity, long now) { onEntityRemoved(entity, now); }
    public synchronized void onEntityRemoved(Entity entity, long now) {
        if (entity == null) return;
        OptionalTag tag = tag(entity);
        if (tag.id == null || tag.guild == null) return;
        Set<UUID> known = entities.get(tag.id);
        if (known == null || !known.remove(entity.getUniqueId())) return;
        InvasionRemovalResult result = engine.mobRemoved(tag.id, entity.getUniqueId(), now);
        if (result.mutation() == InvasionMutationResult.PERSISTENCE_FAILED) {
            failClosed(tag.id, now, "failed to persist invasion mob removal");
            return;
        }
        handleTransitions(tag.id, result.transitions(), now);
        engine.status(tag.guild).ifPresent(state -> bossBars.update(record(state), config.waves().size()));
    }

    public synchronized java.util.Optional<String> resolveGuildId(String guildName) {
        GuildInvasionTargetResolver.Resolution resolution = resolver.resolve(guildName);
        return resolution.target().map(GuildInvasionTargetResolver.ResolvedInvasionTarget::guildId);
    }

    public synchronized void disable(long now) {
        disabled = true;
        for (BukkitTask task : schedules.values()) task.cancel();
        schedules.clear();
        for (InvasionState state : List.copyOf(engine.activeInvasions())) cancel(state.guildId(), now);
    }
    public synchronized void reconcileBossBars() {
        for (InvasionState state : engine.activeInvasions()) {
            InvasionRecord record = record(state);
            bossBars.reconcile(record, config.waves().size(), residents.apply(state.guildId()));
        }
    }

    public synchronized boolean cancel(String guildId, long now) {
        InvasionState before = engine.status(guildId).orElse(null);
        if (before == null || before.status() != InvasionStatus.ACTIVE) return false;
        failedInvasions.add(before.invasionId());
        boolean cancelled = engine.cancel(guildId, now) == InvasionTransition.CANCELLED;
        cleanup(before.invasionId(), before);
        return cancelled;
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
        List<UUID> spawned;
        try { spawned = spawner.spawn(record(state), config.waves().get(wave), radius, attempts, location -> claim.test(location, record.guildId())); }
        catch (RuntimeException failure) { return false; }
        if (spawned.isEmpty()) return false;
        Set<UUID> known = entities.computeIfAbsent(id, ignored -> new HashSet<>());
        known.addAll(spawned);
        for (UUID entity : spawned) {
            InvasionMutationResult result = engine.mobSpawned(id, entity);
            if (result == InvasionMutationResult.PERSISTENCE_FAILED) {
                failedInvasions.add(id);
                return false;
            }
        }
        engine.status(record.guildId()).ifPresent(next -> {
            bossBars.open(record(next), config.waves().size(), spawned.size());
            bossBars.reconcile(record(next), config.waves().size(), residents.apply(next.guildId()));
        });
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

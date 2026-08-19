package org.aincraft.guilds.territory.standing;

import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.permission.GuildBody;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pure-domain standing engine (spec §4–§7). Thread-safe: all mutations are
 * {@code synchronized}. Standing accrues only to the governing guild of a
 * territory, from its members' activity inside that territory. Tiers derived
 * from {@link StandingConfig}; harvest and influence multipliers are read
 * through {@link StandingService}.
 */
public final class StandingEngine implements StandingService {

    private final GovernanceRegistry governance;
    private final StandingConfig config;
    private final PostgresStandingStore store;
    private final Logger log;

    private final StandingState state = new StandingState();
    private boolean dirty;
    /** True when PostgreSQL state could not be loaded — subsystem fails closed. */
    private boolean loadFailed;

    public StandingEngine(
            GovernanceRegistry governance,
            StandingConfig config,
            PostgresStandingStore store,
            Logger log
    ) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.config = Objects.requireNonNull(config, "config");
        // Store is nullable in tests that never flush/recover; the engine
        // only touches it inside flush()/recover().
        this.store = store;
        this.log = Objects.requireNonNull(log, "log");
    }

    private boolean unusable() {
        return loadFailed;
    }

    private TerritoryRegistry territories() {
        return governance.territories();
    }

    // ── Accrual ───────────────────────────────────────────────────────────

    /**
     * Record one activity event. Only members of the territory's governing
     * guild accrue (each event adds the source value to the owner's bar).
     * Returns the updated bar, or empty when the event was a no-op
     * (unknown/un-governed territory, or actor not in the governing guild).
     */
    public synchronized Optional<StandingBar> accrue(
            String territoryId,
            String guildId,
            StandingSource source
    ) {
        if (unusable()) {
            return Optional.empty();
        }
        StandingEntry entry = syncedEntry(territoryId);
        if (entry == null) {
            return Optional.empty();
        }
        if (guildId == null || !guildId.equals(entry.ownerGuildId)) {
            return Optional.empty();
        }
        double value = round2(entry.bars.getOrDefault(guildId, 0.0) + config.valueOf(source));
        entry.bars.put(guildId, Math.min(config.cap(), value));
        dirty = true;
        return Optional.of(new StandingBar(guildId, entry.bars.get(guildId)));
    }

    /**
     * Territory entry synced to the current owner; null when the territory is
     * unknown or ungoverned. On external rebind, bars reset (spec §14:
     * owner-change resets the standing bar).
     */
    private StandingEntry syncedEntry(String territoryId) {
        if (territoryId == null || territoryId.isBlank()) {
            return null;
        }
        Optional<Territory> t = territories().get(territoryId.trim());
        if (t.isEmpty()) {
            return null;
        }
        String owner = t.get().governedByGuildId().orElse(null);
        if (owner == null) {
            return null;
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            entry = new StandingEntry();
            entry.ownerGuildId = owner;
            state.entries.put(territoryId.trim(), entry);
            return entry;
        }
        if (!owner.equals(entry.ownerGuildId)) {
            entry.bars.clear();
            entry.ownerGuildId = owner;
            dirty = true;
            log.info("Standing state reset for " + territoryId.trim() + ": owner changed to " + owner);
        }
        return entry;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    @Override
    public synchronized Optional<TerritoryStandingState> standing(String territoryId) {
        if (unusable()) {
            return Optional.empty();
        }
        if (territoryId == null || territoryId.isBlank()) {
            return Optional.empty();
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(territoryId.trim(), entry));
    }

    @Override
    public synchronized List<TerritoryStandingState> all() {
        if (unusable()) {
            return List.of();
        }
        List<TerritoryStandingState> out = new ArrayList<>();
        for (Map.Entry<String, StandingEntry> e : state.entries.entrySet()) {
            out.add(toSnapshot(e.getKey(), e.getValue()));
        }
        return List.copyOf(out);
    }

    private static TerritoryStandingState toSnapshot(String territoryId, StandingEntry entry) {
        List<StandingBar> bars = new ArrayList<>();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            bars.add(new StandingBar(bar.getKey(), bar.getValue()));
        }
        bars.sort((a, b) -> a.guildId().compareTo(b.guildId()));
        return new TerritoryStandingState(territoryId, entry.ownerGuildId, bars);
    }

    @Override
    public synchronized double harvestMultiplierFor(String territoryId, String guildId) {
        if (unusable() || territoryId == null || guildId == null) {
            return 1.0;
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return 1.0;
        }
        if (!guildId.equals(entry.ownerGuildId)) {
            return 1.0;
        }
        double bar = entry.bars.getOrDefault(guildId, 0.0);
        return config.highestTierFor(bar).map(StandingTier::harvestMultiplier).orElse(1.0);
    }

    @Override
    public synchronized double influenceMultiplierFor(String guildId) {
        if (unusable() || guildId == null || guildId.isBlank()) {
            return 1.0;
        }
        double max = 1.0;
        for (Map.Entry<String, StandingEntry> e : state.entries.entrySet()) {
            StandingEntry entry = e.getValue();
            if (!guildId.equals(entry.ownerGuildId)) {
                continue;
            }
            double bar = entry.bars.getOrDefault(guildId, 0.0);
            double tierMultiplier = config.highestTierFor(bar)
                    .map(StandingTier::influenceMultiplier).orElse(1.0);
            if (tierMultiplier > max) {
                max = tierMultiplier;
            }
        }
        return max;
    }

    @Override
    public synchronized Optional<StandingTier> tierFor(String territoryId, String guildId) {
        if (unusable() || territoryId == null || guildId == null) {
            return Optional.empty();
        }
        StandingEntry entry = state.entries.get(territoryId.trim());
        if (entry == null || !guildId.equals(entry.ownerGuildId)) {
            return Optional.empty();
        }
        double bar = entry.bars.getOrDefault(guildId, 0.0);
        return config.highestTierFor(bar);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /** Load-time recovery: drop missing territories, reset on owner mismatch. */
    public synchronized void recover(long nowEpochMs) {
        if (store == null) {
            return;
        }
        StandingState loaded;
        try {
            loaded = store.load();
        } catch (IOException e) {
            state.entries.clear();
            dirty = false;
            loadFailed = true;
            log.log(Level.SEVERE,
                    "Failed to load standing state from PostgreSQL; standing subsystem disabled", e);
            return;
        }
        state.entries.clear();
        state.entries.putAll(loaded.entries);

        for (Map.Entry<String, StandingEntry> e : new ArrayList<>(state.entries.entrySet())) {
            String territoryId = e.getKey();
            StandingEntry entry = e.getValue();
            Optional<Territory> t = territories().get(territoryId);
            if (t.isEmpty()) {
                state.entries.remove(territoryId);
                dirty = true;
                log.warning("Dropped standing state for missing territory " + territoryId);
                continue;
            }
            String currentOwner = t.get().governedByGuildId().orElse(null);
            if (!Objects.equals(currentOwner, entry.ownerGuildId)) {
                entry.bars.clear();
                entry.ownerGuildId = currentOwner;
                dirty = true;
                log.info("Standing state reset for " + territoryId + ": owner changed to " + currentOwner);
            }
        }
    }

    /** Batched flush of dirty bar mutations (spec §10). */
    public synchronized void flush() throws IOException {
        if (unusable() || !dirty || store == null) {
            return;
        }
        store.save(state);
        dirty = false;
    }

    // ── Admin overrides ───────────────────────────────────────────────────

    /** Admin: set the owner's standing bar (clamped to [0, cap]). */
    @Override
    public synchronized boolean adminSet(String territoryId, String guildId, double value) {
        if (unusable() || territoryId == null || guildId == null
                || territoryId.isBlank() || guildId.isBlank()) {
            return false;
        }
        StandingEntry entry = syncedEntry(territoryId);
        if (entry == null) {
            return false;
        }
        double clamped = Math.max(0.0, Math.min(config.cap(), value));
        if (clamped <= 0) {
            entry.bars.remove(guildId);
        } else {
            entry.bars.put(guildId, round2(clamped));
        }
        dirty = true;
        return true;
    }

    /** Admin: drop all standing state for a territory (persisted on next flush). */
    @Override
    public synchronized boolean adminReset(String territoryId) {
        if (unusable() || territoryId == null) {
            return false;
        }
        boolean removed = state.entries.remove(territoryId) != null;
        if (removed) {
            dirty = true;
        }
        return removed;
    }
}

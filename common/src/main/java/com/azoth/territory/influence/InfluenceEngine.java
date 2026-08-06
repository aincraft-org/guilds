package com.azoth.territory.influence;

import com.azoth.territory.model.Territory;
import com.azoth.territory.permission.AllianceBody;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.permission.GuildBody;
import com.azoth.territory.registry.TerritoryRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pure-domain influence race engine (spec §3–§6).
 * <p>
 * Thread-safe: all mutations are {@code synchronized}. Ownership of the
 * territories registry is shared with the plugin; flips register the new
 * owner and ask {@link OwnershipPersister} to persist territories.json.
 */
public final class InfluenceEngine implements InfluenceService {

    /** Persists the territory ownership change (step 2 of the flip journal). */
    public interface OwnershipPersister {
        void persist(String territoryId, String newOwnerGuildId) throws IOException;
    }

    /** A completed takeover, for server broadcasts. */
    public record TerritoryFlip(String territoryId, String oldOwnerGuildId, String newOwnerGuildId) {
    }

    private final GovernanceRegistry governance;
    private final InfluenceConfig config;
    private final InfluenceStore store;
    private final OwnershipPersister persister;
    private final Logger log;

    private final InfluenceState state = new InfluenceState();
    private boolean dirty;
    /** True when even the corrupt-file backup failed — subsystem fails closed. */
    private boolean loadFailed;

    public InfluenceEngine(
            GovernanceRegistry governance,
            InfluenceConfig config,
            InfluenceStore store,
            OwnershipPersister persister,
            Logger log
    ) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
        this.persister = Objects.requireNonNull(persister, "persister");
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
     * Record one activity event. Attacker events (guild != owner) add the
     * source value to the guild's bar; owner-guild events subtract the
     * defender value from every attacker bar. Returns the actor's updated
     * bar, or empty when the event was a no-op (ineligible, defender, locked).
     *
     * @param victimGuildId primary guild of a PvP victim (only PVP_KILL);
     *                      same-alliance victims accrue nothing
     */
    public synchronized Optional<InfluenceBar> accrue(
            String territoryId,
            String guildId,
            InfluenceSource source,
            long nowEpochMs,
            String victimGuildId
    ) {
        if (unusable()) {
            return Optional.empty();
        }
        TerritoryEntry entry = syncedEntry(territoryId, nowEpochMs);
        if (entry == null) {
            return Optional.empty();
        }
        if (isCooldownActive(entry, nowEpochMs) || entry.declaration != null) {
            return Optional.empty();
        }
        if (source == InfluenceSource.PVP_KILL && victimGuildId != null && !victimGuildId.isBlank()
                && sameAlliance(guildId, victimGuildId.trim())) {
            return Optional.empty();
        }
        if (guildId != null && guildId.equals(entry.ownerGuildId)) {
            defend(entry, source);
            return Optional.empty();
        }
        if (!canContest(entry.ownerGuildId, guildId)) {
            return Optional.empty();
        }
        double value = round2(entry.bars.getOrDefault(guildId, 0.0) + config.valueOf(source));
        entry.bars.put(guildId, Math.min(config.cap(), value));
        dirty = true;
        return Optional.of(new InfluenceBar(guildId, entry.bars.get(guildId)));
    }

    private void defend(TerritoryEntry entry, InfluenceSource source) {
        double defenderValue = config.defenderValueOf(source);
        if (defenderValue <= 0) {
            return;
        }
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            double next = round2(bar.getValue() - defenderValue);
            if (next <= 0) {
                toRemove.add(bar.getKey());
            } else {
                bar.setValue(next);
            }
        }
        for (String guildId : toRemove) {
            entry.bars.remove(guildId);
        }
        dirty = true;
    }

    // ── Eligibility ───────────────────────────────────────────────────────

    private boolean canContest(String ownerGuildId, String attackerGuildId) {
        if (ownerGuildId == null || attackerGuildId == null || ownerGuildId.isBlank()
                || attackerGuildId.isBlank() || ownerGuildId.equals(attackerGuildId)) {
            return false;
        }
        Optional<GuildBody> owner = governance.source().guild(ownerGuildId);
        Optional<GuildBody> attacker = governance.source().guild(attackerGuildId);
        if (owner.isEmpty() || attacker.isEmpty()) {
            return false;
        }
        Optional<AllianceBody> ownerAlliance = governance.source().allianceContainingGuild(ownerGuildId);
        Optional<AllianceBody> attackerAlliance = governance.source().allianceContainingGuild(attackerGuildId);
        if (ownerAlliance.isEmpty() || attackerAlliance.isEmpty()) {
            return false;
        }
        return !ownerAlliance.get().id().equals(attackerAlliance.get().id());
    }

    private boolean sameAlliance(String guildA, String guildB) {
        if (guildA == null || guildB == null || guildA.isBlank() || guildB.isBlank()) {
            return false;
        }
        return governance.source().allianceContainingGuild(guildA)
                .flatMap(a -> governance.source().allianceContainingGuild(guildB)
                        .map(b -> a.id().equals(b.id())))
                .orElse(false);
    }

    /**
     * Territory entry synced to the current owner; null when the territory is
     * unknown or ungoverned. On external rebind, bars + declaration reset
     * (cooldown kept, spec §6 rule 2).
     */
    private TerritoryEntry syncedEntry(String territoryId, long nowEpochMs) {
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
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            entry = new TerritoryEntry();
            entry.ownerGuildId = owner;
            state.entries.put(territoryId.trim(), entry);
            return entry;
        }
        if (!owner.equals(entry.ownerGuildId)) {
            entry.bars.clear();
            entry.declaration = null;
            entry.ownerGuildId = owner;
            dirty = true;
            log.info("Influence state reset for " + territoryId.trim() + ": owner changed to " + owner);
        }
        return entry;
    }

    private static boolean isCooldownActive(TerritoryEntry entry, long nowEpochMs) {
        return entry.cooldownUntilEpochMs > nowEpochMs;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    // ── Queries ───────────────────────────────────────────────────────────

    @Override
    public synchronized Optional<TerritoryInfluenceState> influence(String territoryId) {
        if (unusable()) {
            return Optional.empty();
        }
        if (territoryId == null || territoryId.isBlank()) {
            return Optional.empty();
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(territoryId.trim(), entry));
    }

    @Override
    public synchronized List<TerritoryInfluenceState> all() {
        if (unusable()) {
            return List.of();
        }
        List<TerritoryInfluenceState> out = new ArrayList<>();
        for (Map.Entry<String, TerritoryEntry> e : state.entries.entrySet()) {
            out.add(toSnapshot(e.getKey(), e.getValue()));
        }
        return List.copyOf(out);
    }

    private static TerritoryInfluenceState toSnapshot(String territoryId, TerritoryEntry entry) {
        List<InfluenceBar> bars = new ArrayList<>();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            bars.add(new InfluenceBar(bar.getKey(), bar.getValue()));
        }
        bars.sort((a, b) -> a.guildId().compareTo(b.guildId()));
        return new TerritoryInfluenceState(territoryId, entry.ownerGuildId,
                entry.cooldownUntilEpochMs, bars, entry.declaration);
    }

    @Override
    public synchronized boolean isDeclarable(String territoryId, String guildId, long nowEpochMs) {
        if (unusable()) {
            return false;
        }
        if (territoryId == null || guildId == null || territoryId.isBlank() || guildId.isBlank()) {
            return false;
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null) {
            return false;
        }
        if (isCooldownActive(entry, nowEpochMs) || entry.declaration != null) {
            return false;
        }
        if (!canContest(entry.ownerGuildId, guildId)) {
            return false;
        }
        Double bar = entry.bars.get(guildId);
        return bar != null && bar >= config.cap();
    }

    @Override
    public synchronized boolean isCooldownActive(String territoryId, long nowEpochMs) {
        if (unusable()) {
            return false;
        }
        if (territoryId == null || territoryId.isBlank()) {
            return false;
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        return entry != null && isCooldownActive(entry, nowEpochMs);
    }

    // ── Declaration lifecycle (implemented in Task 4) ─────────────────────

    @Override
    public synchronized DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    @Override
    public synchronized DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized List<TerritoryFlip> tickFlips(long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized List<TerritoryFlip> recover(long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized void flush() throws IOException {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized boolean adminSet(String territoryId, String guildId, double value, long nowEpochMs) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    public synchronized boolean adminReset(String territoryId) {
        throw new UnsupportedOperationException("declared in Task 4");
    }

    /** Configured influence cap (public for display). */
    public double cap() {
        return config.cap();
    }
}

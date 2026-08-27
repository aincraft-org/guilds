package dev.mintychochip.territory.influence;

import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.guilds.alliances.Alliance;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.guilds.Guild;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import dev.mintychochip.territory.standing.StandingService;

import java.io.IOException;
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
 * owner and ask {@link OwnershipPersister} to persist the ownership change.
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
    private final PostgresInfluenceStore store;
    private final OwnershipPersister persister;
    private final Logger log;
    private final StandingService standingService;

    private final InfluenceState state = new InfluenceState();
    private boolean dirty;
    /** True when PostgreSQL state could not be loaded — subsystem fails closed. */
    private boolean loadFailed;

    public InfluenceEngine(
            GovernanceRegistry governance,
            InfluenceConfig config,
            PostgresInfluenceStore store,
            OwnershipPersister persister,
            Logger log
    ) {
        this(governance, config, store, persister, log, null);
    }

    public InfluenceEngine(
            GovernanceRegistry governance,
            InfluenceConfig config,
            PostgresInfluenceStore store,
            OwnershipPersister persister,
            Logger log,
            StandingService standingService
    ) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
        this.persister = Objects.requireNonNull(persister, "persister");
        this.log = Objects.requireNonNull(log, "log");
        this.standingService = standingService;
    }

    /** Influence accrual multiplier from the standing engine (1.0 when absent). */
    private double influenceMultiplierFor(String guildId) {
        if (standingService == null) {
            return 1.0;
        }
        return standingService.influenceMultiplierFor(guildId);
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
        double value = round2(entry.bars.getOrDefault(guildId, 0.0)
                + config.valueOf(source) * influenceMultiplierFor(guildId));
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
        Optional<Guild> owner = governance.source().guild(ownerGuildId);
        Optional<Guild> attacker = governance.source().guild(attackerGuildId);
        if (owner.isEmpty() || attacker.isEmpty()) {
            return false;
        }
        Optional<Alliance> ownerAlliance = governance.source().allianceContainingGuild(ownerGuildId);
        Optional<Alliance> attackerAlliance = governance.source().allianceContainingGuild(attackerGuildId);
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

    // ── Declaration lifecycle ─────────────────────────────────────────────

    @Override
    public synchronized DeclareResult declare(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        if (territoryId == null || territoryId.isBlank()) {
            return DeclareResult.error(DeclareStatus.TERRITORY_UNKNOWN, "territory is required");
        }
        if (unusable()) {
            return DeclareResult.error(DeclareStatus.NOT_ELIGIBLE,
                    "influence subsystem unavailable (corrupt state file)");
        }
        TerritoryEntry entry = syncedEntry(territoryId, nowEpochMs);
        if (entry == null) {
            Optional<Territory> t = territories().get(territoryId.trim());
            if (t.isEmpty()) {
                return DeclareResult.error(DeclareStatus.TERRITORY_UNKNOWN, "unknown territory: " + territoryId);
            }
            return DeclareResult.error(DeclareStatus.UNGOVERNABLE,
                    "territory is not governed by a guild");
        }
        if (isCooldownActive(entry, nowEpochMs)) {
            return DeclareResult.error(DeclareStatus.NOT_ELIGIBLE,
                    "post-takeover cooldown is still active");
        }
        if (entry.declaration != null) {
            return DeclareResult.error(DeclareStatus.RACE_ACTIVE,
                    "a declaration is already active on this territory");
        }
        if (!canContest(entry.ownerGuildId, guildId)) {
            return DeclareResult.error(DeclareStatus.NOT_ELIGIBLE,
                    "your guild may not contest this territory (alliance gate)");
        }
        Optional<Guild> attacker = governance.source().guild(guildId);
        if (attacker.isEmpty() || !attacker.get().government().holderIds().contains(authorityId)) {
            return DeclareResult.error(DeclareStatus.NOT_AUTHORIZED,
                    "you need a seat in your guild's government to declare");
        }
        Double bar = entry.bars.get(guildId);
        if (bar == null || bar < config.cap()) {
            return DeclareResult.error(DeclareStatus.NOT_AT_CAP,
                    "your guild has not reached 100% influence");
        }
        long flipAt = nowEpochMs + config.declareCountdownEpochMs();
        entry.declaration = new Declaration(guildId, nowEpochMs, flipAt);
        dirty = true;
        if (!persistSync()) {
            entry.declaration = null; // roll back so a retry is safe
            dirty = true;
            return DeclareResult.error(DeclareStatus.STORAGE_ERROR,
                    "could not persist the declaration — please retry");
        }
        log.info("Territory " + territoryId + ": declaration by guild " + guildId
                + ", flip at " + flipAt);
        return DeclareResult.ok(DeclareStatus.DECLARED, "declaration filed; territory flips in "
                + config.declareCountdownHours() + "h");
    }

    @Override
    public synchronized DeclareResult cancelDeclaration(String territoryId, String guildId, String authorityId, long nowEpochMs) {
        if (territoryId == null || territoryId.isBlank()) {
            return DeclareResult.error(DeclareStatus.TERRITORY_UNKNOWN, "territory is required");
        }
        if (unusable()) {
            return DeclareResult.error(DeclareStatus.RACE_ACTIVE,
                    "influence subsystem unavailable (corrupt state file)");
        }
        TerritoryEntry entry = state.entries.get(territoryId.trim());
        if (entry == null || entry.declaration == null) {
            return DeclareResult.error(DeclareStatus.RACE_ACTIVE, "no active declaration on this territory");
        }
        if (!entry.declaration.guildId().equals(guildId)) {
            return DeclareResult.error(DeclareStatus.NOT_AUTHORIZED,
                    "only the declaring guild may cancel");
        }
        Optional<Guild> attacker = governance.source().guild(guildId);
        if (attacker.isEmpty() || !attacker.get().government().holderIds().contains(authorityId)) {
            return DeclareResult.error(DeclareStatus.NOT_AUTHORIZED,
                    "you need a seat in your guild's government to cancel");
        }
        Declaration previous = entry.declaration;
        entry.declaration = null;
        dirty = true;
        if (!persistSync()) {
            entry.declaration = previous; // roll back so the race stays locked
            dirty = true;
            return DeclareResult.error(DeclareStatus.STORAGE_ERROR,
                    "could not persist the cancellation — please retry");
        }
        return DeclareResult.ok(DeclareStatus.CANCELLED, "declaration cancelled; the race continues");
    }

    // ── Flips, recovery, persistence ──────────────────────────────────────

    /** Apply due flips; returns the completed takeovers for broadcasting. */
    public synchronized List<TerritoryFlip> tickFlips(long nowEpochMs) {
        if (unusable()) {
            return List.of();
        }
        List<TerritoryFlip> flipped = new ArrayList<>();
        for (TerritoryEntry entry : new ArrayList<>(state.entries.values())) {
            if (entry.pendingFlip != null && entry.pendingFlip.flipAtEpochMs() <= nowEpochMs) {
                applyJournal(entry, nowEpochMs, flipped);
            } else if (entry.declaration != null && entry.declaration.flipAtEpochMs() <= nowEpochMs) {
                flipFromDeclaration(entry, nowEpochMs, flipped);
            }
        }
        return List.copyOf(flipped);
    }

    /** Load-time recovery (spec §6): journal, overdue declarations, owner mismatches. */
    public synchronized List<TerritoryFlip> recover(long nowEpochMs) {
        InfluenceState loaded;
        try {
            loaded = store.load();
        } catch (IOException e) {
            state.entries.clear();
            dirty = false;
            loadFailed = true;
            log.log(Level.SEVERE,
                    "Failed to load influence state from PostgreSQL; influence subsystem disabled", e);
            return List.of();
        }
        state.entries.clear();
        state.entries.putAll(loaded.entries);

        List<TerritoryFlip> flipped = new ArrayList<>();
        for (Map.Entry<String, TerritoryEntry> e : new ArrayList<>(state.entries.entrySet())) {
            String territoryId = e.getKey();
            TerritoryEntry entry = e.getValue();
            Optional<Territory> t = territories().get(territoryId);
            if (t.isEmpty()) {
                state.entries.remove(territoryId);
                dirty = true;
                log.warning("Dropped influence state for missing territory " + territoryId);
                continue;
            }
            if (entry.pendingFlip != null) {
                applyJournal(entry, nowEpochMs, flipped);
                continue;
            }
            if (entry.declaration != null && entry.declaration.flipAtEpochMs() <= nowEpochMs) {
                flipFromDeclaration(entry, nowEpochMs, flipped);
                continue;
            }
            String currentOwner = t.get().governedByGuildId().orElse(null);
            if (!Objects.equals(currentOwner, entry.ownerGuildId)) {
                entry.bars.clear();
                entry.declaration = null;
                entry.ownerGuildId = currentOwner;
                dirty = true;
                log.info("Influence state reset for " + territoryId + ": owner changed to " + currentOwner);
            }
        }
        return List.copyOf(flipped);
    }

    /** Declaration reached flip time: journal the flip, then apply it (spec §6). */
    private void flipFromDeclaration(TerritoryEntry entry, long nowEpochMs, List<TerritoryFlip> flipped) {
        String territoryId = findTerritoryId(entry);
        Territory t = territories().get(territoryId).orElse(null);
        String currentOwner = t == null ? null : t.governedByGuildId().orElse(null);
        if (t == null || !Objects.equals(currentOwner, entry.ownerGuildId)
                || !canContest(entry.ownerGuildId, entry.declaration.guildId())) {
            log.warning("Declaration on " + territoryId + " invalidated — cancelling without flip");
            entry.declaration = null;
            dirty = true;
            persistSync();
            return;
        }
        // Step 1 of the journal: write the marker BEFORE mutating the race
        // state, so a crash can never lose the takeover.
        PendingFlip marker = new PendingFlip(territoryId, entry.ownerGuildId,
                entry.declaration.guildId(), entry.declaration.flipAtEpochMs(),
                nowEpochMs + config.postFlipCooldownEpochMs());
        entry.pendingFlip = marker;
        dirty = true;
        if (!persistSync()) {
            entry.pendingFlip = null; // roll back — declaration + bars untouched
            dirty = true;
            return;
        }
        entry.declaration = null;
        entry.bars.clear();
        dirty = true;
        applyJournal(entry, nowEpochMs, flipped);
    }

    /** Journal marker recovery/apply (spec §6): owner pin check, revalidation, apply or void. */
    private void applyJournal(TerritoryEntry entry, long nowEpochMs, List<TerritoryFlip> flipped) {
        PendingFlip marker = entry.pendingFlip;
        if (marker == null) {
            return;
        }
        String territoryId = marker.territoryId();
        Territory t = territories().get(territoryId).orElse(null);
        String currentOwner = t == null ? null : t.governedByGuildId().orElse(null);
        if (t == null) {
            log.warning("Pending flip for missing territory " + territoryId + " — dropping marker");
            entry.pendingFlip = null;
            dirty = true;
            return;
        }
        boolean oldOwnerStillOwns = Objects.equals(currentOwner, marker.oldOwnerGuildId());
        boolean newOwnerAlreadyOwns = Objects.equals(currentOwner, marker.newOwnerGuildId());
        if (!oldOwnerStillOwns && !newOwnerAlreadyOwns) {
            // External rebind during the crash window: neither pre- nor post-flip
            // owner — the flip is void and must never overwrite the new owner.
            log.warning("Pending flip for " + territoryId + " voided: owner changed to " + currentOwner
                    + " before recovery");
            entry.pendingFlip = null;
            entry.cooldownUntilEpochMs = 0L;
            dirty = true;
            return;
        }
        if (marker.flipAtEpochMs() > nowEpochMs) {
            return; // not due yet; tickFlips will apply it
        }
        if (oldOwnerStillOwns && !canContest(marker.oldOwnerGuildId(), marker.newOwnerGuildId())) {
            log.warning("Pending flip for " + territoryId + " voided: takeover no longer eligible");
            entry.pendingFlip = null;
            entry.cooldownUntilEpochMs = 0L;
            dirty = true;
            return;
        }
        // oldOwnerStillOwns → step 2 (ownership) is still pending and
        // applyFlipCore performs it; newOwnerAlreadyOwns → step 2 already
        // succeeded and the takeover is committed — only finalization runs
        // (no eligibility re-check: the new owner is already in place).
        applyFlipCore(entry, marker, flipped);
    }

    /** Steps 2–3 of the flip journal: register new owner, persist, finalize state. */
    private void applyFlipCore(TerritoryEntry entry, PendingFlip marker, List<TerritoryFlip> flipped) {
        String territoryId = marker.territoryId();
        Territory t = territories().get(territoryId).orElse(null);
        if (t == null) {
            log.warning("Pending flip for missing territory " + territoryId + " — dropping marker");
            entry.pendingFlip = null;
            dirty = true;
            return;
        }
        String currentOwner = t.governedByGuildId().orElse(null);
        if (!Objects.equals(currentOwner, marker.newOwnerGuildId())) {
            try {
                territories().register(t.withGoverningGuild(marker.newOwnerGuildId()));
                persister.persist(territoryId, marker.newOwnerGuildId());
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to persist ownership change for " + territoryId
                        + " — retrying next tick (marker kept)", e);
                return;
            }
        }
        entry.ownerGuildId = marker.newOwnerGuildId();
        entry.cooldownUntilEpochMs = marker.cooldownUntilEpochMs();
        entry.declaration = null;
        entry.bars.clear();
        entry.pendingFlip = null;
        dirty = true;
        if (!persistSync()) {
            entry.pendingFlip = marker; // finalize failed — keep marker for retry
            dirty = true;
            return; // no broadcast until the finalize actually persisted
        }
        flipped.add(new TerritoryFlip(territoryId, marker.oldOwnerGuildId(), marker.newOwnerGuildId()));
    }

    private String findTerritoryId(TerritoryEntry entry) {
        for (Map.Entry<String, TerritoryEntry> e : state.entries.entrySet()) {
            if (e.getValue() == entry) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("entry not registered");
    }

    /** Synchronous atomic transition write; false when the write failed. */
    private boolean persistSync() {
        try {
            store.save(state);
            dirty = false;
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to persist influence state transition", e);
            return false;
        }
    }

    /** Batched flush of dirty bar mutations (spec §6). */
    public synchronized void flush() throws IOException {
        if (unusable() || !dirty) {
            return;
        }
        store.save(state);
        dirty = false;
    }

    // ── Admin overrides ───────────────────────────────────────────────────

    /** Admin: set a guild's bar on a territory (clamped to [0, cap]). */
    public synchronized boolean adminSet(String territoryId, String guildId, double value, long nowEpochMs) {
        if (unusable()) {
            return false;
        }
        if (territoryId == null || guildId == null || territoryId.isBlank() || guildId.isBlank()) {
            return false;
        }
        TerritoryEntry entry = syncedEntry(territoryId, nowEpochMs);
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

    /** Admin: drop all influence state for a territory (persisted on next flush). */
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

    /** Configured influence cap (public for display). */
    public double cap() {
        return config.cap();
    }
}

package org.aincraft.guilds.territory.upkeep;

import org.aincraft.guilds.territory.economy.EconomyBridge;
import org.aincraft.guilds.territory.economy.ExpenseOutcome;
import org.aincraft.guilds.territory.economy.ExpenseReport;
import org.aincraft.guilds.territory.economy.ExpenseKind;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * Deterministic recurring upkeep state machine. A tick attempts at most one
 * period per territory and writes the complete snapshot before returning.
 */
public final class UpkeepEngine {
    private final TerritoryRegistry territories;
    private final EconomyBridge economy;
    private final FacilityRegistry facilities;
    private final UpkeepConfig config;
    private final UpkeepStore store;
    private final ToIntFunction<String> developmentLevel;
    private final Map<String, UpkeepState> states = new TreeMap<>();
    private boolean recovered;

    public UpkeepEngine(
            TerritoryRegistry territories,
            EconomyBridge economy,
            FacilityRegistry facilities,
            UpkeepConfig config,
            UpkeepStore store,
            ToIntFunction<String> developmentLevel
    ) {
        this.territories = Objects.requireNonNull(territories, "territories");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.facilities = Objects.requireNonNull(facilities, "facilities");
        this.config = Objects.requireNonNull(config, "config");
        this.store = Objects.requireNonNull(store, "store");
        this.developmentLevel = Objects.requireNonNull(developmentLevel, "developmentLevel");
    }

    /** Loads durable state and drops entries for missing or ungoverned territories. */
    public synchronized void recover(long nowEpochMs) throws IOException {
        requireEpoch(nowEpochMs);
        states.clear();
        Collection<UpkeepState> loaded = store.load();
        if (loaded != null) {
            for (UpkeepState state : loaded) {
                Optional<Territory> territory = territories.get(state.territoryId());
                if (territory.isPresent() && isGoverned(territory.get())) {
                    states.put(state.territoryId(), state);
                }
            }
        }
        recovered = true;
    }

    /**
     * Processes due periods and returns immutable post-transition snapshots.
     * The period key is derived from the due timestamp, so retries and
     * restarts remain idempotent through {@link EconomyBridge}.
     */
    public synchronized List<UpkeepState> tick(long nowEpochMs) throws IOException {
        requireEpoch(nowEpochMs);
        if (!recovered) {
            recover(nowEpochMs);
        }

        Map<String, UpkeepState> before = new TreeMap<>(states);
        List<UpkeepState> transitions = new ArrayList<>();
        boolean changed = false;

        for (String territoryId : new ArrayList<>(states.keySet())) {
            Optional<Territory> territory = territories.get(territoryId);
            if (territory.isEmpty() || !isGoverned(territory.get())) {
                states.remove(territoryId);
                changed = true;
            }
        }

        List<Territory> current = territories.list().stream()
                .filter(UpkeepEngine::isGoverned)
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
        for (Territory territory : current) {
            String territoryId = territory.id();
            UpkeepAssessment assessment = assess(territory);
            UpkeepState state = states.get(territoryId);
            if (state == null) {
                state = new UpkeepState(
                        territoryId, assessment.amount(), UpkeepStatus.CURRENT,
                        nowEpochMs, 0L, null, null);
                states.put(territoryId, state);
                transitions.add(state);
                changed = true;
            } else if (Double.compare(state.amount(), assessment.amount()) != 0) {
                state = new UpkeepState(
                        state.territoryId(), assessment.amount(), state.status(),
                        state.nextDueEpochMs(), state.graceDeadlineEpochMs(),
                        state.lastPeriodKey(), state.lastOutcome());
                states.put(territoryId, state);
                transitions.add(state);
                changed = true;
            }

            if (nowEpochMs < state.nextDueEpochMs()) {
                continue;
            }
            if (state.amount() <= 0.0) {
                UpkeepState advanced = new UpkeepState(
                        territoryId, state.amount(), UpkeepStatus.CURRENT,
                        safeAdd(nowEpochMs, config.intervalEpochMs()), 0L,
                        state.lastPeriodKey(), null);
                if (!advanced.equals(state)) {
                    states.put(territoryId, advanced);
                    transitions.add(advanced);
                    changed = true;
                }
                continue;
            }

            long dueEpochMs = state.nextDueEpochMs();
            String periodKey = periodKey(territoryId, dueEpochMs);
            ExpenseReport report = economy.chargeExpense(
                    territoryId, ExpenseKind.UPKEEP, state.amount(), periodKey);
            UpkeepState transitioned = transition(state, report.outcome(), periodKey, nowEpochMs);
            if (!transitioned.equals(state)) {
                states.put(territoryId, transitioned);
                transitions.add(transitioned);
                changed = true;
            }
        }

        if (changed) {
            try {
                store.save(List.copyOf(states.values()));
            } catch (IOException e) {
                states.clear();
                states.putAll(before);
                throw e;
            }
        }
        return List.copyOf(transitions);
    }

    public synchronized Optional<UpkeepState> state(String territoryId) {
        return Optional.ofNullable(states.get(territoryId));
    }

    public synchronized List<UpkeepState> all() {
        return List.copyOf(states.values());
    }

    public UpkeepAssessment assess(String territoryId) {
        Territory territory = territories.get(territoryId)
                .orElseThrow(() -> new IllegalArgumentException("unknown territory: " + territoryId));
        return assess(territory);
    }

    public static String periodKey(String territoryId, long dueEpochMs) {
        if (territoryId == null || territoryId.isBlank()) {
            throw new IllegalArgumentException("territoryId must not be blank");
        }
        if (dueEpochMs < 0L) {
            throw new IllegalArgumentException("dueEpochMs must be non-negative");
        }
        return "upkeep:" + territoryId.trim() + ":" + dueEpochMs;
    }

    private UpkeepAssessment assess(Territory territory) {
        int footprintUnits = footprintUnits(territory.boundary());
        int facilityCount = (int) facilities.list().stream()
                .filter(facility -> territory.id().equals(facility.territoryId()))
                .count();
        int level = Math.max(0, developmentLevel.applyAsInt(territory.id()));
        double amount = config.baseAmount()
                + config.chunkAmount() * footprintUnits
                + config.facilityAmount() * facilityCount
                + config.developmentLevelAmount() * level;
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalStateException("upkeep assessment overflow for " + territory.id());
        }
        return new UpkeepAssessment(territory.id(), amount, footprintUnits, facilityCount, level);
    }

    private UpkeepState transition(
            UpkeepState state,
            ExpenseOutcome outcome,
            String periodKey,
            long nowEpochMs
    ) {
        if (outcome == ExpenseOutcome.DEBITED || outcome == ExpenseOutcome.ALREADY_APPLIED) {
            return new UpkeepState(
                    state.territoryId(), state.amount(), UpkeepStatus.CURRENT,
                    safeAdd(nowEpochMs, config.intervalEpochMs()), 0L,
                    periodKey, outcome);
        }

        long deadline = state.graceDeadlineEpochMs() == 0L
                ? safeAdd(state.nextDueEpochMs(), config.graceEpochMs())
                : state.graceDeadlineEpochMs();
        if (nowEpochMs >= deadline) {
            return new UpkeepState(
                    state.territoryId(), state.amount(), UpkeepStatus.SUSPENDED,
                    safeAdd(nowEpochMs, config.intervalEpochMs()), deadline,
                    periodKey, outcome);
        }
        return new UpkeepState(
                state.territoryId(), state.amount(), UpkeepStatus.GRACE,
                deadline, deadline, periodKey, outcome);
    }

    private static boolean isGoverned(Territory territory) {
        return territory.governedByGuildId().isPresent();
    }

    private static int footprintUnits(Boundary boundary) {
        int chunkUnits = boundary.hasChunks() ? boundary.chunks().size() : 0;
        int polygonUnits = boundary.hasPolygon() ? polygonAreaUnits(boundary.polygon()) : 0;
        return Math.max(chunkUnits, polygonUnits);
    }

    private static int polygonAreaUnits(List<BlockPos> polygon) {
        long twiceArea = 0L;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            BlockPos current = polygon.get(i);
            BlockPos previous = polygon.get(j);
            twiceArea += (long) previous.x() * current.z() - (long) current.x() * previous.z();
        }
        double area = Math.abs(twiceArea) / 2.0;
        if (area >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(area);
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static void requireEpoch(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("epoch timestamp must be non-negative");
        }
    }
}

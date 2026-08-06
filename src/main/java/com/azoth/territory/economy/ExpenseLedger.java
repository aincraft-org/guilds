package com.azoth.territory.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Thread-safe idempotency journal with synchronous durable-snapshot hooks. */
public final class ExpenseLedger {
    private final Map<String, ExpenseEntry> entries = new LinkedHashMap<>();
    private volatile Consumer<Collection<ExpenseEntry>> snapshotSink;

    public ExpenseLedger() {
        this(ignored -> {
        });
    }

    public ExpenseLedger(Consumer<Collection<ExpenseEntry>> snapshotSink) {
        this.snapshotSink = Objects.requireNonNull(snapshotSink, "snapshotSink");
    }

    public synchronized Optional<ExpenseEntry> find(String idempotencyKey) {
        return Optional.ofNullable(entries.get(idempotencyKey));
    }
    /**
     * Claims an idempotency key without overwriting an existing journal entry.
     * The snapshot is persisted before the new claim becomes visible.
     */
    public synchronized Optional<ExpenseEntry> claim(ExpenseEntry entry) {
        Objects.requireNonNull(entry, "entry");
        ExpenseEntry existing = entries.get(entry.idempotencyKey());
        if (existing != null) {
            return Optional.of(existing);
        }
        Map<String, ExpenseEntry> candidate = new LinkedHashMap<>(entries);
        candidate.put(entry.idempotencyKey(), entry);
        persistCandidate(candidate.values());
        entries.clear();
        entries.putAll(candidate);
        return Optional.empty();
    }


    public synchronized void put(ExpenseEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Map<String, ExpenseEntry> candidate = new LinkedHashMap<>(entries);
        candidate.put(entry.idempotencyKey(), entry);
        persistCandidate(candidate.values());
        entries.clear();
        entries.putAll(candidate);
    }

    public synchronized void remove(String idempotencyKey) {
        if (!entries.containsKey(idempotencyKey)) {
            return;
        }
        Map<String, ExpenseEntry> candidate = new LinkedHashMap<>(entries);
        candidate.remove(idempotencyKey);
        persistCandidate(candidate.values());
        entries.clear();
        entries.putAll(candidate);
    }

    public synchronized void load(Collection<ExpenseEntry> loaded) {
        Objects.requireNonNull(loaded, "loaded");
        Map<String, ExpenseEntry> candidate = new LinkedHashMap<>();
        for (ExpenseEntry entry : loaded) {
            Objects.requireNonNull(entry, "entry");
            if (candidate.put(entry.idempotencyKey(), entry) != null) {
                throw new IllegalArgumentException("duplicate idempotency key: " + entry.idempotencyKey());
            }
        }
        entries.clear();
        entries.putAll(candidate);
    }

    public synchronized List<ExpenseEntry> entries() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    public void setSnapshotSink(Consumer<Collection<ExpenseEntry>> snapshotSink) {
        this.snapshotSink = Objects.requireNonNull(snapshotSink, "snapshotSink");
    }

    private void persistCandidate(Collection<ExpenseEntry> candidate) {
        snapshotSink.accept(List.copyOf(candidate));
    }
}

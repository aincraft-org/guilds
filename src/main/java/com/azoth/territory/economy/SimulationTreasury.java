package com.azoth.territory.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Non-monetary, in-memory treasury ledger for development and tests. Copy-on-write:
 * every credit/debit returns a new instance and leaves the original untouched.
 */
public final class SimulationTreasury {
    private final Map<String, Double> balances;

    public SimulationTreasury() {
        this.balances = Map.of();
    }

    private SimulationTreasury(Map<String, Double> balances) {
        this.balances = Collections.unmodifiableMap(balances);
    }

    public Map<String, Double> balances() {
        return balances;
    }

    public double balanceOf(String territoryId) {
        if (territoryId == null) {
            return 0.0;
        }
        return balances.getOrDefault(territoryId.trim(), 0.0);
    }

    public SimulationTreasury credit(String territoryId, double amount) {
        requirePositive(amount);
        return update(territoryId, get(territoryId) + amount);
    }

    public SimulationTreasury debit(String territoryId, double amount) {
        requirePositive(amount);
        double current = get(territoryId);
        if (amount > current) {
            return this;
        }
        return update(territoryId, current - amount);
    }

    private double get(String territoryId) {
        return balances.getOrDefault(Objects.requireNonNull(territoryId, "territoryId").trim(), 0.0);
    }

    private SimulationTreasury update(String territoryId, double value) {
        String id = Objects.requireNonNull(territoryId, "territoryId").trim();
        Map<String, Double> next = new LinkedHashMap<>(balances);
        next.put(id, value);
        return new SimulationTreasury(next);
    }

    private static void requirePositive(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive and finite, got " + amount);
        }
    }
}

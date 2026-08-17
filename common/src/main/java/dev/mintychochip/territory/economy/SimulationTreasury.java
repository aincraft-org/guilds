package dev.mintychochip.territory.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Non-monetary, in-memory treasury ledger for development and tests. Copy-on-write:
 * every credit/debit returns a new instance and leaves the original untouched.
 */
public final class SimulationTreasury implements PaymentRail {
    private final Map<String, Double> balances;
    private volatile SimulationTreasury state;

    public SimulationTreasury() {
        this.balances = Map.of();
        this.state = this;
    }

    private SimulationTreasury(Map<String, Double> balances) {
        this.balances = Collections.unmodifiableMap(balances);
        this.state = this;
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
    @Override
    public synchronized SettlementResult settle(java.util.UUID payerId, String territoryId, double amount) {
        if (payerId == null || territoryId == null || territoryId.isBlank()) {
            return new SettlementResult(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
        }
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return new SettlementResult(PaymentRail.SettlementStatus.INSUFFICIENT_FUNDS);
        }
        state = state.credit(territoryId, amount);
        return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
    }
    @Override
    public synchronized TreasuryDebitResult debitTreasury(String territoryId, double amount) {
        if (territoryId == null || territoryId.isBlank()) {
            return new TreasuryDebitResult(TreasuryDebitStatus.PROVIDER_UNAVAILABLE);
        }
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return new TreasuryDebitResult(TreasuryDebitStatus.INVALID_AMOUNT);
        }
        if (amount > state.balanceOf(territoryId)) {
            return new TreasuryDebitResult(TreasuryDebitStatus.INSUFFICIENT_FUNDS);
        }
        state = state.debit(territoryId, amount);
        return new TreasuryDebitResult(TreasuryDebitStatus.DEBITED);
    }

    @Override
    public boolean available() {
        return true;
    }

    /** Balance of the active ledger after payment-rail settlements. */
    public double activeBalanceOf(String territoryId) {
        return state.balanceOf(territoryId);
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

package com.azoth.territory.economy;

import com.azoth.territory.decree.DecreeEffectsInterpreter;
import com.azoth.territory.decree.GoodsCatalog;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public transaction API: other plugins report sales here; Azoth applies PASSED
 * policy tax rates and settles through the active payment rail.
 *
 * <p>Pure domain code: no Bukkit or Vault dependencies.</p>
 */
public class EconomyBridge {

    public record UnresolvedTransaction(
            String territoryId,
            UUID payerUuid,
            double amount,
            long timestampEpochMs,
            String reason
    ) {
    }

    private final TerritoryRegistry territories;
    private final GovernanceRegistry governance;
    private final GoodsCatalog goods;
    private final PaymentRail rail;
    private final boolean simulationMode;
    private final List<UnresolvedTransaction> unresolved = new CopyOnWriteArrayList<>();

    public EconomyBridge(
            TerritoryRegistry territories,
            GovernanceRegistry governance,
            GoodsCatalog goods,
            PaymentRail rail,
            boolean simulationMode
    ) {
        this.territories = Objects.requireNonNull(territories, "territories");
        this.governance = Objects.requireNonNull(governance, "governance");
        this.goods = Objects.requireNonNull(goods, "goods");
        this.rail = Objects.requireNonNull(rail, "rail");
        this.simulationMode = simulationMode;
    }

    public TaxReport reportSale(
            UUID payerId,
            String worldId,
            int blockX,
            int blockZ,
            String goodId,
            double grossAmount
    ) {
        if (payerId == null) {
            return report(TaxOutcome.PAYER_UNAVAILABLE, null, null, 0.0, 0.0);
        }
        if (!Double.isFinite(grossAmount) || grossAmount <= 0) {
            return report(TaxOutcome.INVALID_AMOUNT, null, null, 0.0, 0.0);
        }
        if (worldId == null) {
            return report(TaxOutcome.NO_TERRITORY, null, null, 0.0, 0.0);
        }

        var good = goods.findById(goodId);
        if (good.isEmpty()) {
            return report(TaxOutcome.UNKNOWN_GOOD, null, goodId, 0.0, 0.0);
        }

        LookupResult hit = territories.resolve(worldId, blockX, blockZ);
        if (!hit.isContained()) {
            return report(TaxOutcome.NO_TERRITORY, null, good.get().id(), 0.0, 0.0);
        }
        String territoryId = hit.territoryId().orElse(null);
        if (territoryId == null) {
            return report(TaxOutcome.NO_TERRITORY, null, good.get().id(), 0.0, 0.0);
        }

        var body = governance.resolveForTerritory(territoryId);
        if (!body.hasAssignedGovernment()) {
            return report(TaxOutcome.NO_GOVERNMENT, territoryId, good.get().id(), 0.0, 0.0);
        }
        var territory = territories.get(territoryId).orElse(null);
        if (territory == null) {
            return report(TaxOutcome.NO_TERRITORY, null, good.get().id(), 0.0, 0.0);
        }

        Map<String, Double> rates = DecreeEffectsInterpreter.taxRatesFromPolicies(territory.policies());
        Double rate = rates.get(good.get().id());
        if (rate == null) {
            return report(TaxOutcome.NO_TAX, territoryId, good.get().id(), 0.0, 0.0);
        }

        double taxAmount = TaxCalculator.tax(grossAmount, rate);
        if (simulationMode) {
            return report(TaxOutcome.SIMULATED_TAXED, territoryId, good.get().id(), rate, taxAmount);
        }
        if (!rail.available()) {
            return report(TaxOutcome.VAULT_UNAVAILABLE, territoryId, good.get().id(), rate, taxAmount);
        }

        SettlementResult result = rail.settle(payerId, territoryId, taxAmount);
        return mapSettlement(result, territoryId, good.get().id(), rate, taxAmount, payerId);
    }

    private TaxReport mapSettlement(
            SettlementResult result,
            String territoryId,
            String goodId,
            double rate,
            double taxAmount,
            UUID payerId
    ) {
        if (result == null) {
            return report(TaxOutcome.VAULT_UNAVAILABLE, territoryId, goodId, rate, 0.0);
        }
        return switch (result.status()) {
            case SETTLED -> report(TaxOutcome.TAXED, territoryId, goodId, rate, taxAmount);
            case INSUFFICIENT_FUNDS -> report(TaxOutcome.INSUFFICIENT_FUNDS, territoryId, goodId, rate, 0.0);
            case PAYER_UNAVAILABLE -> report(TaxOutcome.PAYER_UNAVAILABLE, territoryId, goodId, rate, 0.0);
            case VAULT_UNAVAILABLE -> report(TaxOutcome.VAULT_UNAVAILABLE, territoryId, goodId, rate, 0.0);
            case COMPENSATED_FAILURE -> report(TaxOutcome.SETTLEMENT_FAILED, territoryId, goodId, rate, 0.0);
            case RECONCILIATION_REQUIRED -> {
                unresolved.add(new UnresolvedTransaction(
                        territoryId, payerId, taxAmount, System.currentTimeMillis(), "refund failed after charge"));
                yield report(TaxOutcome.SETTLEMENT_RECONCILIATION_REQUIRED, territoryId, goodId, rate, 0.0);
            }
        };
    }

    public List<UnresolvedTransaction> unresolvedTransactions() {
        return List.copyOf(unresolved);
    }

    private static TaxReport report(
            TaxOutcome outcome,
            String territoryId,
            String goodId,
            double rate,
            double amount
    ) {
        return new TaxReport(outcome, territoryId, goodId, rate, amount);
    }
}

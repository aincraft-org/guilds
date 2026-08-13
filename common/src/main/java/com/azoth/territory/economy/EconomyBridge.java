package com.azoth.territory.economy;

import com.azoth.territory.decree.DecreeEffectsInterpreter;
import com.azoth.territory.decree.GoodsCatalog;
import com.azoth.territory.model.LookupResult;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.azoth.territory.economy.AsyncTaxSettlement;
import com.azoth.territory.economy.AsyncSettlementResult;
import java.util.function.Consumer;
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
    private final ExpenseLedger expenses;
    private volatile AsyncTaxSettlement asyncSettlement;
    private final List<UnresolvedTransaction> unresolved = new CopyOnWriteArrayList<>();
    private volatile Consumer<List<UnresolvedTransaction>> unresolvedSink = ignored -> {
    };

    public EconomyBridge(
            TerritoryRegistry territories,
            GovernanceRegistry governance,
            GoodsCatalog goods,
            PaymentRail rail,
            boolean simulationMode
    ) {
        this(territories, governance, goods, rail, simulationMode, ignored -> {
        }, new ExpenseLedger());
    }

    public EconomyBridge(
            TerritoryRegistry territories,
            GovernanceRegistry governance,
            GoodsCatalog goods,
            PaymentRail rail,
            boolean simulationMode,
            Consumer<List<UnresolvedTransaction>> unresolvedSink
    ) {
        this(territories, governance, goods, rail, simulationMode, unresolvedSink, new ExpenseLedger());
    }

    public EconomyBridge(
            TerritoryRegistry territories,
            GovernanceRegistry governance,
            GoodsCatalog goods,
            PaymentRail rail,
            boolean simulationMode,
            ExpenseLedger expenses
    ) {
        this(territories, governance, goods, rail, simulationMode, ignored -> {
        }, expenses);
    }

    public EconomyBridge(
            TerritoryRegistry territories,
            GovernanceRegistry governance,
            GoodsCatalog goods,
            PaymentRail rail,
            boolean simulationMode,
            Consumer<List<UnresolvedTransaction>> unresolvedSink,
            ExpenseLedger expenses
    ) {
        this.territories = Objects.requireNonNull(territories, "territories");
        this.governance = Objects.requireNonNull(governance, "governance");
        this.goods = Objects.requireNonNull(goods, "goods");
        this.rail = Objects.requireNonNull(rail, "rail");
        this.simulationMode = simulationMode;
        this.expenses = Objects.requireNonNull(expenses, "expenses");
        this.unresolvedSink = Objects.requireNonNull(unresolvedSink, "unresolvedSink");
    }

    public void setAsyncSettlement(AsyncTaxSettlement settlement) {
        this.asyncSettlement = settlement;
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
    /**
     * Reports a craft with an integration-supplied total gross value.
     * Quantity is validated as metadata and never used to infer a price.
     */
    public CompletionStage<TaxReport> reportSaleAsync(
            UUID payerId, String worldId, int blockX, int blockZ, String goodId,
            double grossAmount, String eventKey, AsyncTaxSettlement settlement) {
        Objects.requireNonNull(settlement, "settlement");
        if (payerId == null) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.PAYER_UNAVAILABLE, null, null, 0.0, 0.0));
        if (!Double.isFinite(grossAmount) || grossAmount <= 0) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.INVALID_AMOUNT, null, null, 0.0, 0.0));
        if (worldId == null) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.NO_TERRITORY, null, null, 0.0, 0.0));
        var good = goods.findById(goodId);
        if (good.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.UNKNOWN_GOOD, null, goodId, 0.0, 0.0));
        LookupResult hit = territories.resolve(worldId, blockX, blockZ);
        if (!hit.isContained() || hit.territoryId().isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.NO_TERRITORY, null, good.get().id(), 0.0, 0.0));
        String territoryId = hit.territoryId().orElseThrow();
        var body = governance.resolveForTerritory(territoryId);
        if (!body.hasAssignedGovernment()) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.NO_GOVERNMENT, territoryId, good.get().id(), 0.0, 0.0));
        var territory = territories.get(territoryId).orElse(null);
        if (territory == null) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.NO_TERRITORY, null, good.get().id(), 0.0, 0.0));
        Double rate = DecreeEffectsInterpreter.taxRatesFromPolicies(territory.policies()).get(good.get().id());
        if (rate == null) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.NO_TAX, territoryId, good.get().id(), 0.0, 0.0));
        double taxAmount = TaxCalculator.tax(grossAmount, rate);
        String guildId = body.guildBody().map(com.azoth.territory.permission.GuildBody::id).orElse(null);
        if (guildId == null || guildId.isBlank()) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.NO_GOVERNMENT, territoryId, good.get().id(), rate, 0.0));
        String key = deterministicEventKey(eventKey, territoryId, payerId, good.get().id(), taxAmount);
        try {
            return settlement.settle(payerId, guildId, BigDecimal.valueOf(taxAmount), key)
                    .thenApply(result -> mapAsyncSettlement(result, territoryId, good.get().id(), rate, taxAmount, payerId));
        } catch (RuntimeException error) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    report(TaxOutcome.MINT_UNAVAILABLE, territoryId, good.get().id(), rate, 0.0));
        }
    }

    public CompletionStage<TaxReport> reportCraftAsync(
            UUID payerId, String worldId, int blockX, int blockZ, String outputGoodId,
            int outputQuantity, double grossValue, String eventKey, AsyncTaxSettlement settlement) {
        if (outputQuantity <= 0) return java.util.concurrent.CompletableFuture.completedFuture(
                report(TaxOutcome.INVALID_QUANTITY, null, outputGoodId, 0.0, 0.0));
        return reportSaleAsync(payerId, worldId, blockX, blockZ, outputGoodId, grossValue, eventKey, settlement);
    }

    private static String deterministicEventKey(String supplied, String territory, UUID payer, String good, double amount) {
        String seed = (supplied == null ? "" : supplied.trim()) + "|" + territory + "|" + payer + "|" + good + "|"
                + BigDecimal.valueOf(amount).setScale(8, RoundingMode.HALF_UP).toPlainString();
        return "tax-" + java.util.UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private TaxReport mapAsyncSettlement(AsyncSettlementResult result, String territoryId, String goodId,
                                         double rate, double amount, UUID payerId) {
        if (result == null) return report(TaxOutcome.MINT_UNAVAILABLE, territoryId, goodId, rate, 0.0);
        return switch (result.status()) {
            case COMMITTED -> report(TaxOutcome.TAXED, territoryId, goodId, rate, amount);
            case INSUFFICIENT_FUNDS -> report(TaxOutcome.INSUFFICIENT_FUNDS, territoryId, goodId, rate, 0.0);
            case UNAVAILABLE -> report(TaxOutcome.MINT_UNAVAILABLE, territoryId, goodId, rate, 0.0);
            case REJECTED -> report(TaxOutcome.MINT_REJECTED, territoryId, goodId, rate, 0.0);
            case RECONCILIATION_REQUIRED -> {
                unresolved.add(new UnresolvedTransaction(territoryId, payerId, amount, System.currentTimeMillis(),
                        result.diagnosticCode().orElse("Mint reconciliation required")));
                unresolvedSink.accept(List.copyOf(unresolved));
                yield report(TaxOutcome.MINT_RECONCILIATION_REQUIRED, territoryId, goodId, rate, 0.0);
            }
        };
    }

    public TaxReport reportCraft(
            UUID payerId,
            String worldId,
            int blockX,
            int blockZ,
            String outputGoodId,
            int outputQuantity,
            double grossValue
    ) {
        if (outputQuantity <= 0) {
            return report(TaxOutcome.INVALID_QUANTITY, null, outputGoodId, 0.0, 0.0);
        }
        return reportSale(payerId, worldId, blockX, blockZ, outputGoodId, grossValue);
    }

    /**
     * Charges an externally scheduled settlement expense against its treasury.
     * The idempotency key makes retries safe across restarts.
     */
    public ExpenseReport chargeExpense(
            String territoryId,
            ExpenseKind kind,
            double amount,
            String idempotencyKey
    ) {
        if (kind == null || idempotencyKey == null || idempotencyKey.isBlank()
                || !Double.isFinite(amount) || amount <= 0) {
            return expenseReport(ExpenseOutcome.INVALID_AMOUNT, territoryId, kind, amount, idempotencyKey);
        }
        if (territories.get(territoryId).isEmpty()) {
            return expenseReport(ExpenseOutcome.NO_TERRITORY, territoryId, kind, amount, idempotencyKey);
        }
        if (!governance.resolveForTerritory(territoryId).hasAssignedGovernment()) {
            return expenseReport(ExpenseOutcome.NO_GOVERNMENT, territoryId, kind, amount, idempotencyKey);
        }

        ExpenseEntry pending = new ExpenseEntry(
                idempotencyKey, territoryId, kind, amount,
                ExpenseJournalState.PENDING, ExpenseOutcome.RECONCILIATION_REQUIRED);
        try {
            var existing = expenses.claim(pending);
            if (existing.isPresent()) {
                if (existing.get().state() == ExpenseJournalState.DEBITED) {
                    return expenseReport(ExpenseOutcome.ALREADY_APPLIED, territoryId, kind, amount, idempotencyKey);
                }
                return expenseReport(
                        ExpenseOutcome.RECONCILIATION_REQUIRED, territoryId, kind, amount, idempotencyKey);
            }
        } catch (RuntimeException e) {
            return expenseReport(
                    ExpenseOutcome.RECONCILIATION_REQUIRED, territoryId, kind, amount, idempotencyKey);
        }

        TreasuryDebitResult debit;
        try {
            debit = rail.debitTreasury(territoryId, amount);
        } catch (RuntimeException e) {
            return expenseReport(
                    ExpenseOutcome.RECONCILIATION_REQUIRED, territoryId, kind, amount, idempotencyKey);
        }

        if (debit == null) {
            return removeFailedExpense(
                    ExpenseOutcome.VAULT_UNAVAILABLE, territoryId, kind, amount, idempotencyKey);
        }
        return switch (debit.status()) {
            case DEBITED -> {
                ExpenseEntry applied = new ExpenseEntry(
                        idempotencyKey, territoryId, kind, amount,
                        ExpenseJournalState.DEBITED, ExpenseOutcome.DEBITED);
                try {
                    expenses.put(applied);
                    yield expenseReport(ExpenseOutcome.DEBITED, territoryId, kind, amount, idempotencyKey);
                } catch (RuntimeException e) {
                    yield expenseReport(
                            ExpenseOutcome.RECONCILIATION_REQUIRED, territoryId, kind, amount, idempotencyKey);
                }
            }
            case INSUFFICIENT_FUNDS -> removeFailedExpense(
                    ExpenseOutcome.INSUFFICIENT_FUNDS, territoryId, kind, amount, idempotencyKey);
            case VAULT_UNAVAILABLE -> removeFailedExpense(
                    ExpenseOutcome.VAULT_UNAVAILABLE, territoryId, kind, amount, idempotencyKey);
            case INVALID_AMOUNT -> removeFailedExpense(
                    ExpenseOutcome.INVALID_AMOUNT, territoryId, kind, amount, idempotencyKey);
        };
    }

    private ExpenseReport removeFailedExpense(
            ExpenseOutcome outcome,
            String territoryId,
            ExpenseKind kind,
            double amount,
            String idempotencyKey
    ) {
        try {
            expenses.remove(idempotencyKey);
            return expenseReport(outcome, territoryId, kind, amount, idempotencyKey);
        } catch (RuntimeException e) {
            return expenseReport(
                    ExpenseOutcome.RECONCILIATION_REQUIRED, territoryId, kind, amount, idempotencyKey);
        }
    }

    private static ExpenseReport expenseReport(
            ExpenseOutcome outcome,
            String territoryId,
            ExpenseKind kind,
            double amount,
            String idempotencyKey
    ) {
        return new ExpenseReport(outcome, territoryId, kind, amount, idempotencyKey);
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
                UnresolvedTransaction entry = new UnresolvedTransaction(
                        territoryId, payerId, taxAmount, System.currentTimeMillis(), "refund failed after charge");
                unresolved.add(entry);
                unresolvedSink.accept(List.copyOf(unresolved));
                yield report(TaxOutcome.SETTLEMENT_RECONCILIATION_REQUIRED, territoryId, goodId, rate, 0.0);
            }
        };
    }

    public List<UnresolvedTransaction> unresolvedTransactions() {
        return List.copyOf(unresolved);
    }

    public void loadUnresolvedTransactions(Collection<UnresolvedTransaction> entries) {
        unresolved.clear();
        if (entries != null) {
            unresolved.addAll(entries);
        }
        unresolvedSink.accept(List.copyOf(unresolved));
    }

    public void setUnresolvedTransactionSink(Consumer<List<UnresolvedTransaction>> sink) {
        unresolvedSink = Objects.requireNonNull(sink, "sink");
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

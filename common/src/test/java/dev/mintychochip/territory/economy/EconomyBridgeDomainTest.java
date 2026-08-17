package dev.mintychochip.territory.economy;

import dev.mintychochip.territory.decree.DecreeEffects;
import dev.mintychochip.territory.decree.GoodsCatalog;
import dev.mintychochip.territory.decree.TaxEffect;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure-domain EconomyBridge: territory resolution, rate application, outcome mapping. */
class EconomyBridgeDomainTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final UUID PAYER = UUID.randomUUID();
    private static final String WORLD = "world";

    private static final Boundary SQUARE = Boundary.ofPolygon(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
    ));

    private static DecreeEffects carrotTax() {
        return DecreeEffects.ofTax(new TaxEffect(List.of("carrot"), 15.0));
    }

    private static Territory taxedTerritory() {
        Territory t = new Territory("t1", "T", WORLD, SQUARE).withGovernment(Government.monarchy("king:arthur"));
        t = t.proposePolicy("tax", "Tax", "B", "king:arthur", NOW, carrotTax());
        return t.decreePolicy("tax", "king:arthur", true, NOW + 1);
    }

    private static EconomyBridge bridge(PaymentRail rail, boolean simulation) {
        TerritoryRegistry reg = new TerritoryRegistry();
        reg.register(taxedTerritory());
        return new EconomyBridge(reg, new GovernanceRegistry(reg), GoodsCatalog.defaultCatalog(), rail, simulation);
    }

    private static final class RecordingRail implements PaymentRail {
        boolean available = true;
        SettlementResult result = new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        int settleCalls;

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            settleCalls++;
            return result;
        }
        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            return new TreasuryDebitResult(TreasuryDebitStatus.DEBITED);
        }

        @Override
        public boolean available() {
            return available;
        }
    }

    @Test
    void taxedSaleSettlesAndReports() {
        RecordingRail rail = new RecordingRail();
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.TAXED, r.outcome());
        assertEquals("t1", r.territoryId());
        assertEquals("carrot", r.goodId());
        assertEquals(15.0, r.ratePercent(), 1e-9);
        assertEquals(15.0, r.taxAmount(), 1e-9);
        assertEquals(1, rail.settleCalls);
    }

    @Test
    void outsideAnyTerritoryIsNoTerritory() {
        RecordingRail rail = new RecordingRail();
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 500, 500, "carrot", 100.0);
        assertEquals(TaxOutcome.NO_TERRITORY, r.outcome());
        assertEquals(0.0, r.taxAmount(), 1e-9);
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void anarchyTerritoryIsNoGovernment() {
        TerritoryRegistry reg = new TerritoryRegistry();
        reg.register(new Territory("t2", "T2", WORLD, SQUARE));
        EconomyBridge b = new EconomyBridge(reg, new GovernanceRegistry(reg), GoodsCatalog.defaultCatalog(),
                new RecordingRail(), false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.NO_GOVERNMENT, r.outcome());
        assertEquals("t2", r.territoryId());
    }

    @Test
    void unknownGoodIsUnknownGood() {
        EconomyBridge b = bridge(new RecordingRail(), false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "dragon_egg", 100.0);
        assertEquals(TaxOutcome.UNKNOWN_GOOD, r.outcome());
    }

    @Test
    void untaxedGoodIsNoTax() {
        EconomyBridge b = bridge(new RecordingRail(), false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "potato", 100.0);
        assertEquals(TaxOutcome.NO_TAX, r.outcome());
        assertEquals(0.0, r.taxAmount(), 1e-9);
        assertEquals("t1", r.territoryId());
    }

    @Test
    void invalidAmountIsRejectedBeforeSettlement() {
        RecordingRail rail = new RecordingRail();
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", -5.0);
        assertEquals(TaxOutcome.INVALID_AMOUNT, r.outcome());
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void railUnavailableIsProviderUnavailable() {
        RecordingRail rail = new RecordingRail();
        rail.available = false;
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.PROVIDER_UNAVAILABLE, r.outcome());
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void insufficientFundsMapsThrough() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.INSUFFICIENT_FUNDS);
        EconomyBridge b = bridge(rail, false);
        assertEquals(TaxOutcome.INSUFFICIENT_FUNDS, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
    }

    @Test
    void payerUnavailableMapsThrough() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.PAYER_UNAVAILABLE);
        EconomyBridge b = bridge(rail, false);
        assertEquals(TaxOutcome.PAYER_UNAVAILABLE, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
    }

    @Test
    void providerUnavailableSettlementMapsThrough() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.PROVIDER_UNAVAILABLE);
        EconomyBridge b = bridge(rail, false);
        assertEquals(TaxOutcome.PROVIDER_UNAVAILABLE, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
    }

    @Test
    void compensatedFailureMapsThrough() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.COMPENSATED_FAILURE);
        EconomyBridge b = bridge(rail, false);
        assertEquals(TaxOutcome.SETTLEMENT_FAILED, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
    }

    @Test
    void reconciliationRequiredMapsThroughAndQueues() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.RECONCILIATION_REQUIRED);
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.SETTLEMENT_RECONCILIATION_REQUIRED, r.outcome());
        assertEquals(1, b.unresolvedTransactions().size());
        EconomyBridge.UnresolvedTransaction u = b.unresolvedTransactions().get(0);
        assertEquals("t1", u.territoryId());
        assertEquals(PAYER, u.payerUuid());
        assertEquals(15.0, u.amount(), 1e-9);
    }

    @Test
    void simulationModeReturnsSimulatedTaxed() {
        EconomyBridge b = bridge(new RecordingRail(), true);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.SIMULATED_TAXED, r.outcome());
        assertEquals(15.0, r.taxAmount(), 1e-9);
    }

    @Test
    void multipleReconciliationsAreNotDoubleCounted() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.RECONCILIATION_REQUIRED);
        EconomyBridge b = bridge(rail, false);
        b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        b.reportSale(PAYER, WORLD, 5, 5, "carrot", 50.0);
        assertEquals(2, b.unresolvedTransactions().size());
    }

    @Test
    void simulationTreasurySettlementCreditsActiveLedger() {
        SimulationTreasury treasury = new SimulationTreasury();
        SettlementResult result = treasury.settle(PAYER, "t1", 2.5);
        assertEquals(PaymentRail.SettlementStatus.SETTLED, result.status());
        assertEquals(2.5, treasury.activeBalanceOf("t1"), 1e-9);
    }

    @Test
    void unresolvedQueueLoadsAndNotifiesPersistenceSink() {
        RecordingRail rail = new RecordingRail();
        rail.result = new SettlementResult(PaymentRail.SettlementStatus.RECONCILIATION_REQUIRED);
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(taxedTerritory());
        List<List<EconomyBridge.UnresolvedTransaction>> snapshots = new java.util.ArrayList<>();
        EconomyBridge b = new EconomyBridge(
                registry,
                new GovernanceRegistry(registry),
                GoodsCatalog.defaultCatalog(),
                rail,
                false,
                snapshots::add);
        EconomyBridge.UnresolvedTransaction existing = new EconomyBridge.UnresolvedTransaction(
                "old", PAYER, 1.0, NOW, "previous");
        b.loadUnresolvedTransactions(List.of(existing));

        b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);

        assertEquals(List.of(existing), snapshots.get(0));
        assertEquals(2, snapshots.get(1).size());
        assertEquals(2, b.unresolvedTransactions().size());
    }
}

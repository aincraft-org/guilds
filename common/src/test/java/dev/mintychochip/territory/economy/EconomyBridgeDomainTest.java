package dev.mintychochip.territory.economy;

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

    private static Territory governedTerritory() {
        return new Territory("t1", "T", WORLD, SQUARE).withGovernment(Government.monarchy("king:arthur"));
    }

    private static EconomyBridge bridge(PaymentRail rail, boolean simulation) {
        TerritoryRegistry reg = new TerritoryRegistry();
        reg.register(governedTerritory());
        return new EconomyBridge(reg, new GovernanceRegistry(reg), GoodsCatalog.defaultCatalog(), rail, simulation);
    }

    private static final class RecordingRail implements PaymentRail {
        int settleCalls;

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            settleCalls++;
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }
        @Override
        public TreasuryDebitResult debitTreasury(String territoryId, double amount) {
            return new TreasuryDebitResult(TreasuryDebitStatus.DEBITED);
        }

        @Override
        public boolean available() {
            return true;
        }
    }

    @Test
    void governedSaleHasNoTaxWhileDecreesAreUnwired() {
        RecordingRail rail = new RecordingRail();
        EconomyBridge b = bridge(rail, false);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.NO_TAX, r.outcome());
        assertEquals("t1", r.territoryId());
        assertEquals("carrot", r.goodId());
        assertEquals(0.0, r.ratePercent(), 1e-9);
        assertEquals(0.0, r.taxAmount(), 1e-9);
        assertEquals(0, rail.settleCalls);
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
    void simulationModeStillHasNoTaxWhileDecreesAreUnwired() {
        EconomyBridge b = bridge(new RecordingRail(), true);
        TaxReport r = b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0);
        assertEquals(TaxOutcome.NO_TAX, r.outcome());
        assertEquals(0.0, r.taxAmount(), 1e-9);
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
        TerritoryRegistry registry = new TerritoryRegistry();
        registry.register(governedTerritory());
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
        assertEquals(TaxOutcome.NO_TAX, b.reportSale(PAYER, WORLD, 5, 5, "carrot", 100.0).outcome());
        assertEquals(1, b.unresolvedTransactions().size());
    }
}

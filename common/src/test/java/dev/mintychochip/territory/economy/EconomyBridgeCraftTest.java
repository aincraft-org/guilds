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

class EconomyBridgeCraftTest {

    private static final UUID PAYER = UUID.randomUUID();
    private static final Boundary SQUARE = Boundary.ofPolygon(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)));

    private static EconomyBridge bridge(RecordingRail rail) {
        Territory territory = new Territory("t1", "T", "world", SQUARE)
                .withGovernment(Government.monarchy("king"));
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(territory);
        return new EconomyBridge(
                territories,
                new GovernanceRegistry(territories),
                GoodsCatalog.defaultCatalog(),
                rail,
                false);
    }

    @Test
    void craftUsesExplicitGrossValueWithoutQuantityPriceInference() {
        RecordingRail rail = new RecordingRail();

        TaxReport report = bridge(rail).reportCraft(PAYER, "world", 5, 5, "carrot", 4, 200.0);

        assertEquals(TaxOutcome.NO_TAX, report.outcome());
        assertEquals(0.0, report.taxAmount(), 1e-9);
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void nonPositiveCraftQuantityIsRejectedBeforeSettlement() {
        RecordingRail rail = new RecordingRail();

        TaxReport report = bridge(rail).reportCraft(PAYER, "world", 5, 5, "carrot", 0, 200.0);

        assertEquals(TaxOutcome.INVALID_QUANTITY, report.outcome());
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void invalidCraftGrossValueUsesInvalidAmount() {
        RecordingRail rail = new RecordingRail();

        TaxReport report = bridge(rail).reportCraft(PAYER, "world", 5, 5, "carrot", 1, -1.0);

        assertEquals(TaxOutcome.INVALID_AMOUNT, report.outcome());
        assertEquals(0, rail.settleCalls);
    }

    @Test
    void missingCraftPayerIsUnavailable() {
        RecordingRail rail = new RecordingRail();

        TaxReport report = bridge(rail).reportCraft(null, "world", 5, 5, "carrot", 1, 200.0);

        assertEquals(TaxOutcome.PAYER_UNAVAILABLE, report.outcome());
        assertEquals(0, rail.settleCalls);
    }

    private static final class RecordingRail implements PaymentRail {
        private int settleCalls;

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
}

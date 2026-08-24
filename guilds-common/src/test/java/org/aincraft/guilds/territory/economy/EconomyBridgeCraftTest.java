package org.aincraft.guilds.territory.economy;

import org.aincraft.guilds.territory.decree.DecreeEffects;
import org.aincraft.guilds.territory.decree.GoodsCatalog;
import org.aincraft.guilds.territory.decree.TaxEffect;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyBridgeCraftTest {

    private static final UUID PAYER = UUID.randomUUID();
    private static final Boundary SQUARE = Boundary.ofPolygon(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)));

    private static EconomyBridge bridge(RecordingRail rail) {
        long now = 1_700_000_000_000L;
        Territory territory = new Territory("t1", "T", "world", SQUARE)
                .withGovernment(Government.monarchy("king"))
                .proposePolicy("tax", "Tax", "B", "king", now,
                        DecreeEffects.ofTax(new TaxEffect(List.of("carrot"), 15.0)))
                .decreePolicy("tax", "king", true, now + 1);
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

        assertEquals(TaxOutcome.TAXED, report.outcome());
        assertEquals(30.0, report.taxAmount(), 1e-9);
        assertEquals(30.0, rail.lastAmount, 1e-9);
        assertEquals(1, rail.settleCalls);
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
        private double lastAmount;

        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            settleCalls++;
            lastAmount = amount;
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

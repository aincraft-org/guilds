package com.azoth.territory.economy;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** BukkitEconomyBridge delegates from OfflinePlayer to the domain UUID API. */
class BukkitEconomyBridgeTest {

    private static final class CapturingBridge extends EconomyBridge {
        UUID lastPayer;

        CapturingBridge() {
            super(new com.azoth.territory.registry.TerritoryRegistry(),
                    new com.azoth.territory.permission.GovernanceRegistry(
                            new com.azoth.territory.registry.TerritoryRegistry()),
                    com.azoth.territory.decree.GoodsCatalog.defaultCatalog(),
                    new RecordingRail());
        }

        @Override
        public TaxReport reportSale(UUID payerId, String worldId, int blockX, int blockZ,
                                    String goodId, double grossAmount) {
            lastPayer = payerId;
            return new TaxReport(TaxOutcome.NO_TAX, null, goodId, 0.0, 0.0);
        }
    }

    private static final class RecordingRail implements PaymentRail {
        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
            return new SettlementResult(PaymentRail.SettlementStatus.SETTLED);
        }

        @Override
        public boolean available() {
            return true;
        }
    }

    @Test
    void delegatesPayerUuid() {
        UUID id = UUID.randomUUID();
        OfflinePlayer op = mock(OfflinePlayer.class);
        when(op.getUniqueId()).thenReturn(id);
        CapturingBridge db = new CapturingBridge();
        BukkitEconomyBridge b = new BukkitEconomyBridge(db);
        b.reportSale(op, "world", 1, 2, "carrot", 10.0);
        assertEquals(id, db.lastPayer);
    }

    @Test
    void nullPayerDelegatesNullUuid() {
        CapturingBridge db = new CapturingBridge();
        BukkitEconomyBridge b = new BukkitEconomyBridge(db);
        assertEquals(TaxOutcome.NO_TAX, b.reportSale(null, "world", 1, 2, "carrot", 10.0).outcome());
        assertEquals(null, db.lastPayer);
    }
}

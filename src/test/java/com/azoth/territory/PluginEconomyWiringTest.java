package com.azoth.territory;

import com.azoth.territory.decree.GoodsCatalog;
import com.azoth.territory.economy.BukkitEconomyBridge;
import com.azoth.territory.economy.EconomyBridge;
import com.azoth.territory.economy.PaymentRail;
import com.azoth.territory.economy.SettlementResult;
import com.azoth.territory.economy.TreasuryDebitResult;
import com.azoth.territory.economy.TreasuryDebitStatus;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.model.Territory;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.FacilityRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** The packaged plugin.yml soft-depends on Vault. */
class PluginEconomyWiringTest {
    @Test
    void pluginMetadataDeclaresSoftDependVault() throws Exception {
        var stream = getClass().getResourceAsStream("/plugin.yml");
        assertNotNull(stream);
        String yml;
        try (stream) {
            yml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yml.contains("softdepend") && yml.contains("Vault"),
                "plugin.yml must soft-depend on Vault");
    }
    @Test
    void bukkitFacadeResolvesPersistedFacilityLocations() {
        TerritoryRegistry territories = new TerritoryRegistry();
        territories.register(new Territory(
                "t1",
                "T",
                "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0),
                        new BlockPos(10, 0),
                        new BlockPos(10, 10),
                        new BlockPos(0, 10)))));
        FacilityRegistry facilities = new FacilityRegistry(territories);
        SettlementFacility facility = new SettlementFacility(
                "market", "Market", "t1", FacilityType.TRADING_POST, "world", 5, 64, 5);
        facilities.register(facility);
        EconomyBridge economy = new EconomyBridge(
                territories,
                new GovernanceRegistry(territories),
                GoodsCatalog.defaultCatalog(),
                new TestRail(),
                false);

        BukkitEconomyBridge bridge = new BukkitEconomyBridge(economy, facilities);

        assertEquals(java.util.Optional.of(facility), bridge.resolveFacility("world", 5, 64, 5));
    }

    private static final class TestRail implements PaymentRail {
        @Override
        public SettlementResult settle(UUID payerId, String territoryId, double amount) {
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

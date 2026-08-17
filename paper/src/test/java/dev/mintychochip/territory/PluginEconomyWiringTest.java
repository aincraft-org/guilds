package dev.mintychochip.territory;

import dev.mintychochip.territory.decree.GoodsCatalog;
import dev.mintychochip.territory.economy.BukkitEconomyBridge;
import dev.mintychochip.territory.economy.EconomyBridge;
import dev.mintychochip.territory.economy.PaymentRail;
import dev.mintychochip.territory.economy.SettlementResult;
import dev.mintychochip.territory.economy.TreasuryDebitResult;
import dev.mintychochip.territory.economy.TreasuryDebitStatus;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.FacilityType;
import dev.mintychochip.territory.model.SettlementFacility;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.territory.registry.FacilityRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** The packaged plugin.yml must not depend on Vault. */
class PluginEconomyWiringTest {
    @Test
    void pluginMetadataDoesNotDeclareVault() throws Exception {
        var stream = getClass().getResourceAsStream("/plugin.yml");
        assertNotNull(stream);
        String yml;
        try (stream) {
            yml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(!yml.contains("Vault"), "plugin.yml must not depend on Vault");
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


    @Test
    void pluginWiresDurableExpenseLedgerAcrossLifecycle() throws Exception {
        String source = Files.readString(findMainSource());

        assertTrue(source.contains("new PostgresExpenseStore(database)"),
                "plugin must construct the PostgreSQL expense store");
        assertTrue(source.contains("new ExpenseLedger"),
                "plugin must construct an expense ledger");
        assertTrue(source.contains("expenseStore.load()"),
                "plugin must load expense entries before economy use");
        assertTrue(source.contains("expenseStore.save(expenseLedger.entries())"),
                "plugin must flush expense entries on shutdown");
    }

    private static Path findMainSource() throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd.resolve("src/main/java/dev/mintychochip/territory/AzothTerritoryPlugin.java");
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        Path p = cwd;
        for (int i = 0; i < 4; i++) {
            Path tryPath = p.resolve("src/main/java/dev/mintychochip/territory/AzothTerritoryPlugin.java");
            if (Files.isRegularFile(tryPath)) {
                return tryPath;
            }
            p = p.getParent();
            if (p == null) {
                break;
            }
        }
        throw new IllegalStateException("Could not locate AzothTerritoryPlugin.java from " + cwd);
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

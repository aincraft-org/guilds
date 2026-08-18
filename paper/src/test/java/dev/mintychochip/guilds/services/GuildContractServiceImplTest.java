package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.GuildsServiceTestFixture;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-database tests for guild contracts: escrow debit at post time, escrow
 * release on fulfillment, upgrade-progress credit, cancellation refund, and
 * the self-fulfill / non-open guards.
 */
class GuildContractServiceImplTest {

    /** The temp dir. */
    @TempDir
    Path tempDir;

    /** The services. */
    private GuildsServiceTestFixture.Services services;
    /** The contracts. */
    private GuildContractService contracts;
    /** The guilds. */
    private GuildService guilds;
    /** The residents. */
    private ResidentService residents;

    /** The alpha id. */
    private String alphaId;
    /** The beta id. */
    private String betaId;
    /** The mayor. */
    private UUID mayor;

    /** Sets the up. */
    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        contracts = services.guildContractService();
        guilds = services.guildService();
        residents = services.residentService();

        // The integration tests share one persistent database with no per-test
        // isolation, so clear any leftover rows (fixed guild/resident names) before
        // seeding, to make this class runnable both on a fresh DB and on a reused one.
        clearTestData();

        mayor = UUID.randomUUID();
        residents.createResident(mayor, "mayor");

        Guild alpha = guilds.createGuild("Alpha", mayor);
        Guild beta = guilds.createGuild("Beta", mayor);
        alphaId = alpha.getId();
        betaId = beta.getId();

        guilds.updateGuildBalance("Alpha", 1000.0);
        guilds.updateGuildBalance("Beta", 500.0);
    }

    /** Performs the clear test data operation. */
    private void clearTestData() {
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE guild_contracts, guild_resources, guild_blocks, guild_residents,
                        guild_level_benefits, broadcast_read_status, broadcast_messages,
                        resource_contributions, permissions, economy_transactions, guild_quests,
                        guild_specializations, guild_unlocked_nodes, alliance_ministers,
                        alliance_members, alliances, guilds, residents
                    CASCADE
                    """);
        } catch (Exception e) {
            // Best-effort cleanup; the test setup will surface any real problem.
        }
    }

    /** Performs the tear down operation. */
    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    /**
     * Performs the balance operation.
     * @param guildId the guild id
     * @return the result
     */
    private double balance(String guildId) {
        return guilds.getGuildById(guildId).map(Guild::getBalance).orElseThrow();
    }

    /**
     * Performs the upgrade progress operation.
     * @param guildId the guild id
     * @return the result
     * @throws Exception if an error occurs
     */
    private String upgradeProgress(String guildId) throws Exception {
        String sql = "SELECT upgrade_progress FROM guilds WHERE id = ?";
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString("upgrade_progress");
            }
        }
    }

    /** Creates a new contract debits escrow and persists. */
    @Test
    void createContract_debitsEscrowAndPersists() {
        GuildContractService.ContractResult result =
                contracts.createContract(alphaId, "DIAMOND", 10, 100.0);

        assertTrue(result.isSuccessful());
        assertEquals(GuildContractService.ContractStatus.CREATED, result.getStatus());
        assertEquals(900.0, balance(alphaId), "payment must be escrowed at post time");
        assertEquals(500.0, balance(betaId));

        Optional<GuildContract> stored = contracts.getContract(result.getContract().getId());
        assertTrue(stored.isPresent());
        assertEquals("DIAMOND", stored.get().getResourceType());
        assertEquals(10, stored.get().getAmount());
        assertEquals(100.0, stored.get().getPayment());
        assertTrue(stored.get().isOpen());
    }

    /** Creates a new contract insufficient funds fails. */
    @Test
    void createContract_insufficientFundsFails() {
        GuildContractService.ContractResult result =
                contracts.createContract(betaId, "DIAMOND", 10, 600.0);

        assertFalse(result.isSuccessful());
        assertEquals(GuildContractService.ContractStatus.INSUFFICIENT_FUNDS, result.getStatus());
        assertEquals(500.0, balance(betaId), "balance must be untouched on failure");
    }

    /** Creates a new contract invalid amount or payment fails. */
    @Test
    void createContract_invalidAmountOrPaymentFails() {
        assertEquals(GuildContractService.ContractStatus.INVALID_AMOUNT,
                contracts.createContract(alphaId, "DIAMOND", 0, 100.0).getStatus());
        assertEquals(GuildContractService.ContractStatus.INVALID_PAYMENT,
                contracts.createContract(alphaId, "DIAMOND", 10, 0.0).getStatus());
        assertEquals(GuildContractService.ContractStatus.GUILD_NOT_FOUND,
                contracts.createContract("missing-guild", "DIAMOND", 10, 100.0).getStatus());
    }

    /**
     * Performs the fulfill contract releases escrow and credits upgrade progress operation.
     * @throws Exception if an error occurs
     */
    @Test
    void fulfillContract_releasesEscrowAndCreditsUpgradeProgress() throws Exception {
        GuildContractService.ContractResult created =
                contracts.createContract(alphaId, "DIAMOND", 10, 100.0);

        GuildContractService.FulfillResult fulfilled =
                contracts.fulfillContract(created.getContract().getId(), betaId);

        assertTrue(fulfilled.isSuccessful());
        assertEquals(GuildContractService.FulfillStatus.FULFILLED, fulfilled.getStatus());
        assertEquals(100.0, fulfilled.getPaymentReleased());

        assertEquals(600.0, balance(betaId), "fulfiller must be paid from escrow");
        assertEquals(900.0, balance(alphaId), "contracting guild keeps its escrow debit");

        String progress = upgradeProgress(alphaId);
        assertTrue(progress.contains("\"DIAMOND\":10"), "resources must be credited to upgrade progress: " + progress);

        Optional<GuildContract> stored = contracts.getContract(created.getContract().getId());
        assertEquals(GuildContract.Status.FULFILLED, stored.get().getStatus());
    }

    /** Performs the fulfill contract guild cannot fulfill its own contract operation. */
    @Test
    void fulfillContract_guildCannotFulfillItsOwnContract() {
        GuildContractService.ContractResult created =
                contracts.createContract(alphaId, "DIAMOND", 10, 100.0);

        GuildContractService.FulfillResult fulfilled =
                contracts.fulfillContract(created.getContract().getId(), alphaId);

        assertFalse(fulfilled.isSuccessful());
        assertEquals(GuildContractService.FulfillStatus.SELF_FULFILL, fulfilled.getStatus());
        assertEquals(900.0, balance(alphaId), "no escrow release and no further debit");
    }

    /** Performs the fulfill contract already fulfilled rejected operation. */
    @Test
    void fulfillContract_alreadyFulfilledRejected() {
        GuildContractService.ContractResult created =
                contracts.createContract(alphaId, "DIAMOND", 10, 100.0);
        String id = created.getContract().getId();

        assertTrue(contracts.fulfillContract(id, betaId).isSuccessful());

        // A third guild cannot re-fulfill a fulfilled contract.
        Guild third = guilds.createGuild("Gamma", mayor);
        GuildContractService.FulfillResult second =
                contracts.fulfillContract(id, third.getId());

        assertFalse(second.isSuccessful());
        assertEquals(GuildContractService.FulfillStatus.NOT_OPEN, second.getStatus());
        assertEquals(600.0, balance(betaId), "payment must not be released twice");
    }

    /** Returns whether cel contract refunds escrow to contracting guild. */
    @Test
    void cancelContract_refundsEscrowToContractingGuild() {
        GuildContractService.ContractResult created =
                contracts.createContract(alphaId, "DIAMOND", 10, 100.0);
        String id = created.getContract().getId();

        assertTrue(contracts.cancelContract(id, alphaId));

        assertEquals(1000.0, balance(alphaId), "escrow must be refunded on cancel");
        assertEquals(GuildContract.Status.CANCELLED,
                contracts.getContract(id).orElseThrow().getStatus());
    }

    /** Returns whether cel contract non owner or non open rejected. */
    @Test
    void cancelContract_nonOwnerOrNonOpenRejected() {
        GuildContractService.ContractResult created =
                contracts.createContract(alphaId, "DIAMOND", 10, 100.0);
        String id = created.getContract().getId();

        assertFalse(contracts.cancelContract(id, betaId), "only the contracting guild may cancel");
        assertEquals(900.0, balance(alphaId), "escrow must not be refunded to a non-owner");

        assertTrue(contracts.fulfillContract(id, betaId).isSuccessful());
        assertFalse(contracts.cancelContract(id, alphaId), "fulfilled contracts cannot be cancelled");
        assertEquals(600.0, balance(betaId), "fulfiller payment must be preserved");
    }

    /** Performs the open contracts lists only open contracts operation. */
    @Test
    void openContracts_listsOnlyOpenContracts() {
        contracts.createContract(alphaId, "DIAMOND", 10, 100.0);
        GuildContractService.ContractResult second =
                contracts.createContract(alphaId, "IRON_INGOT", 5, 50.0);

        List<GuildContract> open = contracts.getOpenContracts();
        assertEquals(2, open.size());

        contracts.fulfillContract(second.getContract().getId(), betaId);
        assertEquals(1, contracts.getOpenContracts().size(),
                "fulfilled contracts must leave the open list");

        List<GuildContract> forAlpha = contracts.getContractsForGuild(alphaId);
        assertEquals(2, forAlpha.size());
    }
}
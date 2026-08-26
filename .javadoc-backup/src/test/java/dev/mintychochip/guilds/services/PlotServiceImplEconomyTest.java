package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.GuildsServiceTestFixture;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-SQLite tests for the plot purchase economy: guild-bank debit/credit,
 * ownership transfer, audit rows, affordability, and rollback on failure.
 */
class PlotServiceImplEconomyTest {

    /** The world constant. */
    private static final String WORLD = "world";

    /** The temp dir. */
    @TempDir
    Path tempDir;

    /** The services. */
    private GuildsServiceTestFixture.Services services;
    /** The plots. */
    private PlotService plots;
    /** The guilds. */
    private GuildService guilds;
    /** The residents. */
    private ResidentService residents;

    /** The alice. */
    private UUID alice;
    /** The bob. */
    private UUID bob;
    /** The carol. */
    private UUID carol;
    /** The mayor. */
    private UUID mayor;

    /** Sets the up. */
    @BeforeEach
    void setUp() {
        services = GuildsServiceTestFixture.create(tempDir);
        plots = services.plotService();
        guilds = services.guildService();
        residents = services.residentService();

        mayor = UUID.randomUUID();
        alice = UUID.randomUUID();
        bob = UUID.randomUUID();
        carol = UUID.randomUUID();

        // The guilds table enforces mayor_uuid -> residents; the mayor must exist first.
        residents.createResident(mayor, "mayor");
        guilds.createGuild("Alpha", mayor);
        guilds.createGuild("Beta", mayor);
        residents.createResident(alice, "alice");
        residents.createResident(bob, "bob");
        residents.createResident(carol, "carol");
        assertTrue(guilds.addResidentToGuild("Alpha", alice));
        assertTrue(guilds.addResidentToGuild("Alpha", carol));
        assertTrue(guilds.addResidentToGuild("Beta", bob));

        guilds.updateGuildBalance("Alpha", 1000.0);
        guilds.updateGuildBalance("Beta", 500.0);
    }

    /** Performs the tear down operation. */
    @AfterEach
    void tearDown() {
        if (services != null) {
            services.databaseManager().shutdown();
        }
    }

    /**
     * Performs the plot at operation.
     * @param x the x
     * @param z the z
     * @return the result
     */
    private GuildBlock plotAt(int x, int z) {
        return plots.getGuildBlock(x, z, WORLD).orElseThrow();
    }

    /** Guild-owned plot at (x,z) in Alpha, owned by the given resident and listed at the price. */
    private GuildBlock listedPlot(int x, int z, UUID owner, double price) {
        GuildBlock plot = plots.createGuildBlock(x, z, WORLD, "Alpha");
        plot.setOwnerId(owner);
        plots.updateGuildBlock(plot);
        assertTrue(plots.setPlotForSale(plot.getId(), price, owner), "seller must be the owner");
        return plot;
    }

    /**
     * Performs the balance operation.
     * @param guildName the guild name
     * @return the result
     */
    private double balance(String guildName) {
        return guilds.getGuild(guildName).map(Guild::getBalance).orElseThrow();
    }

    /**
     * Performs the audit rows operation.
     * @return the result
     * @throws Exception if an error occurs
     */
    private long auditRows() throws Exception {
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM economy_transactions WHERE type = 'PLOT_PURCHASE'")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    /**
     * Performs the cross guild purchase moves money and ownership operation.
     * @throws Exception if an error occurs
     */
    @Test
    void crossGuildPurchase_movesMoneyAndOwnership() throws Exception {
        GuildBlock plot = listedPlot(100, 100, alice, 100.0);

        assertTrue(plots.buyPlot(bob, plot.getId(), 100.0));

        assertEquals(400.0, balance("Beta"), "buyer guild must be debited (500 - 100)");
        assertEquals(1100.0, balance("Alpha"), "plot guild must be credited");
        assertEquals(bob, plots.getGuildBlock(plot.getId()).orElseThrow().getOwnerId());
        assertFalse(plots.getGuildBlock(plot.getId()).orElseThrow().isForSale());
        assertEquals(1, auditRows(), "purchase must be audited");
    }

    /**
     * Performs the same guild purchase transfers ownership without moving money operation.
     * @throws Exception if an error occurs
     */
    @Test
    void sameGuildPurchase_transfersOwnershipWithoutMovingMoney() throws Exception {
        GuildBlock plot = listedPlot(200, 200, alice, 100.0);

        assertTrue(plots.buyPlot(carol, plot.getId(), 100.0));

        assertEquals(1000.0, balance("Alpha"), "same-guild purchase nets zero");
        assertEquals(carol, plots.getGuildBlock(plot.getId()).orElseThrow().getOwnerId());
        assertEquals(1, auditRows(), "same-guild purchase is still audited");
    }

    /**
     * Performs the insufficient funds rejects purchase and changes nothing operation.
     * @throws Exception if an error occurs
     */
    @Test
    void insufficientFunds_rejectsPurchaseAndChangesNothing() throws Exception {
        GuildBlock plot = listedPlot(300, 300, alice, 600.0);

        assertFalse(plots.buyPlot(bob, plot.getId(), 600.0), "Beta has only 500");

        assertEquals(500.0, balance("Beta"));
        assertEquals(1000.0, balance("Alpha"));
        assertEquals(alice, plots.getGuildBlock(plot.getId()).orElseThrow().getOwnerId());
        assertEquals(0, auditRows());
    }

    /**
     * Performs the owner cannot buy own plot operation.
     * @throws Exception if an error occurs
     */
    @Test
    void ownerCannotBuyOwnPlot() throws Exception {
        GuildBlock plot = listedPlot(400, 400, alice, 100.0);
        assertFalse(plots.buyPlot(alice, plot.getId(), 100.0));
        assertEquals(alice, plots.getGuildBlock(plot.getId()).orElseThrow().getOwnerId());
    }

    /**
     * Performs the not for sale or wrong price is rejected operation.
     * @throws Exception if an error occurs
     */
    @Test
    void notForSaleOrWrongPriceIsRejected() throws Exception {
        GuildBlock plot = listedPlot(500, 500, alice, 100.0);
        assertFalse(plots.buyPlot(bob, plot.getId(), 99.0), "price must match exactly");

        assertTrue(plots.buyPlot(carol, plot.getId(), 100.0),
                "same-guild purchase is allowed and transfers ownership (net-zero)");
        assertFalse(plots.buyPlot(bob, plot.getId(), 100.0), "plot is no longer for sale after purchase");
    }

    /**
     * Performs the failed purchase rolls back every change operation.
     * @throws Exception if an error occurs
     */
    @Test
    void failedPurchaseRollsBackEveryChange() throws Exception {
        GuildBlock plot = listedPlot(600, 600, alice, 100.0);

        // Force the audit INSERT to fail mid-transaction: the debit, credit,
        // and ownership transfer must all roll back.
        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE economy_transactions");
        }
        try {
            assertFalse(plots.buyPlot(bob, plot.getId(), 100.0));
        } finally {
            try (Connection connection = services.databaseManager().getDataSource().getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS economy_transactions (
                            id TEXT PRIMARY KEY,
                            guild_id TEXT,
                            player_uuid TEXT,
                            type TEXT NOT NULL,
                            amount REAL NOT NULL,
                            description TEXT,
                            timestamp TEXT NOT NULL
                        )
                        """);
            }
        }

        assertEquals(500.0, balance("Beta"), "debit must roll back");
        assertEquals(1000.0, balance("Alpha"), "credit must roll back");
        assertEquals(alice, plots.getGuildBlock(plot.getId()).orElseThrow().getOwnerId(),
                "ownership transfer must roll back");
    }

    /**
     * Returns whether resident afford plot reflects balance and sale state.
     * @throws Exception if an error occurs
     */
    @Test
    void canResidentAffordPlot_reflectsBalanceAndSaleState() throws Exception {
        GuildBlock plot = listedPlot(700, 700, alice, 100.0);
        assertTrue(plots.canResidentAffordPlot(bob, plot.getId()), "Beta (500) can afford 100");
        assertTrue(plots.canResidentAffordPlot(carol, plot.getId()), "Alpha (1000) can afford 100");

        plots.setPlotForSale(plot.getId(), 0.0, alice);
        assertFalse(plots.canResidentAffordPlot(bob, plot.getId()), "not for sale -> cannot afford");
    }

    /**
     * Performs the buyer without guild cannot purchase operation.
     * @throws Exception if an error occurs
     */
    @Test
    void buyerWithoutGuildCannotPurchase() throws Exception {
        GuildBlock plot = listedPlot(800, 800, alice, 100.0);
        UUID outsider = UUID.randomUUID();
        residents.createResident(outsider, "outsider");
        assertFalse(plots.buyPlot(outsider, plot.getId(), 100.0));
        assertFalse(plots.canResidentAffordPlot(outsider, plot.getId()));
    }
}

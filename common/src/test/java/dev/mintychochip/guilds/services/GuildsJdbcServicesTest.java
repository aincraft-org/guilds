package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.GuildsJdbcFixture;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the shipped JDBC guild, permission, and contract services.
 * Skips when {@code GUILDS_TEST_JDBC_URL} is unset via {@link dev.mintychochip.territory.PostgresTestDatabase}.
 */
class GuildsJdbcServicesTest {

    /** The services. */
    private GuildsJdbcFixture.Services services;
    /** The suffix. */
    private String suffix;

    /**
     * Sets the up.
     * @throws Exception if an error occurs
     */
    @BeforeEach
    void setUp() throws Exception {
        services = GuildsJdbcFixture.open();
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    /** Performs the tear down operation. */
    @AfterEach
    void tearDown() {
        if (services != null) {
            services.close();
        }
    }

    /**
     * Creates a new get guild maps level points and active project.
     * @throws Exception if an error occurs
     */
    @Test
    void createGetGuildMapsLevelPointsAndActiveProject() throws Exception {
        UUID mayor = UUID.randomUUID();
        services.residentService().createResident(mayor, "mayor-" + suffix);
        String name = "Mapped-" + suffix;
        Guild created = services.guildService().createGuild(name, mayor);

        assertEquals(name, created.getName());
        assertEquals(mayor, created.getMayorUuid());

        try (Connection connection = services.databaseManager().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE guilds SET guild_level = ?, tech_points = ?, active_project_id = ? WHERE id = ?")) {
            statement.setInt(1, 3);
            statement.setInt(2, 2);
            statement.setString(3, "better_storage");
            statement.setString(4, created.getId());
            assertEquals(1, statement.executeUpdate());
        }

        Optional<Guild> loaded = services.guildService().getGuild(name);
        assertTrue(loaded.isPresent());
        assertEquals(name, loaded.get().getName());
        assertEquals(created.getId(), loaded.get().getId());
        assertEquals(3, loaded.get().getGuildLevel());
        assertEquals(2, loaded.get().getTechPoints());
        assertEquals("better_storage", loaded.get().getActiveProjectId());
    }

    /** Performs the permission grant then check operation. */
    @Test
    void permissionGrantThenCheck() {
        UUID resident = UUID.randomUUID();
        services.residentService().createResident(resident, "alice-" + suffix);
        String contextId = "alpha-" + suffix;

        assertFalse(services.permissionService().hasPermission(resident, "build", "guild", contextId));
        assertTrue(services.permissionService().grantPermission(resident, "build", "guild", contextId, true));
        assertTrue(services.permissionService().hasPermission(resident, "build", "guild", contextId));
    }

    /** Performs the contract post fulfill and post cancel escrow operation. */
    @Test
    void contractPostFulfillAndPostCancelEscrow() {
        UUID mayor = UUID.randomUUID();
        services.residentService().createResident(mayor, "escrow-" + suffix);
        Guild alpha = services.guildService().createGuild("Alpha-" + suffix, mayor);
        Guild beta = services.guildService().createGuild("Beta-" + suffix, mayor);
        services.guildService().updateGuildBalance(alpha.getName(), 1000.0);
        services.guildService().updateGuildBalance(beta.getName(), 500.0);

        GuildContractService.ContractResult posted =
                services.guildContractService().createContract(alpha.getId(), "DIAMOND", 10, 100.0);
        assertTrue(posted.isSuccessful());
        assertEquals(900.0, balance(alpha.getId()));

        GuildContractService.FulfillResult fulfilled =
                services.guildContractService().fulfillContract(posted.getContract().getId(), beta.getId());
        assertTrue(fulfilled.isSuccessful());
        assertEquals(600.0, balance(beta.getId()));
        assertEquals(GuildContract.Status.FULFILLED,
                services.guildContractService().getContract(posted.getContract().getId()).orElseThrow().getStatus());

        GuildContractService.ContractResult second =
                services.guildContractService().createContract(alpha.getId(), "IRON_INGOT", 5, 50.0);
        assertTrue(second.isSuccessful());
        assertEquals(850.0, balance(alpha.getId()));
        assertTrue(services.guildContractService().cancelContract(second.getContract().getId(), alpha.getId()));
        assertEquals(900.0, balance(alpha.getId()), "escrow must be refunded on cancel");
        assertEquals(GuildContract.Status.CANCELLED,
                services.guildContractService().getContract(second.getContract().getId()).orElseThrow().getStatus());
    }

    /**
     * Performs the balance operation.
     * @param guildId the guild id
     * @return the result
     */
    private double balance(String guildId) {
        return services.guildService().getGuildById(guildId).map(Guild::getBalance).orElseThrow();
    }
}

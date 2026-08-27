package dev.mintychochip.guilds;

import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.database.migration.SchemaInitializer;
import dev.mintychochip.guilds.services.GuildContractService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.GuildToggleService;
import dev.mintychochip.guilds.services.LocationService;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.impl.AllianceServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildContractServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildToggleServiceImpl;
import dev.mintychochip.guilds.services.impl.LocationServiceImpl;
import dev.mintychochip.guilds.services.impl.PermissionServiceImpl;
import dev.mintychochip.guilds.services.impl.PlotServiceImpl;
import dev.mintychochip.guilds.services.impl.ResidentServiceImpl;
import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.persist.PostgresDatabase;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Wires the shipped JDBC guilds services against the shared {@code Database} pool.
 */
public final class GuildsJdbcFixture {

    /** Immutable data carrier for services. */
    public record Services(
            PostgresDatabase database,
            DatabaseManager databaseManager,
            ResidentService residentService,
            GuildService guildService,
            PermissionService permissionService,
            GuildContractService guildContractService
    ) implements AutoCloseable {
        /** Performs the close operation. */
        @Override
        public void close() {
            databaseManager.shutdown();
            database.close();
        }
    }

    /** Creates a new guilds jdbc fixture instance. */
    private GuildsJdbcFixture() {
    }

    /**
     * Performs the open operation.
     * @return the result
     * @throws IOException if an error occurs
     */
    public static Services open() throws IOException {
        Logger logger = Logger.getLogger("common-guilds-jdbc");
        PostgresDatabase database = PostgresTestDatabase.open();
        SchemaInitializer schemaInitializer = new SchemaInitializer(logger);
        DatabaseManager databaseManager = new DatabaseManager(
                logger, database.dataSource(), schemaInitializer);

        ResidentServiceImpl residents = new ResidentServiceImpl(databaseManager, logger);
        GuildServiceImpl guilds = new GuildServiceImpl(databaseManager, logger, residents);
        PlotServiceImpl plots = new PlotServiceImpl(databaseManager, guilds, logger);
        LocationService locations = new LocationServiceImpl(plots, guilds);
        GuildToggleService toggles = new GuildToggleServiceImpl(locations);
        AllianceServiceImpl alliances = new AllianceServiceImpl(databaseManager, logger, guilds);
        PermissionServiceImpl permissions = new PermissionServiceImpl(
                databaseManager, logger, plots, guilds, residents, toggles, locations, alliances);
        guilds.setPermissionService(permissions);
        plots.setPermissionService(permissions);
        GuildContractServiceImpl contracts = new GuildContractServiceImpl(databaseManager, guilds);
        return new Services(database, databaseManager, residents, guilds, permissions, contracts);
    }
}

package dev.mintychochip.guilds;

import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.persist.PostgresDatabase;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.database.migration.SchemaInitializer;
import dev.mintychochip.guilds.services.AllianceService;
import dev.mintychochip.guilds.services.GuildContractService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.GuildToggleService;
import dev.mintychochip.guilds.services.LocationService;
import dev.mintychochip.guilds.services.PermissionService;
import dev.mintychochip.guilds.services.PlotService;
import dev.mintychochip.guilds.services.ResidentService;
import dev.mintychochip.guilds.services.impl.AllianceServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildContractServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildServiceImpl;
import dev.mintychochip.guilds.services.impl.GuildToggleServiceImpl;
import dev.mintychochip.guilds.services.impl.LocationServiceImpl;
import dev.mintychochip.guilds.services.impl.PermissionServiceImpl;
import dev.mintychochip.guilds.services.impl.PlotServiceImpl;
import dev.mintychochip.guilds.services.impl.ResidentServiceImpl;

import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Test fixture: real PostgreSQL-backed guilds services wired exactly like the
 * {@link GuildsServices} composition root, against the shared integration
 * database configured by {@code GUILDS_TEST_JDBC_URL}.
 */
public final class GuildsServiceTestFixture {

    /** Immutable data carrier for services. */
    public record Services(
            DatabaseManager databaseManager,
            ResidentService residentService,
            GuildService guildService,
            PlotService plotService,
            PermissionService permissionService,
            AllianceService allianceService,
            GuildContractService guildContractService
    ) {
    }

    /** Creates a new guilds service test fixture instance. */
    private GuildsServiceTestFixture() {
    }

    /**
     * Performs the create operation.
     * @param tempDir the temp dir
     * @return the result
     */
    public static Services create(Path tempDir) {
        Logger logger = Logger.getLogger("guilds-test");
        PostgresDatabase database = PostgresTestDatabase.open();
        SchemaInitializer schemaInitializer = new SchemaInitializer(logger);
        DatabaseManager databaseManager = new DatabaseManager(
                logger, database.dataSource(), schemaInitializer, true);

        ResidentServiceImpl residentImpl = new ResidentServiceImpl(databaseManager, logger);
        GuildServiceImpl guildImpl = new GuildServiceImpl(databaseManager, logger, residentImpl);
        PlotServiceImpl plotImpl = new PlotServiceImpl(databaseManager, guildImpl, logger);
        LocationService locationService = new LocationServiceImpl(plotImpl, guildImpl);
        GuildToggleService guildToggleService = new GuildToggleServiceImpl(locationService);
        AllianceServiceImpl allianceImpl = new AllianceServiceImpl(
                databaseManager, logger, guildImpl);
        PermissionServiceImpl permissionImpl = new PermissionServiceImpl(
                databaseManager, logger, plotImpl, guildImpl, residentImpl, guildToggleService, locationService,
                allianceImpl);
        guildImpl.setPermissionService(permissionImpl);
        plotImpl.setPermissionService(permissionImpl);

        GuildContractServiceImpl contractImpl = new GuildContractServiceImpl(databaseManager, guildImpl);

        return new Services(databaseManager, residentImpl, guildImpl, plotImpl, permissionImpl, allianceImpl, contractImpl);
    }
}

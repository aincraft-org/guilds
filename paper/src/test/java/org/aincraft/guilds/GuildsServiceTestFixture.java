package org.aincraft.guilds;

import org.aincraft.guilds.config.DatabaseConfig;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.database.migration.SchemaInitializer;
import org.aincraft.guilds.services.AllianceService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.GuildToggleService;
import org.aincraft.guilds.services.LocationService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.impl.AllianceServiceImpl;
import org.aincraft.guilds.services.impl.GuildServiceImpl;
import org.aincraft.guilds.services.impl.GuildToggleServiceImpl;
import org.aincraft.guilds.services.impl.LocationServiceImpl;
import org.aincraft.guilds.services.impl.PermissionServiceImpl;
import org.aincraft.guilds.services.impl.PlotServiceImpl;
import org.aincraft.guilds.services.impl.ResidentServiceImpl;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test fixture: real SQLite-backed guilds services wired exactly like the
 * {@link GuildsServices} composition root (including the late-bound
 * permission-service setters), against a fresh database in a temp folder.
 */
public final class GuildsServiceTestFixture {

    public record Services(
            DatabaseManager databaseManager,
            ResidentService residentService,
            GuildService guildService,
            PlotService plotService,
            PermissionService permissionService,
            AllianceService allianceService
    ) {
    }

    private GuildsServiceTestFixture() {
    }

    public static Services create(Path tempDir) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("guilds-test"));

        File databaseFile = tempDir.resolve("guilds.db").toFile();
        DatabaseConfig databaseConfig = new DatabaseConfig(
                plugin, databaseFile, "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        SchemaInitializer schemaInitializer = new SchemaInitializer(plugin);
        DatabaseManager databaseManager = new DatabaseManager(plugin, databaseConfig, schemaInitializer);

        Logger logger = Logger.getLogger("guilds-test");
        ResidentServiceImpl residentImpl = new ResidentServiceImpl(databaseManager, logger);
        GuildServiceImpl guildImpl = new GuildServiceImpl(databaseManager, logger, residentImpl);
        PlotServiceImpl plotImpl = new PlotServiceImpl(databaseManager, guildImpl, logger);
        LocationService locationService = new LocationServiceImpl(plotImpl, guildImpl);
        GuildToggleService guildToggleService = new GuildToggleServiceImpl(locationService);
        AllianceServiceImpl allianceImpl = new AllianceServiceImpl(
                plugin, databaseManager, logger, guildImpl);
        PermissionServiceImpl permissionImpl = new PermissionServiceImpl(
                databaseManager, logger, plotImpl, guildImpl, residentImpl, guildToggleService, locationService,
                allianceImpl);
        guildImpl.setPermissionService(permissionImpl);
        plotImpl.setPermissionService(permissionImpl);

        return new Services(databaseManager, residentImpl, guildImpl, plotImpl, permissionImpl, allianceImpl);
    }
}

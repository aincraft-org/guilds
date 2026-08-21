package org.aincraft.guilds.territory;

import org.aincraft.guilds.territory.persist.PostgresDatabase;
import org.aincraft.guilds.GuildsServices;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.impl.GuildServiceImpl;
import org.aincraft.guilds.storage.service.BukkitMainThreadExecutor;
import org.aincraft.guilds.storage.service.GuildStorageService;
import org.aincraft.guilds.storage.service.MainThreadExecutor;
import org.aincraft.guilds.storage.service.RegistryStorageFacilityAccessValidator;
import org.aincraft.guilds.storage.service.StorageFacilityAccessValidator;
import org.aincraft.guilds.territory.building.FacilityAnchorValidator;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the manual composition root: building {@link GuildsServices}
 * must wire the whole service graph (previously Guice's eager singletons),
 * including the Guild/Permission/Plot cycle broken by setPermissionService.
 * The composition root is hosted directly by the Guilds plugin.
 */
class GuildsServicesWiringTest {

    @Test
    void manualWiring_buildsFullServiceGraph() throws Exception {
        Wiring wiring = newServicesWithTempDataFolder();
        try {
            GuildsServices services = wiring.services();
            assertNotNull(services.getBroadcastService());
            assertNotNull(services.getGuildService());
            assertNotNull(services.getPermissionService());
            assertNotNull(services.getCommandRegistry());
        } finally {
            wiring.database().close();
        }
    }

    @Test
    void cycleBreak_wiresPermissionServiceIntoGuildService() throws Exception {
        Wiring wiring = newServicesWithTempDataFolder();
        try {
            GuildsServices services = wiring.services();
            GuildService guild = services.getGuildService();
            PermissionService permission = services.getPermissionService();

            Field permissionField = GuildServiceImpl.class.getDeclaredField("permissionService");
            permissionField.setAccessible(true);
            assertSame(permission, permissionField.get(guild));
        } finally {
            wiring.database().close();
        }
    }


    @Test
    void wireStorage_usesRegistryValidatorAndBukkitMainThreadExecutor() throws Exception {
        Wiring wiring = newServicesWithTempDataFolder();
        try {
            GuildsServices services = wiring.services();
            FacilityRegistry facilities = mock(FacilityRegistry.class);
            GovernanceRegistry governance = mock(GovernanceRegistry.class);
            FacilityAnchorValidator anchors = mock(FacilityAnchorValidator.class);
            services.wireStorage(facilities, governance, anchors);
            GuildStorageService storage = services.getGuildStorageService();
            assertNotNull(storage);
            StorageFacilityAccessValidator accessValidator = services.getStorageFacilityAccessValidator();
            MainThreadExecutor mainThreadExecutor = services.getStorageMainThreadExecutor();
            assertTrue(accessValidator instanceof RegistryStorageFacilityAccessValidator);
            assertTrue(mainThreadExecutor instanceof BukkitMainThreadExecutor);
        } finally {
            wiring.database().close();
        }
    }

    private record Wiring(PostgresDatabase database, GuildsServices services) {
    }

    private static Wiring newServicesWithTempDataFolder() throws Exception {
        var host = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(host.getDataFolder()).thenReturn(Files.createTempDirectory("guilds-wiring").toFile());
        when(host.getLogger()).thenReturn(java.util.logging.Logger.getLogger("wiring"));
        PostgresDatabase database = PostgresTestDatabase.open();
        return new Wiring(database, new GuildsServices(host, database));
    }
}

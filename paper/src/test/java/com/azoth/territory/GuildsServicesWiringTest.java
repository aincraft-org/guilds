package com.azoth.territory;

import org.aincraft.guilds.GuildsServices;
import org.aincraft.guilds.services.BroadcastService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.TownService;
import org.aincraft.guilds.services.impl.TownServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Smoke test for the manual composition root: building {@link GuildsServices}
 * must wire the whole service graph (previously Guice's eager singletons),
 * including the Town/Permission/Plot cycle broken by setPermissionService.
 * The composition root is hosted directly by the Azoth Territory plugin.
 */
class GuildsServicesWiringTest {

    @Test
    void manualWiring_buildsFullServiceGraph() throws Exception {
        GuildsServices services = newServicesWithTempDataFolder();

        assertNotNull(services.getBroadcastService());
        assertNotNull(services.getTownService());
        assertNotNull(services.getPermissionService());
        assertNotNull(services.getCommandRegistry());
        assertNotNull(services.getWebServer());
    }

    @Test
    void cycleBreak_wiresPermissionServiceIntoTownService() throws Exception {
        GuildsServices services = newServicesWithTempDataFolder();

        TownService town = services.getTownService();
        PermissionService permission = services.getPermissionService();

        // The late-bound setter must have run: broadcast and permission
        // evaluation depend on permissionService inside townService.
        Field permissionField = TownServiceImpl.class.getDeclaredField("permissionService");
        permissionField.setAccessible(true);
        assertSame(permission, permissionField.get(town));
    }

    private static GuildsServices newServicesWithTempDataFolder() throws Exception {
        var host = mock(org.bukkit.plugin.java.JavaPlugin.class);
        when(host.getDataFolder()).thenReturn(Files.createTempDirectory("azoth-wiring").toFile());
        when(host.getLogger()).thenReturn(java.util.logging.Logger.getLogger("wiring"));
        return new GuildsServices(host);
    }
}

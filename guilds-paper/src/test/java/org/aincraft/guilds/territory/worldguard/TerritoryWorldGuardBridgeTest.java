package org.aincraft.guilds.territory.worldguard;

import org.aincraft.guilds.territory.registry.TerritoryRegistry;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorldGuard is never actually installed/enabled in this unit test process
 * (no live Bukkit server), so {@code WorldGuard.getInstance().getPlatform()}
 * genuinely throws {@link IllegalStateException} exactly as it would on a
 * real server that doesn't have WorldGuard — exercising the bridge's real
 * degrade path rather than a mocked stand-in for it.
 */
@ExtendWith(MockitoExtension.class)
class TerritoryWorldGuardBridgeTest {

    @Mock
    private Plugin plugin;
    @Mock
    private TerritoryRegistry registry;

    @Test
    void startDegradesToNoOpWhenWorldGuardIsNotAvailable() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("test-worldguard-bridge"));

        TerritoryWorldGuardBridge bridge = new TerritoryWorldGuardBridge(
                plugin, registry, Optional::empty);

        assertDoesNotThrow(bridge::start);

        // Degraded (WorldGuard absent/uninitialized): never touches the server to
        // register listeners or schedule the refresh task.
        verify(plugin, never()).getServer();
    }

    @Test
    void stopBeforeStartIsSafe() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-worldguard-bridge"));

        TerritoryWorldGuardBridge bridge = new TerritoryWorldGuardBridge(
                plugin, registry, Optional::empty);

        assertDoesNotThrow(bridge::stop);
    }

    @Test
    void startThenStopIsSafe() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-worldguard-bridge"));

        TerritoryWorldGuardBridge bridge = new TerritoryWorldGuardBridge(
                plugin, registry, Optional::empty);

        bridge.start();
        assertDoesNotThrow(bridge::stop);
    }

    @Test
    void pluginYmlDeclaresWorldGuardAsSoftDependency() {
        String pluginYml = read("guilds-paper/src/main/resources/plugin.yml");

        assertTrue(pluginYml.contains("softdepend: [MapGUI, squaremap, WorldGuard, Mint, PlaceholderAPI]"));
        // MapGUI is optional too: core Guilds systems must enable without it.
        assertFalse(pluginYml.contains("depend: [MapGUI]"));
    }

    @Test
    void guildsPluginWiresBridgeStartAndStop() {
        String plugin = read("guilds-paper/src/main/java/org/aincraft/guilds/GuildsPlugin.java");

        assertTrue(plugin.contains("new TerritoryWorldGuardBridge("));
        assertTrue(plugin.contains("this.worldGuardBridge.start();"));
        assertTrue(plugin.contains("worldGuardBridge.stop();"));
    }

    private static String read(String file) {
        try {
            Path cwd = Path.of(System.getProperty("user.dir"));
            Path direct = cwd.resolve(file);
            if (Files.isRegularFile(direct)) {
                return Files.readString(direct);
            }
            return Files.readString(cwd.resolve("..", file));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

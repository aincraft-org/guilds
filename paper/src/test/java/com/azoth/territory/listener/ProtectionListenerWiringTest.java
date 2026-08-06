package com.azoth.territory.listener;

import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural evidence that protection listeners exist, handle the required
 * Paper events, and that the plugin enable path registers them against domain APIs.
 * No live server required.
 */
class ProtectionListenerWiringTest {

    @Test
    void protectionListener_isListenerAndHoldsDomainProtection() {
        TerritoryRegistry territories = new TerritoryRegistry();
        GovernanceRegistry governance = new GovernanceRegistry(territories);
        BlockProtection protection = new BlockProtection(governance);
        ProtectionListener listener = new ProtectionListener(protection);

        assertTrue(listener instanceof Listener);
        assertTrue(listener.protection() == protection);
    }

    @Test
    void protectionListener_declaresHandlersForBreakPlaceFireExplosions() {
        Set<Class<?>> handled = Arrays.stream(ProtectionListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventHandler.class))
                .map(this::eventType)
                .collect(Collectors.toCollection(HashSet::new));

        assertTrue(handled.contains(BlockBreakEvent.class), "missing BlockBreakEvent handler: " + handled);
        assertTrue(handled.contains(BlockPlaceEvent.class), "missing BlockPlaceEvent handler: " + handled);
        assertTrue(handled.contains(BlockBurnEvent.class), "missing BlockBurnEvent handler: " + handled);
        assertTrue(handled.contains(BlockSpreadEvent.class), "missing BlockSpreadEvent handler: " + handled);
        assertTrue(handled.contains(BlockIgniteEvent.class), "missing BlockIgniteEvent handler: " + handled);
        assertTrue(handled.contains(EntityExplodeEvent.class), "missing EntityExplodeEvent handler: " + handled);
        assertTrue(handled.contains(BlockExplodeEvent.class), "missing BlockExplodeEvent handler: " + handled);
    }

    @Test
    void protectionListener_declaresHandlersForSpawnAndEntityGrief() {
        Set<Class<?>> handled = Arrays.stream(ProtectionListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventHandler.class))
                .map(this::eventType)
                .collect(Collectors.toCollection(HashSet::new));

        assertTrue(handled.contains(CreatureSpawnEvent.class),
                "missing CreatureSpawnEvent handler: " + handled);
        assertTrue(handled.contains(EntityChangeBlockEvent.class),
                "missing EntityChangeBlockEvent handler: " + handled);
        assertTrue(handled.contains(EntityInteractEvent.class),
                "missing EntityInteractEvent handler: " + handled);
    }

    @Test
    void protectionListener_handlersCallDomainApis() throws Exception {
        // Source-level: handlers must call canBreak / canPlace / toggle-aware fire
        // and explosion gates / blocksMobSpawn / blocksEntityGrief
        String source = readMainSource("com/azoth/territory/listener/ProtectionListener.java");
        assertTrue(source.contains("protection.canBreak("), "break path must call domain canBreak");
        assertTrue(source.contains("protection.canPlace("), "place path must call domain canPlace");
        assertTrue(source.contains("protection.isFireProtected("),
                "fire path must call domain isFireProtected");
        assertTrue(source.contains("protection.areExplosionsProtected("),
                "explosion path must call domain areExplosionsProtected");
        assertTrue(source.contains("protection.blocksMobSpawn("),
                "spawn path must call domain blocksMobSpawn");
        assertTrue(source.contains("protection.blocksEntityGrief("),
                "entity grief path must call domain blocksEntityGrief");
        assertTrue(source.contains("event.setCancelled(true)"), "must cancel denied break/place/fire");
        assertTrue(source.contains("blockList()") || source.contains("filterProtectedBlocks"),
                "must filter explosion block lists");
    }

    @Test
    void restrictedSpawnReasons_includeNaturalAndHostile_excludeEggsSpawnersCommands() {
        assertTrue(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.NATURAL));
        assertTrue(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.REINFORCEMENTS));
        assertTrue(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.RAID));
        assertTrue(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.PATROL));
        assertFalse(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.SPAWNER));
        assertFalse(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.SPAWNER_EGG));
        assertFalse(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.COMMAND));
        assertFalse(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.CUSTOM));
        assertFalse(ProtectionListener.isRestrictedSpawnReason(
                CreatureSpawnEvent.SpawnReason.BREEDING));
    }

    @Test
    void pluginOnEnable_registersGovernanceProtectionAndListeners() throws Exception {
        String source = readMainSource("com/azoth/territory/AzothTerritoryPlugin.java");
        assertTrue(source.contains("new GovernanceRegistry("), "must construct GovernanceRegistry");
        assertTrue(source.contains("new BlockProtection("), "must construct BlockProtection");
        assertTrue(source.contains("new ProtectionListener("), "must construct ProtectionListener");
        assertTrue(source.contains("registerEvents("), "must register Paper listeners");
        assertTrue(source.contains("import com.azoth.territory.listener.ProtectionListener"),
                "must import ProtectionListener");
        assertNotNull(Class.forName("com.azoth.territory.listener.ProtectionListener"));
        assertNotNull(Class.forName("com.azoth.territory.permission.BlockProtection"));
    }

    @Test
    void domainEnvironmentalApi_existsOnShippedBlockProtection() throws Exception {
        Method env = BlockProtection.class.getMethod(
                "isEnvironmentallyProtected", String.class, int.class, int.class);
        Method spawn = BlockProtection.class.getMethod(
                "blocksMobSpawn", String.class, int.class, int.class);
        Method grief = BlockProtection.class.getMethod(
                "blocksEntityGrief", String.class, int.class, int.class);
        assertNotNull(env);
        assertEquals(boolean.class, env.getReturnType());
        assertEquals(boolean.class, spawn.getReturnType());
        assertEquals(boolean.class, grief.getReturnType());
    }

    private Class<?> eventType(Method method) {
        Class<?>[] params = method.getParameterTypes();
        assertTrue(params.length >= 1, "EventHandler must take event: " + method);
        return params[0];
    }

    private static String readMainSource(String relativeUnderMainJava) throws Exception {
        // Prefer filesystem (dev tree); fall back to classpath resource if present
        Path fromCwd = Path.of("src/main/java").resolve(relativeUnderMainJava);
        if (Files.isRegularFile(fromCwd)) {
            return Files.readString(fromCwd, StandardCharsets.UTF_8);
        }
        String resource = "/" + relativeUnderMainJava;
        try (InputStream in = ProtectionListenerWiringTest.class.getResourceAsStream(resource)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        // Walk up from user.dir for multi-module / worktree layouts
        Path p = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path candidate = p.resolve("src/main/java").resolve(relativeUnderMainJava);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
            p = p.getParent();
            if (p == null) {
                break;
            }
        }
        throw new IllegalStateException("Cannot locate source: " + relativeUnderMainJava);
    }
}

package com.azoth.territory.listener;

import com.azoth.territory.permission.BlockProtection;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural evidence that the interaction listener handles the required Paper
 * events and calls the domain interaction APIs. No live server required.
 */
class InteractionProtectionListenerTest {

    @Test
    void interactionListener_isListenerAndHoldsDomainProtection() {
        TerritoryRegistry territories = new TerritoryRegistry();
        GovernanceRegistry governance = new GovernanceRegistry(territories);
        BlockProtection protection = new BlockProtection(governance);
        InteractionProtectionListener listener = new InteractionProtectionListener(protection);

        assertTrue(listener instanceof Listener);
        assertEquals(protection, listener.protection());
    }

    @Test
    void interactionListener_declaresHandlersForInteractionVectors() {
        Set<Class<?>> handled = Arrays.stream(
                        InteractionProtectionListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventHandler.class))
                .map(this::eventType)
                .collect(Collectors.toCollection(HashSet::new));

        // Container open + hopper steal
        assertTrue(handled.contains(InventoryOpenEvent.class),
                "missing InventoryOpenEvent handler: " + handled);
        assertTrue(handled.contains(InventoryMoveItemEvent.class),
                "missing InventoryMoveItemEvent handler: " + handled);
        assertTrue(handled.contains(InventoryPickupItemEvent.class),
                "missing InventoryPickupItemEvent handler: " + handled);
        // Doors/buttons/levers/beds gate on PlayerInteractEvent in ProtectionListener;
        // bed enter is here.
        assertTrue(handled.contains(PlayerBedEnterEvent.class),
                "missing PlayerBedEnterEvent handler: " + handled);
        // Item frames / armor stands / paintings
        assertTrue(handled.contains(HangingPlaceEvent.class),
                "missing HangingPlaceEvent handler: " + handled);
        assertTrue(handled.contains(HangingBreakByEntityEvent.class),
                "missing HangingBreakByEntityEvent handler: " + handled);
        assertTrue(handled.contains(PlayerInteractEntityEvent.class),
                "missing PlayerInteractEntityEvent handler: " + handled);
        assertTrue(handled.contains(PlayerItemFrameChangeEvent.class),
                "missing PlayerItemFrameChangeEvent handler: " + handled);
        assertTrue(handled.contains(PlayerArmorStandManipulateEvent.class),
                "missing PlayerArmorStandManipulateEvent handler: " + handled);
        assertTrue(handled.contains(EntityPlaceEvent.class),
                "missing EntityPlaceEvent handler: " + handled);
        // Vehicles
        assertTrue(handled.contains(VehicleEnterEvent.class),
                "missing VehicleEnterEvent handler: " + handled);
        assertTrue(handled.contains(VehicleDamageEvent.class),
                "missing VehicleDamageEvent handler: " + handled);
        assertTrue(handled.contains(VehicleDestroyEvent.class),
                "missing VehicleDestroyEvent handler: " + handled);
        assertTrue(handled.contains(PlayerBucketEmptyEvent.class),
                "missing PlayerBucketEmptyEvent handler: " + handled);
        // Leash
        assertTrue(handled.contains(PlayerLeashEntityEvent.class),
                "missing PlayerLeashEntityEvent handler: " + handled);
        assertTrue(handled.contains(PlayerUnleashEntityEvent.class),
                "missing PlayerUnleashEntityEvent handler: " + handled);
        // Animal kill / PvP
        assertTrue(handled.contains(EntityDamageByEntityEvent.class),
                "missing EntityDamageByEntityEvent handler: " + handled);
        // Spawn + teleport
        assertTrue(handled.contains(PlayerTeleportEvent.class),
                "missing PlayerTeleportEvent handler: " + handled);
    }

    @Test
    void interactionListener_handlersCallDomainApis() throws Exception {
        String source = readMainSource("com/azoth/territory/listener/InteractionProtectionListener.java");
        assertTrue(source.contains("protection.canInteract("),
                "interaction path must call domain canInteract");
        assertTrue(source.contains("protection.canInteractWithEntity("),
                "entity path must call domain canInteractWithEntity");
        assertTrue(source.contains("protection.canPlace("),
                "bucket-empty path must call domain canPlace");
        assertTrue(source.contains("protection.allowsPvp("),
                "pvp path must call domain allowsPvp");
        assertTrue(source.contains("protection.canTeleportInto("),
                "teleport/spawn path must call domain canTeleportInto");
        assertTrue(source.contains("protection.isEnvironmentallyProtected("),
                "mechanical hopper path must use environmental eligibility");
        assertTrue(source.contains("event.setCancelled(true)"),
                "must cancel denied interactions");
    }

    @Test
    void domainInteractionApis_existOnShippedBlockProtection() throws Exception {
        assertEquals(boolean.class, BlockProtection.class.getMethod(
                "canInteract", String.class, int.class, int.class, String.class).getReturnType());
        assertEquals(boolean.class, BlockProtection.class.getMethod(
                "canInteractWithEntity", String.class, int.class, int.class, String.class).getReturnType());
        assertEquals(boolean.class, BlockProtection.class.getMethod(
                "crossesBoundary", String.class, int.class, int.class, int.class, int.class).getReturnType());
        assertEquals(boolean.class, BlockProtection.class.getMethod(
                "allowsPvp", String.class, int.class, int.class, String.class, String.class).getReturnType());
        assertEquals(boolean.class, BlockProtection.class.getMethod(
                "canTeleportInto", String.class, int.class, int.class, String.class).getReturnType());
    }

    @Test
    void pluginOnEnable_registersInteractionListener() throws Exception {
        String source = readMainSource("com/azoth/territory/AzothTerritoryPlugin.java");
        assertTrue(source.contains("new InteractionProtectionListener("),
                "must construct InteractionProtectionListener");
        assertTrue(source.contains(
                        "import com.azoth.territory.listener.InteractionProtectionListener"),
                "must import InteractionProtectionListener");
        assertNotNull(Class.forName("com.azoth.territory.listener.InteractionProtectionListener"));
    }

    private Class<?> eventType(Method method) {
        Class<?>[] params = method.getParameterTypes();
        assertTrue(params.length >= 1, "EventHandler must take event: " + method);
        return params[0];
    }

    private static String readMainSource(String relativeUnderMainJava) throws Exception {
        Path fromCwd = Path.of("src/main/java").resolve(relativeUnderMainJava);
        if (Files.isRegularFile(fromCwd)) {
            return Files.readString(fromCwd, StandardCharsets.UTF_8);
        }
        String resource = "/" + relativeUnderMainJava;
        try (InputStream in = InteractionProtectionListenerTest.class.getResourceAsStream(resource)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
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

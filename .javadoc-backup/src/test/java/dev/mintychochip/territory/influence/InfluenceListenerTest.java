package dev.mintychochip.territory.influence;

import dev.mintychochip.territory.PostgresTestDatabase;
import dev.mintychochip.territory.persist.PostgresDatabase;
import dev.mintychochip.territory.permission.GovernanceRegistry;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceListenerTest {

    @Test
    void influenceListener_isListenerAndHoldsEngine() {
        PostgresDatabase database = PostgresTestDatabase.open();
        try {
            TerritoryRegistry territories = new TerritoryRegistry();
            GovernanceRegistry governance = new GovernanceRegistry(territories);
            InfluenceEngine engine = new InfluenceEngine(governance, InfluenceConfig.defaults(),
                    new PostgresInfluenceStore(database),
                    (t, g) -> { }, Logger.getLogger("test"));
            InfluenceListener listener = new InfluenceListener(governance, engine);

            assertTrue(listener instanceof Listener);
            assertEquals(engine, listener.engine());
        } finally {
            database.close();
        }
    }

    @Test
    void influenceListener_declaresHandlersForActivityVectors() {
        Set<Class<?>> handled = Arrays.stream(InfluenceListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventHandler.class))
                .map(m -> m.getParameterTypes()[0])
                .collect(Collectors.toCollection(HashSet::new));

        assertTrue(handled.contains(PlayerDeathEvent.class), "missing PlayerDeathEvent handler: " + handled);
        assertTrue(handled.contains(EntityDeathEvent.class), "missing EntityDeathEvent handler: " + handled);
        assertTrue(handled.contains(BlockBreakEvent.class), "missing BlockBreakEvent handler: " + handled);
        assertTrue(handled.contains(BlockPlaceEvent.class), "missing BlockPlaceEvent handler: " + handled);
        assertTrue(handled.contains(CraftItemEvent.class), "missing CraftItemEvent handler: " + handled);
    }
}

package org.aincraft.guilds.territory.standing;

import org.aincraft.guilds.territory.PostgresTestDatabase;
import org.aincraft.guilds.territory.persist.PostgresDatabase;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandingListenerTest {

    @Test
    void standingListener_isListenerAndHoldsEngine() throws Exception {
        PostgresDatabase database = PostgresTestDatabase.open();
        try {
            TerritoryRegistry territories = new TerritoryRegistry();
            GovernanceRegistry governance = new GovernanceRegistry(territories);
            StandingEngine engine = new StandingEngine(governance, StandingConfig.defaults(),
                    new PostgresStandingStore(database), Logger.getLogger("test"));
            StandingListener listener = new StandingListener(governance, engine);

            assertTrue(listener instanceof Listener);
            assertEquals(engine, listener.engine());
        } finally {
            database.close();
        }
    }

    @Test
    void standingListener_declaresHandlersForActivityVectors() {
        Set<Class<?>> handled = Arrays.stream(StandingListener.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventHandler.class))
                .map(m -> m.getParameterTypes()[0])
                .collect(Collectors.toCollection(HashSet::new));
        assertTrue(handled.contains(PlayerDeathEvent.class), "missing PlayerDeathEvent handler: " + handled);
        assertTrue(handled.contains(EntityDeathEvent.class), "missing EntityDeathEvent handler: " + handled);
        assertTrue(handled.contains(BlockBreakEvent.class), "missing BlockBreakEvent handler: " + handled);
        assertEquals(3, handled.size(), "only the three standing sources should be handled: " + handled);
    }
}

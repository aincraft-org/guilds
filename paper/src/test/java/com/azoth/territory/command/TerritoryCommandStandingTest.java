package com.azoth.territory.command;

import com.azoth.territory.AzothTerritoryPlugin;
import com.azoth.territory.PostgresTestDatabase;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import com.azoth.territory.permission.GovernanceRegistry;
import com.azoth.territory.registry.TerritoryRegistry;
import com.azoth.territory.standing.PostgresStandingStore;
import com.azoth.territory.standing.StandingConfig;
import com.azoth.territory.standing.StandingEngine;
import com.azoth.territory.standing.StandingSource;
import com.azoth.territory.persist.PostgresDatabase;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerritoryCommandStandingTest {

    private PostgresDatabase database;
    private StandingEngine engine;
    private TerritoryRegistry territories;

    @BeforeEach
    void setUp() throws Exception {
        territories = new TerritoryRegistry();
        GovernanceRegistry governance = new GovernanceRegistry(territories);
        territories.register(new Territory("everfall", "Everfall", "world",
                Boundary.ofPolygon(List.of(
                        new BlockPos(0, 0), new BlockPos(100, 0),
                        new BlockPos(100, 100), new BlockPos(0, 100))),
                List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town"));
        database = PostgresTestDatabase.open();
        engine = new StandingEngine(governance, StandingConfig.defaults(),
                new PostgresStandingStore(database), Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void pluginExposesStandingEngine() throws Exception {
        Method getter = AzothTerritoryPlugin.class.getMethod("getStandingEngine");
        assertEquals(StandingEngine.class, getter.getReturnType());
    }

    @Test
    void commandStanding_readsBarsForTerritory() {
        engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);

        AzothTerritoryPlugin plugin = mock(AzothTerritoryPlugin.class);
        when(plugin.getStandingEngine()).thenReturn(engine);
        TerritoryCommand cmd = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);

        assertDoesNotThrow(() -> cmd.onCommand(sender, mock(org.bukkit.command.Command.class),
                "territory", new String[]{"standing", "everfall"}));
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void commandStandingSet_admin_setsBar() {
        AzothTerritoryPlugin plugin = mock(AzothTerritoryPlugin.class);
        when(plugin.getStandingEngine()).thenReturn(engine);
        TerritoryCommand cmd = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("azoth.territory.admin")).thenReturn(true);

        assertDoesNotThrow(() -> cmd.onCommand(sender, mock(org.bukkit.command.Command.class),
                "territory", new String[]{"standing", "set", "everfall", "everfall-town", "250"}));
        assertEquals(250.0, engine.standing("everfall").orElseThrow().bars().get(0).value(), 0.001);
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void commandStandingReset_admin_clearsState() {
        engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);

        AzothTerritoryPlugin plugin = mock(AzothTerritoryPlugin.class);
        when(plugin.getStandingEngine()).thenReturn(engine);
        TerritoryCommand cmd = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("azoth.territory.admin")).thenReturn(true);

        assertDoesNotThrow(() -> cmd.onCommand(sender, mock(org.bukkit.command.Command.class),
                "territory", new String[]{"standing", "reset", "everfall"}));
        assertEquals(false, engine.standing("everfall").isPresent());
    }
}

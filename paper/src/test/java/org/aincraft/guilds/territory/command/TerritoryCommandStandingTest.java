package org.aincraft.guilds.territory.command;

import org.aincraft.guilds.GuildsPlugin;
import org.aincraft.guilds.territory.PostgresTestDatabase;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.ZoneType;
import org.aincraft.guilds.territory.permission.GovernanceRegistry;
import org.aincraft.guilds.territory.registry.TerritoryRegistry;
import org.aincraft.guilds.territory.standing.PostgresStandingStore;
import org.aincraft.guilds.territory.standing.StandingConfig;
import org.aincraft.guilds.territory.standing.StandingEngine;
import org.aincraft.guilds.territory.standing.StandingSource;
import org.aincraft.guilds.territory.persist.PostgresDatabase;
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
        Method getter = GuildsPlugin.class.getMethod("getStandingEngine");
        assertEquals(StandingEngine.class, getter.getReturnType());
    }

    @Test
    void commandStanding_readsBarsForTerritory() {
        engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);

        GuildsPlugin plugin = mock(GuildsPlugin.class);
        when(plugin.getStandingEngine()).thenReturn(engine);
        TerritoryCommand cmd = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);

        assertDoesNotThrow(() -> cmd.onCommand(sender, mock(org.bukkit.command.Command.class),
                "territory", new String[]{"standing", "everfall"}));
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void commandStandingSet_admin_setsBar() {
        GuildsPlugin plugin = mock(GuildsPlugin.class);
        when(plugin.getStandingEngine()).thenReturn(engine);
        TerritoryCommand cmd = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.territory.admin")).thenReturn(true);

        assertDoesNotThrow(() -> cmd.onCommand(sender, mock(org.bukkit.command.Command.class),
                "territory", new String[]{"standing", "set", "everfall", "everfall-town", "250"}));
        assertEquals(250.0, engine.standing("everfall").orElseThrow().bars().get(0).value(), 0.001);
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void commandStandingReset_admin_clearsState() {
        engine.accrue("everfall", "everfall-town", StandingSource.PVP_KILL);

        GuildsPlugin plugin = mock(GuildsPlugin.class);
        when(plugin.getStandingEngine()).thenReturn(engine);
        TerritoryCommand cmd = new TerritoryCommand(plugin);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.territory.admin")).thenReturn(true);

        assertDoesNotThrow(() -> cmd.onCommand(sender, mock(org.bukkit.command.Command.class),
                "territory", new String[]{"standing", "reset", "everfall"}));
        assertEquals(false, engine.standing("everfall").isPresent());
    }
}

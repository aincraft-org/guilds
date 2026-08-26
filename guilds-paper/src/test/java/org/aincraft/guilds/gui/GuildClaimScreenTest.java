package org.aincraft.guilds.gui;

import de.flog99.mapgui.Session;
import org.aincraft.guilds.map.ClaimLayer;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import de.flog99.mapgui.HandOptions;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuildClaimScreenTest {

    @Test
    void fixedCenterKeepsDisplayWorldWhenPlayerChangesWorld() {
        Player player = mock(Player.class);
        World worldA = worldNamed("world_a");
        World worldB = worldNamed("world_b");

        Location location = locationAt(worldA, 10, 20);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(worldB);

        Session session = mock(Session.class);
        when(session.player()).thenReturn(player);
        when(session.width()).thenReturn(128);
        when(session.height()).thenReturn(128);

        PlotService plots = mock(PlotService.class);
        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);

        GuildClaimScreen screen = new GuildClaimScreen("Alpha", guilds, plots, permissions, 1);
        screen.attach(session);
        screen.setFixedCenter(10, 20, "world_a");

        assertEquals("world_a", screen.displayWorld());
    }

    @Test
    void currentLayerClassifiesWithFixedWorldNotPlayerWorld() {
        Player player = mock(Player.class);
        World worldA = worldNamed("world_a");
        World worldB = worldNamed("world_b");

        Location location = locationAt(worldA, 10, 20);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(worldB);

        Session session = mock(Session.class);
        when(session.player()).thenReturn(player);
        when(session.width()).thenReturn(128);
        when(session.height()).thenReturn(128);

        PlotService plots = mock(PlotService.class);
        when(plots.getGuildBlock(anyInt(), anyInt(), eq("world_a"))).thenReturn(Optional.empty());

        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);

        GuildClaimScreen screen = new GuildClaimScreen("Alpha", guilds, plots, permissions, 1);
        screen.attach(session);
        screen.setFixedCenter(10, 20, "world_a");

        ClaimLayer layer = screen.currentLayer();

        assertEquals("world_a", layer.world());
        assertEquals(3, layer.size());
        verify(plots, atLeastOnce()).getGuildBlock(anyInt(), anyInt(), eq("world_a"));
        verify(plots, never()).getGuildBlock(anyInt(), anyInt(), eq("world_b"));
    }

    @Test
    void handUsesPopupModeLikeMapGuiMenu() {
        PlotService plots = mock(PlotService.class);
        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);

        GuildClaimScreen screen = new GuildClaimScreen("Alpha", guilds, plots, permissions, 1);

        assertEquals(HandOptions.popup(), screen.hand());
    }

    @Test
    void facingLabelConvertsYawToCompassPoints() {
        assertEquals("N", GuildClaimScreen.facingLabel(-180f));
        assertEquals("N", GuildClaimScreen.facingLabel(180f));
        assertEquals("NE", GuildClaimScreen.facingLabel(-135f));
        assertEquals("E", GuildClaimScreen.facingLabel(-90f));
        assertEquals("SE", GuildClaimScreen.facingLabel(-45f));
        assertEquals("S", GuildClaimScreen.facingLabel(0f));
        assertEquals("SW", GuildClaimScreen.facingLabel(45f));
        assertEquals("W", GuildClaimScreen.facingLabel(90f));
        assertEquals("NW", GuildClaimScreen.facingLabel(135f));
    }

    private World worldNamed(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private Location locationAt(World world, int chunkX, int chunkZ) {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);

        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(location.getChunk()).thenReturn(chunk);
        return location;
    }
}

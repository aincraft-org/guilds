package org.aincraft.guilds.gui;

import de.flog99.mapgui.Session;
import de.flog99.mapgui.ui.AwtFont;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
    void currentLayerClassifiesCenterChunkByPlotOwnership() {
        Player player = mock(Player.class);
        World worldA = worldNamed("world_a");
        Location location = locationAt(worldA, 10, 20);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(worldA);

        Session session = mock(Session.class);
        when(session.player()).thenReturn(player);
        when(session.width()).thenReturn(128);
        when(session.height()).thenReturn(128);

        Guild own = new Guild("Alpha", UUID.randomUUID());
        GuildBlock plot = new GuildBlock(10, 20, "world_a", own.getId());

        PlotService plots = mock(PlotService.class);
        when(plots.getGuildBlock(10, 20, "world_a")).thenReturn(Optional.of(plot));

        GuildService guilds = mock(GuildService.class);
        when(guilds.getGuildById(own.getId())).thenReturn(Optional.of(own));
        when(guilds.getGuild(own.getId())).thenReturn(Optional.of(own));

        PermissionService permissions = mock(PermissionService.class);

        GuildClaimScreen screen = new GuildClaimScreen("Alpha", guilds, plots, permissions, 1);
        screen.attach(session);
        screen.setFixedCenter(10, 20, "world_a");

        ClaimLayer layer = screen.currentLayer();
        ClaimLayer.Cell centerCell = layer.cellAt(10, 20).orElseThrow();
        assertEquals(ClaimLayer.Kind.OWN_GUILD, centerCell.kind());
        assertEquals("Alpha", centerCell.guildName());
    }


    @Test
    void handUsesPinnedMainHandModeForMapGestures() {
        PlotService plots = mock(PlotService.class);
        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);

        GuildClaimScreen screen = new GuildClaimScreen("Alpha", guilds, plots, permissions, 1);

        assertEquals(HandOptions.pinned(4), screen.hand());
    }

    @Test
    void mapUsesLargerReadableFont() {
        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), mock(PlotService.class), mock(PermissionService.class), 1);

        assertEquals(10, ((AwtFont) screen.font()).awt().getSize());
    }

    @Test
    void compassUsesLargerReadableFont() throws Exception {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));

        Session session = mock(Session.class);
        when(session.player()).thenReturn(player);
        when(session.width()).thenReturn(128);
        when(session.height()).thenReturn(128);

        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), mock(PlotService.class), mock(PermissionService.class), 1);
        screen.attach(session);

        de.flog99.mapgui.ui.Painter painter = mock(de.flog99.mapgui.ui.Painter.class);
        de.flog99.mapgui.ui.PaintContext context = new de.flog99.mapgui.ui.PaintContext(
                painter, new de.flog99.mapgui.ui.Rect(0, 0, 32, 32), false);
        var paintCompass = GuildClaimScreen.class.getDeclaredMethod(
                "paintCompass", de.flog99.mapgui.ui.PaintContext.class);
        paintCompass.setAccessible(true);
        paintCompass.invoke(screen, context);

        var capturedFont = org.mockito.ArgumentCaptor.forClass(de.flog99.mapgui.ui.TextFont.class);
        verify(painter).font(capturedFont.capture());
        assertEquals(8, ((AwtFont) capturedFont.getValue()).awt().getSize());
    }

    @Test
    void toolbarAndRecenterReplaceTheLegend() {
        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), mock(PlotService.class), mock(PermissionService.class), 1);

        assertEquals(6, screen.build().children().size());
    }

    @Test
    void defaultToolStartsInPanMode() {
        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), mock(PlotService.class), mock(PermissionService.class), 1);

        assertEquals(GuildClaimScreen.Tool.PAN, screen.activeTool());
    }

    @Test
    void panningMovesLogicalCenterOppositeDragDirection() {
        Player player = mock(Player.class);
        World world = worldNamed("world");
        Location location = locationAt(world, 0, 0);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(world);

        Session session = mock(Session.class);
        when(session.player()).thenReturn(player);
        when(session.width()).thenReturn(128);
        when(session.height()).thenReturn(128);

        PlotService plots = mock(PlotService.class);
        when(plots.getGuildBlock(anyInt(), anyInt(), eq("world"))).thenReturn(Optional.empty());

        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), plots, mock(PermissionService.class), 1);
        screen.attach(session);
        screen.setFixedCenter(0, 0, "world");

        screen.held(64, 64);
        screen.held(114, 64);

        assertEquals(-1, screen.currentLayer().centerChunkX());
        assertEquals(0, screen.currentLayer().centerChunkZ());
    }

    @Test
    void recenterRestoresPlayerFollowing() {
        Player player = mock(Player.class);
        World world = worldNamed("world");
        Location location = locationAt(world, 5, 7);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(world);

        Session session = mock(Session.class);
        when(session.player()).thenReturn(player);
        when(session.width()).thenReturn(128);
        when(session.height()).thenReturn(128);

        PlotService plots = mock(PlotService.class);
        when(plots.getGuildBlock(anyInt(), anyInt(), eq("world"))).thenReturn(Optional.empty());

        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), plots, mock(PermissionService.class), 1);
        screen.attach(session);
        screen.setFixedCenter(0, 0, "world");

        screen.recenter();

        assertTrue(screen.followingPlayer());
        assertEquals(5, screen.currentLayer().centerChunkX());
        assertEquals(7, screen.currentLayer().centerChunkZ());
    }

    @Test
    void mapUsesCachedTerrainInsteadOfPlayerLockedTerrain() {
        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), mock(PlotService.class), mock(PermissionService.class), 1);

        assertFalse(screen.terrain());
    }

    @Test
    void compassOverlayUsesCompactMinecraftItemFootprint() {
        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), mock(PlotService.class), mock(PermissionService.class), 1);

        assertEquals(32, screen.compassWidth());
        assertEquals(32, screen.compassHeight());
    }
    @Test
    void sameOwnerIdentifiesConnectedTerritoryAdjacency() {
        ClaimLayer.Cell ownA = new ClaimLayer.Cell(0, 0, ClaimLayer.Kind.OWN_GUILD, "Alpha");
        ClaimLayer.Cell ownB = new ClaimLayer.Cell(0, 1, ClaimLayer.Kind.OWN_GUILD, "Alpha");
        ClaimLayer.Cell otherSolaria1 = new ClaimLayer.Cell(1, 0, ClaimLayer.Kind.OTHER_GUILD, "Solaria");
        ClaimLayer.Cell otherSolaria2 = new ClaimLayer.Cell(1, 1, ClaimLayer.Kind.OTHER_GUILD, "Solaria");
        ClaimLayer.Cell otherValhalla = new ClaimLayer.Cell(2, 0, ClaimLayer.Kind.OTHER_GUILD, "Valhalla");
        ClaimLayer.Cell wilderness = new ClaimLayer.Cell(3, 0, ClaimLayer.Kind.WILDERNESS, null);

        // Adjacent cells of own guild connect
        org.junit.jupiter.api.Assertions.assertTrue(GuildClaimScreen.sameOwner(ownA, Optional.of(ownB)));
        // Adjacent cells of the same other guild connect
        org.junit.jupiter.api.Assertions.assertTrue(GuildClaimScreen.sameOwner(otherSolaria1, Optional.of(otherSolaria2)));
        // Different guilds do not connect (outer perimeter boundary drawn)
        org.junit.jupiter.api.Assertions.assertFalse(GuildClaimScreen.sameOwner(otherSolaria1, Optional.of(otherValhalla)));
        org.junit.jupiter.api.Assertions.assertFalse(GuildClaimScreen.sameOwner(ownA, Optional.of(otherSolaria1)));
        // Wilderness does not connect to claimed territory
        org.junit.jupiter.api.Assertions.assertFalse(GuildClaimScreen.sameOwner(ownA, Optional.of(wilderness)));
        // Out of bounds neighbor does not connect (outer map edge drawn)
        org.junit.jupiter.api.Assertions.assertFalse(GuildClaimScreen.sameOwner(ownA, Optional.empty()));
    }
    @Test
    void cellRectSpansEntireCanvasFlushWithNoGaps() {
        de.flog99.mapgui.ui.Rect bounds = new de.flog99.mapgui.ui.Rect(0, 0, 128, 128);
        int size = 11; // radius 5 -> 11x11 grid

        // First cell starts exactly at (0, 0)
        de.flog99.mapgui.ui.Rect first = GuildClaimScreen.cellRect(bounds, size, 0, 0);
        assertEquals(0, first.x());
        assertEquals(0, first.y());

        // Last cell ends exactly at (128, 128)
        de.flog99.mapgui.ui.Rect last = GuildClaimScreen.cellRect(bounds, size, size - 1, size - 1);
        assertEquals(128, last.x() + last.width());
        assertEquals(128, last.y() + last.height());

        // Adjacent cells are flush (no gaps or overlaps)
        for (int col = 0; col < size - 1; col++) {
            de.flog99.mapgui.ui.Rect a = GuildClaimScreen.cellRect(bounds, size, col, 0);
            de.flog99.mapgui.ui.Rect b = GuildClaimScreen.cellRect(bounds, size, col + 1, 0);
            assertEquals(a.x() + a.width(), b.x());
        }
    }
    @Test
    void chunkGridShiftsWithPlayerWithinChunk() {
        de.flog99.mapgui.ui.Rect bounds = new de.flog99.mapgui.ui.Rect(0, 0, 176, 176);
        int size = 11;

        de.flog99.mapgui.ui.Rect atBlockZero = GuildClaimScreen.shiftedCellRect(
                bounds, size, 5, 5, GuildClaimScreen.playerRelativeShift(0), 0.0);
        de.flog99.mapgui.ui.Rect atBlockOne = GuildClaimScreen.shiftedCellRect(
                bounds, size, 5, 5, GuildClaimScreen.playerRelativeShift(1), 0.0);

        assertEquals(atBlockZero.x() - 1, atBlockOne.x());
        assertEquals(atBlockZero.x() + atBlockZero.width() - 1,
                atBlockOne.x() + atBlockOne.width());
    }

    @Test
    void chunkGridMovesOnePixelAcrossChunkBoundary() {
        de.flog99.mapgui.ui.Rect beforeCrossing = GuildClaimScreen.shiftedCellRect(
                new de.flog99.mapgui.ui.Rect(0, 0, 176, 176),
                11, 6, 5, GuildClaimScreen.playerRelativeShift(15), 0.0);
        de.flog99.mapgui.ui.Rect afterCrossing = GuildClaimScreen.shiftedCellRect(
                new de.flog99.mapgui.ui.Rect(0, 0, 176, 176),
                11, 5, 5, GuildClaimScreen.playerRelativeShift(16), 0.0);

        assertEquals(beforeCrossing.x() - 1, afterCrossing.x());
    }

    @Test
    void cursorHitTestingUsesTheSamePlayerRelativeShift() {
        int size = 11;
        double shift = GuildClaimScreen.playerRelativeShift(1);
        int boundary = 87;

        assertEquals(4, GuildClaimScreen.cellIndexAtPixel(boundary - 1, 176, size, shift));
        assertEquals(5, GuildClaimScreen.cellIndexAtPixel(boundary, 176, size, shift));
    }

    @Test
    void cursorHitTestingMatchesRoundedNonDivisibleBoundary() {
        int size = 11;
        int boundary = GuildClaimScreen.shiftedCellRect(
                new de.flog99.mapgui.ui.Rect(0, 0, 128, 128),
                size, 2, 0, 0.0, 0.0).x();

        assertEquals(23, boundary);
        assertEquals(1, GuildClaimScreen.cellIndexAtPixel(boundary - 1, 128, size, 0.0));
        assertEquals(2, GuildClaimScreen.cellIndexAtPixel(boundary, 128, size, 0.0));
    }

    @Test
    void playerRelativeShiftUsesFloorModuloForNegativeCoordinates() {
        assertEquals(GuildClaimScreen.playerRelativeShift(15),
                GuildClaimScreen.playerRelativeShift(-1), 0.000001);
        assertEquals(GuildClaimScreen.playerRelativeShift(0),
                GuildClaimScreen.playerRelativeShift(-16), 0.000001);
    }

    @Test
    void playerMarkerStaysAtTerrainAnchorAsPlayerMovesWithinChunk() throws Exception {
        Player player = mock(Player.class);
        World world = worldNamed("world");
        Location location = mock(Location.class);
        Chunk chunk = mock(Chunk.class);
        when(player.getLocation()).thenReturn(location);
        when(player.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(0, 1);
        when(location.getBlockZ()).thenReturn(0);
        when(location.getChunk()).thenReturn(chunk);
        when(chunk.getX()).thenReturn(0);
        when(chunk.getZ()).thenReturn(0);

        Session session = mock(Session.class);
        when(session.player()).thenReturn(player);
        when(session.width()).thenReturn(176);
        when(session.height()).thenReturn(176);

        PlotService plots = mock(PlotService.class);
        when(plots.getGuildBlock(anyInt(), anyInt(), eq("world"))).thenReturn(Optional.empty());
        GuildClaimScreen screen = new GuildClaimScreen(
                "Alpha", mock(GuildService.class), plots, mock(PermissionService.class), 1);
        screen.attach(session);
        screen.setFixedCenter(0, 0, "world");

        de.flog99.mapgui.ui.Painter painter = mock(de.flog99.mapgui.ui.Painter.class);
        var paintLayer = GuildClaimScreen.class.getDeclaredMethod(
                "paintLayer", de.flog99.mapgui.ui.PaintContext.class);
        paintLayer.setAccessible(true);
        var context = new de.flog99.mapgui.ui.PaintContext(
                painter, new de.flog99.mapgui.ui.Rect(0, 0, 176, 176), false);
        paintLayer.invoke(screen, context);
        paintLayer.invoke(screen, context);

        verify(painter, times(2)).pixel(88, 88, java.awt.Color.RED);
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

package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PermissionService;
import org.aincraft.guilds.services.PlotService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarqueeUnclaimTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final String GUILD = "Alpha";
    private static final String WORLD = "world";

    @Test
    void permissionDeniedPreventsAllWrites() {
        PlotService plots = mock(PlotService.class);
        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.hasPermission(PLAYER, "unclaim", "guild", GUILD)).thenReturn(false);

        MarqueeUnclaim.Result result = MarqueeUnclaim.commit(
                plots, guilds, permissions, PLAYER, GUILD, WORLD, 0, 1, 0, 1);

        assertFalse(result.allowed());
        assertEquals(0, result.unclaimed());
        assertEquals(0, result.skipped());
        verify(plots, never()).unclaimGuildBlock(anyInt(), anyInt(), anyString());
    }

    @Test
    void unclaimsOnlyChunksOwnedByViewerGuild() {
        Guild own = new Guild(GUILD, UUID.randomUUID());
        GuildBlock block = new GuildBlock(0, 0, WORLD, own.getId());
        PlotService plots = mock(PlotService.class);
        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.hasPermission(PLAYER, "unclaim", "guild", GUILD)).thenReturn(true);
        when(plots.getGuildBlock(0, 0, WORLD)).thenReturn(Optional.of(block));
        when(guilds.getGuildById(own.getId())).thenReturn(Optional.of(own));
        when(plots.unclaimGuildBlock(0, 0, WORLD)).thenReturn(true);

        MarqueeUnclaim.Result result = MarqueeUnclaim.commit(
                plots, guilds, permissions, PLAYER, GUILD, WORLD, 0, 0, 0, 0);

        assertTrue(result.allowed());
        assertEquals(1, result.unclaimed());
        assertEquals(0, result.skipped());
        verify(plots).unclaimGuildBlock(0, 0, WORLD);
    }

    @Test
    void skipsEmptyAndForeignChunks() {
        Guild other = new Guild("Beta", UUID.randomUUID());
        GuildBlock foreign = new GuildBlock(1, 0, WORLD, other.getId());
        PlotService plots = mock(PlotService.class);
        GuildService guilds = mock(GuildService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.hasPermission(PLAYER, "unclaim", "guild", GUILD)).thenReturn(true);
        when(plots.getGuildBlock(0, 0, WORLD)).thenReturn(Optional.empty());
        when(plots.getGuildBlock(1, 0, WORLD)).thenReturn(Optional.of(foreign));
        when(guilds.getGuildById(other.getId())).thenReturn(Optional.of(other));

        MarqueeUnclaim.Result result = MarqueeUnclaim.commit(
                plots, guilds, permissions, PLAYER, GUILD, WORLD, 0, 1, 0, 0);

        assertTrue(result.allowed());
        assertEquals(0, result.unclaimed());
        assertEquals(2, result.skipped());
        verify(plots, never()).unclaimGuildBlock(anyInt(), anyInt(), anyString());
    }
}

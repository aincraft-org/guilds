package org.aincraft.guilds.gui;

import org.aincraft.guilds.models.GuildBlock;
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
import static org.mockito.Mockito.when;

class MarqueeClaimTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final String GUILD = "Alpha";
    private static final String WORLD = "world";

    @Test
    void permissionRevokedAtCommitDeniesAllWrites() {
        PlotService plots = mock(PlotService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.canClaimForGuild(PLAYER, GUILD)).thenReturn(false);

        MarqueeClaim.Result result = MarqueeClaim.commit(
                plots, permissions, PLAYER, GUILD, WORLD, 0, 1, 0, 1);

        assertFalse(result.allowed());
        assertEquals(0, result.claimed());
        assertEquals(0, result.skipped());
    }

    @Test
    void claimsAllWhenPermittedAndWilderness() {
        PlotService plots = mock(PlotService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.canClaimForGuild(PLAYER, GUILD)).thenReturn(true);
        when(plots.getGuildBlock(anyInt(), anyInt(), anyString())).thenReturn(Optional.empty());
        when(plots.claimGuildBlock(anyInt(), anyInt(), anyString(), eq(GUILD))).thenReturn(true);

        MarqueeClaim.Result result = MarqueeClaim.commit(
                plots, permissions, PLAYER, GUILD, WORLD, 0, 1, 0, 1);

        assertTrue(result.allowed());
        assertEquals(4, result.claimed());
        assertEquals(0, result.skipped());
    }

    @Test
    void skipsAlreadyClaimedCellsWhenPermitted() {
        GuildBlock existing = new GuildBlock(0, 0, WORLD, GUILD);
        PlotService plots = mock(PlotService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.canClaimForGuild(PLAYER, GUILD)).thenReturn(true);
        when(plots.getGuildBlock(anyInt(), anyInt(), anyString())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            return (x == 0 && z == 0) ? Optional.of(existing) : Optional.empty();
        });
        when(plots.claimGuildBlock(anyInt(), anyInt(), anyString(), eq(GUILD))).thenReturn(true);

        MarqueeClaim.Result result = MarqueeClaim.commit(
                plots, permissions, PLAYER, GUILD, WORLD, 0, 1, 0, 1);

        assertTrue(result.allowed());
        assertEquals(3, result.claimed());
        assertEquals(1, result.skipped());
    }
}

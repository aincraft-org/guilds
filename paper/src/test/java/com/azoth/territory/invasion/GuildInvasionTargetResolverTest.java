package com.azoth.territory.invasion;

import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildBlock;
import org.aincraft.guilds.services.GuildService;
import org.aincraft.guilds.services.PlotService;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuildInvasionTargetResolverTest {
    @Test
    void resolverTypeExistsAndResolvesExactName() {
        GuildService guilds = mock(GuildService.class);
        PlotService plots = mock(PlotService.class);
        Guild guild = new Guild("Guild A", java.util.UUID.randomUUID());
        GuildBlock claim = new GuildBlock(0, 0, "world", guild.getId());
        guild.setSpawnLocation(new org.aincraft.guilds.models.Location(8.5, 64, 8.5, "world"));
        when(guilds.getAllGuilds()).thenReturn(List.of(guild));
        when(plots.getGuildBlocksInGuild(guild.getName())).thenReturn(List.of(claim));
        when(plots.getGuildBlock(0, 0, "world")).thenReturn(java.util.Optional.of(claim));
        var resolver = new GuildInvasionTargetResolver(guilds, plots);
        var result = resolver.resolve("gUiLd a");
        assertTrue(result.isResolved());
        assertEquals(guild.getId(), result.target().orElseThrow().guildId());
    }

    @Test
    void unknownGuildIsRejected() {
        GuildService guilds = mock(GuildService.class);
        PlotService plots = mock(PlotService.class);
        when(guilds.getAllGuilds()).thenReturn(List.of());
        var result = new GuildInvasionTargetResolver(guilds, plots).resolve("missing");
        assertTrue(result.isRejected());
    }
}

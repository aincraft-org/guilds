package dev.mintychochip.guilds.commands;

import dev.mintychochip.guilds.GuildsPlugin;
import dev.mintychochip.territory.invasion.GuildDamage;
import dev.mintychochip.territory.invasion.InvasionRuntime;
import dev.mintychochip.territory.invasion.InvasionStartResult;
import dev.mintychochip.territory.invasion.InvasionStartStatus;
import dev.mintychochip.territory.invasion.InvasionState;
import dev.mintychochip.territory.invasion.InvasionStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for territory command invasion. */
class TerritoryCommandInvasionTest {
    /**
     * Performs the invasion commands require dedicated permission operation.
     * @throws Exception if an error occurs
     */
    @Test
    void invasionCommandsRequireDedicatedPermission() throws Exception {
        GuildsPlugin plugin = mock(GuildsPlugin.class);
        InvasionRuntime runtime = mock(InvasionRuntime.class);
        when(plugin.getInvasionRuntime()).thenReturn(runtime);
        CommandSender sender = mock(CommandSender.class);

        TerritoryCommandTestSupport.execute(plugin, sender, "territory invasion start Guild A");

        verify(sender).hasPermission("guilds.territory.invasion");
        verify(runtime, never()).start(any(), anyLong());
    }

    /**
     * Performs the invasion start stop and status delegate to runtime operation.
     * @throws Exception if an error occurs
     */
    @Test
    void invasionStartStopAndStatusDelegateToRuntime() throws Exception {
        GuildsPlugin plugin = mock(GuildsPlugin.class);
        InvasionRuntime runtime = mock(InvasionRuntime.class);
        when(plugin.getInvasionRuntime()).thenReturn(runtime);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("guilds.territory.invasion")).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(runtime.start(eq("Guild A"), anyLong()))
                .thenReturn(new InvasionStartResult(InvasionStartStatus.STARTED, id));
        when(runtime.resolveGuildId("Guild A")).thenReturn(Optional.of("guild-a"));
        when(runtime.cancel(eq("guild-a"), anyLong())).thenReturn(true);
        when(runtime.status("guild-a")).thenReturn(Optional.of(new InvasionState(
                id, "guild-a", "Guild A", "world", 0, 64, 0, InvasionStatus.ACTIVE,
                1, List.of(), new GuildDamage(2, 20), 1)));
        TerritoryCommandTestSupport.execute(plugin, sender, "territory invasion start Guild A");
        TerritoryCommandTestSupport.execute(plugin, sender, "territory invasion stop Guild A");
        TerritoryCommandTestSupport.execute(plugin, sender, "territory invasion status Guild A");

        verify(runtime, times(2)).resolveGuildId("Guild A");
        verify(runtime).start(eq("Guild A"), anyLong());
        verify(runtime).cancel(eq("guild-a"), anyLong());
        verify(runtime).status("guild-a");
        verify(sender, atLeast(3)).sendMessage(any(Component.class));
    }
}

package com.azoth.territory.command;

import com.azoth.territory.AzothTerritoryPlugin;
import com.azoth.territory.invasion.GuildDamage;
import com.azoth.territory.invasion.InvasionRuntime;
import com.azoth.territory.invasion.InvasionStartResult;
import com.azoth.territory.invasion.InvasionStartStatus;
import com.azoth.territory.invasion.InvasionState;
import com.azoth.territory.invasion.InvasionStatus;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
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

class TerritoryCommandInvasionTest {
    @Test
    void invasionCommandsRequireDedicatedPermission() {
        AzothTerritoryPlugin plugin = mock(AzothTerritoryPlugin.class);
        InvasionRuntime runtime = mock(InvasionRuntime.class);
        when(plugin.getInvasionRuntime()).thenReturn(runtime);
        CommandSender sender = mock(CommandSender.class);

        new TerritoryCommand(plugin).onCommand(sender, mock(Command.class), "territory",
                new String[]{"invasion", "start", "Guild A"});

        verify(sender).hasPermission("azoth.territory.invasion");
        verify(runtime, never()).start(any(), anyLong());
    }

    @Test
    void invasionStartStopAndStatusDelegateToRuntime() {
        AzothTerritoryPlugin plugin = mock(AzothTerritoryPlugin.class);
        InvasionRuntime runtime = mock(InvasionRuntime.class);
        when(plugin.getInvasionRuntime()).thenReturn(runtime);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("azoth.territory.invasion")).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(runtime.start(eq("Guild A"), anyLong()))
                .thenReturn(new InvasionStartResult(InvasionStartStatus.STARTED, id));
        when(runtime.resolveGuildId("Guild A")).thenReturn(Optional.of("guild-a"));
        when(runtime.cancel(eq("guild-a"), anyLong())).thenReturn(true);
        when(runtime.status("guild-a")).thenReturn(Optional.of(new InvasionState(
                id, "guild-a", "Guild A", "world", 0, 64, 0, InvasionStatus.ACTIVE,
                1, List.of(), new GuildDamage(2, 20), 1)));
        TerritoryCommand command = new TerritoryCommand(plugin);
        Command bukkitCommand = mock(Command.class);

        command.onCommand(sender, bukkitCommand, "territory", new String[]{"invasion", "start", "Guild A"});
        command.onCommand(sender, bukkitCommand, "territory", new String[]{"invasion", "stop", "Guild A"});
        command.onCommand(sender, bukkitCommand, "territory", new String[]{"invasion", "status", "Guild A"});

        verify(runtime, times(2)).resolveGuildId("Guild A");
        verify(runtime).start(eq("Guild A"), anyLong());
        verify(runtime).cancel(eq("guild-a"), anyLong());
        verify(runtime).status("guild-a");
        verify(sender, atLeast(3)).sendMessage(any(Component.class));
    }
}

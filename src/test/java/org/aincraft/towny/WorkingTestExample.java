package org.aincraft.towny;

import org.aincraft.towny.commands.TownyGeneralCommand;
import org.aincraft.towny.commands.MapCommand;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Working test example demonstrating the testing framework
 * This shows how to run tests for the Towny plugin
 */
class WorkingTestExample {

    @Mock
    private TownyPlugin plugin;

    @Mock
    private ResidentService residentService;

    @Mock
    private TownService townService;

    @Mock
    private PlotService plotService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private MapCommand mapCommand;

    private TownyGeneralCommand townyGeneralCommand;
    private Player mockPlayer;
    private Command mockCommand;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        // Initialize Mockito annotations
        MockitoAnnotations.openMocks(this);

        // Create command instance
        townyGeneralCommand = new TownyGeneralCommand(
            plugin, residentService, townService, plotService, permissionService, mapCommand
        );

        // Setup mock player
        mockPlayer = mock(Player.class);
        mockCommand = mock(Command.class);
        playerUuid = UUID.randomUUID();

        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("TestPlayer");
    }

    @Test
    @DisplayName("Should provide tab completion for towny commands")
    void shouldProvideTabCompletion() {
        // Given
        Player player = mockPlayer;

        // When
        List<String> completions = townyGeneralCommand.onTabComplete(player, mockCommand, "towny", new String[]{});

        // Then
        assertThat(completions).isNotEmpty();
        assertThat(completions).contains("chat", "tc", "map", "top", "help");

        // Verify the command structure works
        verifyNoInteractions(residentService, townService, plotService, permissionService);
    }

    @Test
    @DisplayName("Should show usage error when no arguments provided")
    void shouldShowUsageErrorWhenNoArguments() {
        // Given
        Player player = mockPlayer;

        // When
        boolean result = townyGeneralCommand.onCommand(player, mockCommand, "towny", new String[]{"chat"});

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage("§cUsage: /towny chat <message>");
        verify(player).sendMessage("§7Sends a message to all residents of your town.");
    }

    @Test
    @DisplayName("Should show help when no subcommand provided")
    void shouldShowHelpWhenNoSubcommandProvided() {
        // Given
        Player player = mockPlayer;

        // When
        boolean result = townyGeneralCommand.onCommand(player, mockCommand, "towny", new String[]{});

        // Then
        assertThat(result).isTrue();
        // Should show general towny info - we can't easily verify the exact content but we can verify it was called
        verify(player, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should reject console sender for player-only commands")
    void shouldRejectConsoleSender() {
        // Given
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("towny.general.chat")).thenReturn(true);

        // When
        boolean result = townyGeneralCommand.onCommand(console, mockCommand, "towny", new String[]{"chat", "test"});

        // Then
        // Console commands should return true but do nothing
        assertThat(result).isTrue();
        verify(console, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should handle unknown subcommands gracefully")
    void shouldHandleUnknownSubcommandsGracefully() {
        // Given
        Player player = mockPlayer;

        // When
        boolean result = townyGeneralCommand.onCommand(player, mockCommand, "towny", new String[]{"unknowncommand"});

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage("§cUnknown command: unknowncommand");
        verify(player).sendMessage("§7Use '/towny help' for available commands.");
    }
}
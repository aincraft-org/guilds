package org.aincraft.guilds;

import org.aincraft.guilds.commands.GuildsGeneralCommand;
import org.aincraft.guilds.commands.MapCommand;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.services.ResidentService;
import org.aincraft.guilds.services.TownService;
import org.aincraft.guilds.services.PlotService;
import org.aincraft.guilds.services.PermissionService;
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
 * This shows how to run tests for the Guilds plugin
 */
class WorkingTestExample {

    @Mock
    private GuildsPlugin plugin;

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

    private GuildsGeneralCommand guildsGeneralCommand;
    private Player mockPlayer;
    private Command mockCommand;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        // Initialize Mockito annotations
        MockitoAnnotations.openMocks(this);

        // Create command instance
        guildsGeneralCommand = new GuildsGeneralCommand(
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
    @DisplayName("Should provide tab completion for guilds commands")
    void shouldProvideTabCompletion() {
        // Given
        Player player = mockPlayer;

        // When
        List<String> completions = guildsGeneralCommand.onTabComplete(player, mockCommand, "guilds", new String[]{});

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
        boolean result = guildsGeneralCommand.onCommand(player, mockCommand, "guilds", new String[]{"chat"});

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage("§cUsage: /guilds chat <message>");
        verify(player).sendMessage("§7Sends a message to all residents of your town.");
    }

    @Test
    @DisplayName("Should show help when no subcommand provided")
    void shouldShowHelpWhenNoSubcommandProvided() {
        // Given
        Player player = mockPlayer;

        // When
        boolean result = guildsGeneralCommand.onCommand(player, mockCommand, "guilds", new String[]{});

        // Then
        assertThat(result).isTrue();
        // Should show general guilds info - we can't easily verify the exact content but we can verify it was called
        verify(player, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    @DisplayName("Should reject console sender for player-only commands")
    void shouldRejectConsoleSender() {
        // Given
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("guilds.general.chat")).thenReturn(true);

        // When
        boolean result = guildsGeneralCommand.onCommand(console, mockCommand, "guilds", new String[]{"chat", "test"});

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
        boolean result = guildsGeneralCommand.onCommand(player, mockCommand, "guilds", new String[]{"unknowncommand"});

        // Then
        assertThat(result).isTrue();
        verify(player).sendMessage("§cUnknown command: unknowncommand");
        verify(player).sendMessage("§7Use '/guilds help' for available commands.");
    }
}
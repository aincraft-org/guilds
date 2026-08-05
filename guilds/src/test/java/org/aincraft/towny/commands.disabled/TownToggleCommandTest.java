package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.Resident;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TownCommand toggle functionality
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Town Toggle Command Tests")
class TownToggleCommandTest {

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
    private Command mockCommand;

    @Mock
    private Player mockPlayer;

    private TownCommand townCommand;
    private UUID playerUuid;
    private String playerName = "TestPlayer";
    private String townName = "TestTown";
    private Town testTown;

    @BeforeEach
    void setUp() {
        townCommand = new TownCommand(plugin, residentService, townService, plotService, permissionService);

        playerUuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn(playerName);

        testTown = new Town(townName, playerUuid);
        testTown.setId(UUID.randomUUID().toString());

        // Setup default toggle states
        testTown.setPvpEnabled(false);
        testTown.setFireEnabled(false);
        testTown.setExplosionsEnabled(false);
        testTown.setMobsEnabled(true);
        testTown.setPublicEnabled(false);

        // Setup resident
        Resident resident = new Resident(playerUuid, playerName);
        resident.setTown(townName);
    }

    @Test
    @DisplayName("Should show toggle help when no arguments provided")
    void shouldShowToggleHelpWhenNoArgumentsProvided() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(contains("Town Toggle Commands"));
        verify(mockPlayer).sendMessage(contains("Available toggles:"));
    }

    @Test
    @DisplayName("Should show current toggle states with list command")
    void shouldShowCurrentToggleStatesWithListCommand() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(townService.getTownToggles(townName)).thenReturn(testTown.getAllToggles());
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "list"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(contains(townName + " Toggles"));
        verify(mockPlayer).sendMessage(contains("PvP: " + ChatColor.RED + "DISABLED"));
        verify(mockPlayer).sendMessage(contains("Fire: " + ChatColor.RED + "DISABLED"));
        verify(mockPlayer).sendMessage(contains("Mobs: " + ChatColor.GREEN + "ENABLED"));
    }

    @Test
    @DisplayName("Should toggle PvP successfully with admin rights")
    void shouldTogglePvpSuccessfullyWithAdminRights() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(townService.toggleTownPermission(eq(townName), eq("pvp"), eq(playerUuid))).thenReturn(true);
        when(townService.getTownToggle(eq(townName), eq("pvp"))).thenReturn(true);
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "pvp"});

        // Then
        assertThat(result).isTrue();
        verify(townService).toggleTownPermission(townName, "pvp", playerUuid);
        verify(mockPlayer).sendMessage(contains("Toggled PvP"));
        verify(mockPlayer).sendMessage(contains(ChatColor.GREEN + "ON"));
    }

    @Test
    @DisplayName("Should set toggle to specific value successfully")
    void shouldSetToggleToSpecificValueSuccessfully() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(townService.setTownToggle(eq(townName), eq("fire"), eq(true), eq(playerUuid))).thenReturn(true);
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "fire", "on"});

        // Then
        assertThat(result).isTrue();
        verify(townService).setTownToggle(townName, "fire", true, playerUuid);
        verify(mockPlayer).sendMessage(contains("Set Fire Spread"));
        verify(mockPlayer).sendMessage(contains(ChatColor.GREEN + "ON"));
    }

    @Test
    @DisplayName("Should handle different value formats (off, false, disable)")
    void shouldHandleDifferentValueFormats() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(townService.setTownToggle(eq(townName), eq("explosions"), eq(false), eq(playerUuid))).thenReturn(true);
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // Test "off"
        boolean result1 = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "explosions", "off"});

        // Reset and test "false"
        when(townService.setTownToggle(eq(townName), eq("explosions"), eq(false), eq(playerUuid))).thenReturn(true);
        boolean result2 = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "explosions", "false"});

        // Reset and test "disable"
        when(townService.setTownToggle(eq(townName), eq("explosions"), eq(false), eq(playerUuid))).thenReturn(true);
        boolean result3 = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "explosions", "disable"});

        // Then
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertThat(result3).isTrue();

        verify(townService, times(3)).setTownToggle(townName, "explosions", false, playerUuid);
        verify(mockPlayer, times(3)).sendMessage(contains("Set Explosions"));
        verify(mockPlayer, times(3)).sendMessage(contains(ChatColor.RED + "OFF"));
    }

    @Test
    @DisplayName("Should reject invalid toggle type")
    void shouldRejectInvalidToggleType() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "invalid_type"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "Unknown toggle type: invalid_type");
        verify(townService, never()).toggleTownPermission(anyString(), anyString(), any());
        verify(townService, never()).setTownToggle(anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Should reject invalid toggle value")
    void shouldRejectInvalidToggleValue() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "pvp", "invalid_value"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "Invalid value. Use: on/off, true/false, or enable/disable");
        verify(townService, never()).setTownToggle(anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Should reject command when player not in town")
    void shouldRejectCommandWhenPlayerNotInTown() {
        // Given
        Resident residentWithoutTown = new Resident(playerUuid, playerName);
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(residentWithoutTown));

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "pvp"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "You are not in a town!");
        verify(townService, never()).toggleTownPermission(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should reject command when player lacks admin rights")
    void shouldRejectCommandWhenPlayerLacksAdminRights() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(false);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "pvp"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "You don't have permission to toggle town settings!");
        verify(townService, never()).toggleTownPermission(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should handle failed toggle operation")
    void shouldHandleFailedToggleOperation() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(townService.toggleTownPermission(eq(townName), eq("pvp"), eq(playerUuid))).thenReturn(false);
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "pvp"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "Failed to toggle pvp!");
        verify(townService).toggleTownPermission(townName, "pvp", playerUuid);
    }

    @Test
    @DisplayName("Should handle failed toggle list operation")
    void shouldHandleFailedToggleListOperation() {
        // Given
        when(residentService.getResident(playerUuid)).thenReturn(Optional.of(new Resident(playerUuid, playerName)));
        when(townService.getTownToggles(townName)).thenReturn(Map.of());
        when(permissionService.hasTownAdmin(playerUuid, townName)).thenReturn(true);

        // When
        boolean result = townCommand.onCommand(mockPlayer, mockCommand, "town", new String[]{"toggle", "list"});

        // Then
        assertThat(result).isTrue();
        verify(mockPlayer).sendMessage(ChatColor.RED + "Failed to load toggle states!");
        verify(townService).getTownToggles(townName);
    }

    @Test
    @DisplayName("Should provide tab completion for toggle command")
    void shouldProvideTabCompletionForToggleCommand() {
        // When - Test tab completion for first argument
        List<String> completions = townCommand.onTabComplete(mockPlayer, mockCommand, "town", new String[]{""});

        // Then
        assertThat(completions).isNotNull();
        assertThat(completions).contains("toggle");
    }

    @Test
    @DisplayName("Should provide toggle type tab completions")
    void shouldProvideToggleTypeTabCompletions() {
        // When
        List<String> completions = townCommand.onTabComplete(mockPlayer, mockCommand, "town", new String[]{"toggle", ""});

        // Then
        assertThat(completions).isNotNull();
        assertThat(completions).contains("list", "pvp", "fire", "explosions", "mobs", "public");
    }

    @Test
    @DisplayName("Should provide toggle value tab completions for valid toggle types")
    void shouldProvideToggleValueTabCompletionsForValidToggleTypes() {
        // When - Test different toggle types
        List<String> completions1 = townCommand.onTabComplete(mockPlayer, mockCommand, "town", new String[]{"toggle", "pvp", ""});
        List<String> completions2 = townCommand.onTabComplete(mockPlayer, mockCommand, "town", new String[]{"toggle", "fire", ""});
        List<String> completions3 = townCommand.onTabComplete(mockPlayer, mockCommand, "town", new String[]{"toggle", "explosions", ""});

        // Then
        assertThat(completions1).isNotNull();
        assertThat(completions2).isNotNull();
        assertThat(completions3).isNotNull();

        assertThat(completions1).contains("on", "off", "true", "false", "enable", "disable");
        assertThat(completions2).contains("on", "off", "true", "false", "enable", "disable");
        assertThat(completions3).contains("on", "off", "true", "false", "enable", "disable");
    }

    @Test
    @DisplayName("Should not provide value completions for list command")
    void shouldNotProvideValueCompletionsForListCommand() {
        // When
        List<String> completions = townCommand.onTabComplete(mockPlayer, mockCommand, "town", new String[]{"toggle", "list", ""});

        // Then
        assertThat(completions).isNull(); // No completions for third argument when second is "list"
    }
}
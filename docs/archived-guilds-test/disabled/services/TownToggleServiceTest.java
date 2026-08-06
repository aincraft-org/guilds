package org.aincraft.guilds.services;

import com.google.inject.Inject;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.Resident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TownService toggle functionality
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Town Service Toggle Tests")
class TownToggleServiceTest {

    @Mock
    private TownService townService;

    @Mock
    private PermissionService permissionService;

    private Town testTown;
    private UUID mayorUuid;
    private UUID residentUuid;
    private String townName = "TestTown";

    @BeforeEach
    void setUp() {
        mayorUuid = UUID.randomUUID();
        residentUuid = UUID.randomUUID();

        testTown = new Town(townName, mayorUuid);
        testTown.setId(UUID.randomUUID().toString());

        // Set default toggle values for testing
        testTown.setPvpEnabled(false);
        testTown.setFireEnabled(false);
        testTown.setExplosionsEnabled(false);
        testTown.setMobsEnabled(true);
        testTown.setPublicEnabled(false);
    }

    @Test
    @DisplayName("Should successfully toggle town permission when user has admin rights")
    void shouldSuccessfullyToggleTownPermissionWhenUserHasAdminRights() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.of(testTown));
        when(townService.updateTown(any(Town.class))).thenReturn(testTown);
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(true);

        // When
        boolean result = townService.toggleTownPermission(townName, "pvp", residentUuid);

        // Then
        assertThat(result).isTrue();
        assertThat(testTown.isPvpEnabled()).isTrue();
        verify(townService).updateTown(testTown);
        verify(permissionService).hasTownAdmin(residentUuid, townName);
    }

    @Test
    @DisplayName("Should fail to toggle town permission when user lacks admin rights")
    void shouldFailToToggleTownPermissionWhenUserLacksAdminRights() {
        // Given
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(false);

        // When
        boolean result = townService.toggleTownPermission(townName, "pvp", residentUuid);

        // Then
        assertThat(result).isFalse();
        assertThat(testTown.isPvpEnabled()).isFalse(); // Should remain unchanged
        verify(permissionService).hasTownAdmin(residentUuid, townName);
        verify(townService, never()).updateTown(any(Town.class));
    }

    @Test
    @DisplayName("Should fail to toggle town permission when town doesn't exist")
    void shouldFailToToggleTownPermissionWhenTownDoesntExist() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.empty());
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(true);

        // When
        boolean result = townService.toggleTownPermission(townName, "pvp", residentUuid);

        // Then
        assertThat(result).isFalse();
        verify(permissionService).hasTownAdmin(residentUuid, townName);
        verify(townService, never()).updateTown(any(Town.class));
    }

    @Test
    @DisplayName("Should get all town toggles successfully")
    void shouldGetAllTownTogglesSuccessfully() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.of(testTown));

        // When
        Map<String, Boolean> toggles = townService.getTownToggles(townName);

        // Then
        assertThat(toggles).hasSize(5);
        assertThat(toggles.get("pvp")).isFalse();
        assertThat(toggles.get("fire")).isFalse();
        assertThat(toggles.get("explosions")).isFalse();
        assertThat(toggles.get("mobs")).isTrue();
        assertThat(toggles.get("public")).isFalse();
        verify(townService).getTown(townName);
    }

    @Test
    @DisplayName("Should return empty map when town doesn't exist")
    void shouldReturnEmptyMapWhenTownDoesntExist() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.empty());

        // When
        Map<String, Boolean> toggles = townService.getTownToggles(townName);

        // Then
        assertThat(toggles).isEmpty();
        verify(townService).getTown(townName);
    }

    @Test
    @DisplayName("Should set town toggle successfully with admin rights")
    void shouldSetTownToggleSuccessfullyWithAdminRights() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.of(testTown));
        when(townService.updateTown(any(Town.class))).thenReturn(testTown);
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(true);

        // When
        boolean result = townService.setTownToggle(townName, "pvp", true, residentUuid);

        // Then
        assertThat(result).isTrue();
        assertThat(testTown.isPvpEnabled()).isTrue();
        verify(townService).updateTown(testTown);
        verify(permissionService).hasTownAdmin(residentUuid, townName);
    }

    @Test
    @DisplayName("Should fail to set town toggle without admin rights")
    void shouldFailToSetTownToggleWithoutAdminRights() {
        // Given
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(false);

        // When
        boolean result = townService.setTownToggle(townName, "pvp", true, residentUuid);

        // Then
        assertThat(result).isFalse();
        assertThat(testTown.isPvpEnabled()).isFalse(); // Should remain unchanged
        verify(permissionService).hasTownAdmin(residentUuid, townName);
        verify(townService, never()).updateTown(any(Town.class));
    }

    @Test
    @DisplayName("Should fail to set town toggle with invalid type")
    void shouldFailToSetTownToggleWithInvalidType() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.of(testTown));
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(true);

        // When
        boolean result = townService.setTownToggle(townName, "invalid_type", true, residentUuid);

        // Then
        assertThat(result).isFalse();
        verify(permissionService).hasTownAdmin(residentUuid, townName);
        verify(townService, never()).updateTown(any(Town.class));
    }

    @Test
    @DisplayName("Should get town toggle state correctly")
    void shouldGetTownToggleStateCorrectly() {
        // Given
        testTown.setPvpEnabled(true);
        when(townService.getTown(townName)).thenReturn(Optional.of(testTown));

        // When
        boolean pvpState = townService.getTownToggle(townName, "pvp");
        boolean fireState = townService.getTownToggle(townName, "fire");
        boolean invalidState = townService.getTownToggle(townName, "invalid");

        // Then
        assertThat(pvpState).isTrue();
        assertThat(fireState).isFalse();
        assertThat(invalidState).isFalse();
        verify(townService, times(3)).getTown(townName);
    }

    @Test
    @DisplayName("Should return false for toggle when town doesn't exist")
    void shouldReturnFalseForToggleWhenTownDoesntExist() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.empty());

        // When
        boolean result = townService.getTownToggle(townName, "pvp");

        // Then
        assertThat(result).isFalse();
        verify(townService).getTown(townName);
    }

    @Test
    @DisplayName("Should handle multiple toggle operations in sequence")
    void shouldHandleMultipleToggleOperationsInSequence() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.of(testTown));
        when(townService.updateTown(any(Town.class))).thenReturn(testTown);
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(true);

        // When - Perform multiple operations
        townService.setTownToggle(townName, "pvp", true, residentUuid);
        townService.setTownToggle(townName, "fire", true, residentUuid);
        townService.toggleTownPermission(townName, "explosions", residentUuid);
        townService.setTownToggle(townName, "mobs", false, residentUuid);
        townService.setTownToggle(townName, "public", true, residentUuid);

        // Then - Verify all changes were applied
        assertThat(testTown.isPvpEnabled()).isTrue();
        assertThat(testTown.isFireEnabled()).isTrue();
        assertThat(testTown.isExplosionsEnabled()).isTrue(); // Toggled from false
        assertThat(testTown.isMobsEnabled()).isFalse();
        assertThat(testTown.isPublicEnabled()).isTrue();

        verify(townService, times(5)).updateTown(any(Town.class));
        verify(permissionService, times(5)).hasTownAdmin(residentUuid, townName);
    }

    @Test
    @DisplayName("Should preserve case insensitivity in toggle operations")
    void shouldPreserveCaseInsensitivityInToggleOperations() {
        // Given
        when(townService.getTown(townName)).thenReturn(Optional.of(testTown));
        when(townService.updateTown(any(Town.class))).thenReturn(testTown);
        when(permissionService.hasTownAdmin(residentUuid, townName)).thenReturn(true);

        // When - Test various cases
        townService.setTownToggle(townName, "PVP", true, residentUuid);
        townService.setTownToggle(townName, "FIRE", true, residentUuid);
        townService.setTownToggle(townName, "EXPLOSIONS", true, residentUuid);
        townService.setTownToggle(townName, "MOBS", false, residentUuid);
        townService.setTownToggle(townName, "PUBLIC", true, residentUuid);

        // Then - Verify changes were applied
        assertThat(testTown.isPvpEnabled()).isTrue();
        assertThat(testTown.isFireEnabled()).isTrue();
        assertThat(testTown.isExplosionsEnabled()).isTrue();
        assertThat(testTown.isMobsEnabled()).isFalse();
        assertThat(testTown.isPublicEnabled()).isTrue();

        verify(townService, times(5)).updateTown(any(Town.class));
    }
}
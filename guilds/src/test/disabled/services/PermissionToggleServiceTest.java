package org.aincraft.towny.services;

import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownBlock;
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
 * Unit tests for PermissionService toggle integration functionality
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Permission Service Toggle Tests")
class PermissionToggleServiceTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private PlotService plotService;

    @Mock
    private TownService townService;

    private Town testTown;
    private TownBlock testTownBlock;
    private String worldName = "test_world";
    private String townId = "test_town_id";
    private String townName = "TestTown";

    @BeforeEach
    void setUp() {
        testTown = new Town(townName, UUID.randomUUID());
        testTown.setId(townId);
        testTown.setPvpEnabled(true);
        testTown.setFireEnabled(false);
        testTown.setExplosionsEnabled(true);
        testTown.setMobsEnabled(false);
        testTown.setPublicEnabled(true);

        testTownBlock = new TownBlock(10, 20, worldName, townId);
        testTownBlock.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should return correct PvP state at town location")
    void shouldReturnCorrectPvpStateAtTownLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));

        // When
        boolean pvpEnabled = permissionService.isPvpEnabledAtLocation(165, 320, worldName); // 10*16+5, 20*16+0

        // Then
        assertThat(pvpEnabled).isTrue();
        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService).getTownById(townId);
    }

    @Test
    @DisplayName("Should return wilderness defaults when no town at location")
    void shouldReturnWildernessDefaultsWhenNoTownAtLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.empty());

        // When
        boolean pvpEnabled = permissionService.isPvpEnabledAtLocation(165, 320, worldName);
        boolean fireEnabled = permissionService.isFireEnabledAtLocation(165, 320, worldName);
        boolean explosionsEnabled = permissionService.areExplosionsEnabledAtLocation(165, 320, worldName);
        boolean mobsEnabled = permissionService.areMobsEnabledAtLocation(165, 320, worldName);
        boolean publicEnabled = permissionService.isPublicAccessEnabledAtLocation(165, 320, worldName);

        // Then
        assertThat(pvpEnabled).isTrue(); // Wilderness default
        assertThat(fireEnabled).isFalse(); // Wilderness default
        assertThat(explosionsEnabled).isFalse(); // Wilderness default
        assertThat(mobsEnabled).isTrue(); // Wilderness default
        assertThat(publicEnabled).isTrue(); // Wilderness default

        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService, never()).getTownById(anyString());
    }

    @Test
    @DisplayName("Should return correct fire state at town location")
    void shouldReturnCorrectFireStateAtTownLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));

        // When
        boolean fireEnabled = permissionService.isFireEnabledAtLocation(165, 320, worldName);

        // Then
        assertThat(fireEnabled).isFalse();
        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService).getTownById(townId);
    }

    @Test
    @DisplayName("Should return correct explosion state at town location")
    void shouldReturnCorrectExplosionStateAtTownLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));

        // When
        boolean explosionsEnabled = permissionService.areExplosionsEnabledAtLocation(165, 320, worldName);

        // Then
        assertThat(explosionsEnabled).isTrue();
        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService).getTownById(townId);
    }

    @Test
    @DisplayName("Should return correct mob spawning state at town location")
    void shouldReturnCorrectMobSpawningStateAtTownLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));

        // When
        boolean mobsEnabled = permissionService.areMobsEnabledAtLocation(165, 320, worldName);

        // Then
        assertThat(mobsEnabled).isFalse();
        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService).getTownById(townId);
    }

    @Test
    @DisplayName("Should return correct public access state at town location")
    void shouldReturnCorrectPublicAccessStateAtTownLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));

        // When
        boolean publicEnabled = permissionService.isPublicAccessEnabledAtLocation(165, 320, worldName);

        // Then
        assertThat(publicEnabled).isTrue();
        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService).getTownById(townId);
    }

    @Test
    @DisplayName("Should return all toggle states at town location")
    void shouldReturnAllToggleStatesAtTownLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));

        // When
        Map<String, Boolean> toggles = permissionService.getTogglesAtLocation(165, 320, worldName);

        // Then
        assertThat(toggles).hasSize(5);
        assertThat(toggles.get("pvp")).isTrue();
        assertThat(toggles.get("fire")).isFalse();
        assertThat(toggles.get("explosions")).isTrue();
        assertThat(toggles.get("mobs")).isFalse();
        assertThat(toggles.get("public")).isTrue();

        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService).getTownById(townId);
    }

    @Test
    @DisplayName("Should return empty map for toggles when no town at location")
    void shouldReturnEmptyMapForTogglesWhenNoTownAtLocation() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.empty());

        // When
        Map<String, Boolean> toggles = permissionService.getTogglesAtLocation(165, 320, worldName);

        // Then
        assertThat(toggles).isEmpty();
        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService, never()).getTownById(anyString());
    }

    @Test
    @DisplayName("Should handle coordinates conversion correctly")
    void shouldHandleCoordinatesConversionCorrectly() {
        // Given
        when(plotService.getTownBlock(0, 0, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));

        // When - Test different coordinates within the same chunk
        boolean pvpAtStart = permissionService.isPvpEnabledAtLocation(0, 0, worldName);
        boolean pvpAtEnd = permissionService.isPvpEnabledAtLocation(15, 255, worldName);
        boolean pvpInMiddle = permissionService.isPvpEnabledAtLocation(8, 128, worldName);

        // Then - All should return the same town's PvP state
        assertThat(pvpAtStart).isTrue();
        assertThat(pvpAtEnd).isTrue();
        assertThat(pvpInMiddle).isTrue();

        verify(plotService, times(3)).getTownBlock(0, 0, worldName);
        verify(townService, times(3)).getTownById(townId);
    }

    @Test
    @DisplayName("Should handle different chunks correctly")
    void shouldHandleDifferentChunksCorrectly() {
        // Given - Create a second town and town block
        Town secondTown = new Town("SecondTown", UUID.randomUUID());
        secondTown.setId("second_town_id");
        secondTown.setPvpEnabled(false);

        TownBlock secondTownBlock = new TownBlock(11, 20, worldName, "second_town_id");

        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(plotService.getTownBlock(11, 20, worldName)).thenReturn(Optional.of(secondTownBlock));
        when(townService.getTownById(townId)).thenReturn(Optional.of(testTown));
        when(townService.getTownById("second_town_id")).thenReturn(Optional.of(secondTown));

        // When - Check PvP in different chunks
        boolean pvpInFirstChunk = permissionService.isPvpEnabledAtLocation(165, 320, worldName); // chunk 10,20
        boolean pvpInSecondChunk = permissionService.isPvpEnabledAtLocation(181, 320, worldName); // chunk 11,20

        // Then - Should return different results
        assertThat(pvpInFirstChunk).isTrue(); // First town allows PvP
        assertThat(pvpInSecondChunk).isFalse(); // Second town disallows PvP

        verify(plotService).getTownBlock(10, 20, worldName);
        verify(plotService).getTownBlock(11, 20, worldName);
        verify(townService).getTownById(townId);
        verify(townService).getTownById("second_town_id");
    }

    @Test
    @DisplayName("Should handle errors gracefully when town service fails")
    void shouldHandleErrorsGracefullyWhenTownServiceFails() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenReturn(Optional.of(testTownBlock));
        when(townService.getTownById(townId)).thenThrow(new RuntimeException("Database error"));

        // When - Should not throw exception, return wilderness defaults
        boolean pvpEnabled = permissionService.isPvpEnabledAtLocation(165, 320, worldName);
        Map<String, Boolean> toggles = permissionService.getTogglesAtLocation(165, 320, worldName);

        // Then
        assertThat(pvpEnabled).isTrue(); // Wilderness default
        assertThat(toggles).isEmpty(); // Empty map on error

        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService).getTownById(townId);
    }

    @Test
    @DisplayName("Should handle errors gracefully when plot service fails")
    void shouldHandleErrorsGracefullyWhenPlotServiceFails() {
        // Given
        when(plotService.getTownBlock(10, 20, worldName)).thenThrow(new RuntimeException("Plot service error"));

        // When - Should not throw exception, return wilderness defaults
        boolean pvpEnabled = permissionService.isPvpEnabledAtLocation(165, 320, worldName);
        boolean fireEnabled = permissionService.isFireEnabledAtLocation(165, 320, worldName);

        // Then
        assertThat(pvpEnabled).isTrue(); // Wilderness default
        assertThat(fireEnabled).isFalse(); // Wilderness default

        verify(plotService).getTownBlock(10, 20, worldName);
        verify(townService, never()).getTownById(anyString());
    }
}
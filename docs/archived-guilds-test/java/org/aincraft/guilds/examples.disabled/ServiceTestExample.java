package org.aincraft.guilds.examples;

import org.aincraft.guilds.base.BaseUnitTest;
import org.aincraft.guilds.factory.TestDataBuilder;
import org.aincraft.guilds.factory.TestObjectFactory;
import org.aincraft.guilds.models.TownBlock;
import org.aincraft.guilds.services.impl.PlotServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Example Bukkit plugin service test
 * Demonstrates proper testing of service layer using Mockito and JUnit 5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceTestExample extends BaseUnitTest {

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @InjectMocks
    private PlotServiceImpl plotService;

    private UUID testResidentUuid;
    private String testWorld;
    private String testTown;

    @Override
    protected void setup() {
        testResidentUuid = TestObjectFactory.createTestUuid();
        testWorld = getDefaultWorldName();
        testTown = getDefaultTownName();

        try {
            // Setup common database mock behavior
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(preparedStatement.executeUpdate()).thenReturn(1);
        } catch (SQLException e) {
            // Should not happen with mocks
        }
    }

    @Test
    @DisplayName("Should create town block successfully")
    void shouldCreateTownBlockSuccessfully() throws SQLException {
        // Given - database setup
        when(connection.prepareStatement(contains("INSERT INTO"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When - creating town block
        TownBlock result = plotService.createTownBlock(10, 20, testWorld, testTown);

        // Then - should be created successfully
        assertThat(result).isNotNull();
        assertThat(result.getX()).isEqualTo(10);
        assertThat(result.getZ()).isEqualTo(20);
        assertThat(result.getWorld()).isEqualTo(testWorld);
        assertThat(result.getTownId()).isEqualTo(testTown);

        verify(connection).prepareStatement(contains("INSERT INTO"));
        verify(preparedStatement).setInt(1, 10);
        verify(preparedStatement).setInt(2, 20);
        verify(preparedStatement).setString(3, testWorld);
        verify(preparedStatement).setString(4, testTown);
        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("Should get existing town block")
    void shouldGetExistingTownBlock() throws SQLException {
        // Given - existing town block in database
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("world")).thenReturn(testWorld);
        when(resultSet.getString("town_id")).thenReturn(testTown);
        when(resultSet.getInt("x")).thenReturn(10);
        when(resultSet.getInt("z")).thenReturn(20);
        when(resultSet.getString("owner_id")).thenReturn(null);

        // When - getting town block
        Optional<TownBlock> result = plotService.getTownBlock(10, 20, testWorld);

        // Then - should return town block
        assertThat(result).isPresent();
        TownBlock townBlock = result.get();
        assertThat(townBlock.getX()).isEqualTo(10);
        assertThat(townBlock.getZ()).isEqualTo(20);
        assertThat(townBlock.getWorld()).isEqualTo(testWorld);
        assertThat(townBlock.getTownId()).isEqualTo(testTown);
    }

    @Test
    @DisplayName("Should return empty when town block doesn't exist")
    void shouldReturnEmptyWhenTownBlockDoesNotExist() throws SQLException {
        // Given - no town block in database
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        // When - getting non-existent town block
        Optional<TownBlock> result = plotService.getTownBlock(999, 999, testWorld);

        // Then - should return empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should check if town block exists")
    void shouldCheckIfTownBlockExists() throws SQLException {
        // Given - town block exists
        when(connection.prepareStatement(contains("SELECT COUNT(*)"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        // When - checking existence
        boolean result = plotService.townBlockExists(10, 20, testWorld);

        // Then - should return true
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("SELECT COUNT(*)"));
    }

    @Test
    @DisplayName("Should handle database errors gracefully")
    void shouldHandleDatabaseErrorsGracefully() throws SQLException {
        // Given - database error
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Connection failed"));

        // When & Then - should handle gracefully
        assertThatThrownBy(() -> plotService.getTownBlock(10, 20, testWorld))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get town block");
    }

    @Test
    @DisplayName("Should claim plot for resident successfully")
    void shouldClaimPlotForResidentSuccessfully() throws SQLException {
        // Given - resident can claim plot
        TownBlock testPlot = TestDataBuilder.aTownBlock()
                .withCoordinates(10, 20)
                .inWorld(testWorld)
                .belongingToTown(testTown)
                .build();

        when(connection.prepareStatement(contains("UPDATE"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When - claiming plot
        boolean result = plotService.claimPlotForResident(testResidentUuid, 10, 20, testWorld);

        // Then - should be claimed successfully
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("UPDATE"));
        verify(preparedStatement).setString(1, testResidentUuid.toString());
        verify(preparedStatement).setInt(2, 10);
        verify(preparedStatement).setInt(3, 20);
        verify(preparedStatement).setString(4, testWorld);
        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("Should validate resident claim permissions")
    void shouldValidateResidentClaimPermissions() throws SQLException {
        // Given - resident in test town with permissions
        when(connection.prepareStatement(contains("SELECT COUNT(*)"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1); // Resident exists in town

        // When - checking claim permission
        boolean result = plotService.canResidentClaimPlot(testResidentUuid, 10, 20, testWorld);

        // Then - should be allowed
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should update town block permissions")
    void shouldUpdateTownBlockPermissions() throws SQLException {
        // Given - existing town block
        TownBlock testPlot = TestDataBuilder.aTownBlock()
                .withCoordinates(10, 20)
                .inWorld(testWorld)
                .belongingToTown(testTown)
                .build();

        int newPermissions = 1 | 2 | 4 | 8; // BUILD, DESTROY, SWITCH, ITEM_USE

        when(connection.prepareStatement(contains("UPDATE"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When - updating permissions
        boolean result = plotService.updatePlotPermissions(testPlot, newPermissions);

        // Then - should be updated successfully
        assertThat(result).isTrue();
        assertThat(testPlot.getPermissionsFlags()).isEqualTo(newPermissions);
        verify(connection).prepareStatement(contains("UPDATE"));
        verify(preparedStatement).setInt(1, newPermissions);
        verify(preparedStatement).setInt(2, 10);
        verify(preparedStatement).setInt(3, 20);
        verify(preparedStatement).setString(4, testWorld);
    }
}
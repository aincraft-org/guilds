package org.aincraft.towny.services;

import org.aincraft.towny.TestUtilities;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Permission;
import org.aincraft.towny.models.TownBlock;
import org.aincraft.towny.services.impl.PlotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlotService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlotServiceTest {

    @Mock
    private DatabaseManager databaseManager;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private Logger logger;

    @Mock
    private org.aincraft.towny.services.TownService townService;

    private PlotServiceImpl plotService;

    private UUID testPlotId = UUID.randomUUID();
    private UUID testResidentId = UUID.randomUUID();
    private String testWorld = "test_world";
    private int testX = 10;
    private int testZ = 20;

    @BeforeEach
    void setUp() throws SQLException {
        // Setup mock dependencies
        when(databaseManager.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(preparedStatement.getGeneratedKeys()).thenReturn(resultSet);

        // Mock town ID query for createTownBlock
        when(resultSet.getString("id")).thenReturn("test-town-id");

        // Manually create the service with mocked dependencies
        plotService = new PlotServiceImpl(databaseManager, townService, logger);
    }

    @Test
    @DisplayName("Should create town block successfully")
    void shouldCreateTownBlockSuccessfully() throws SQLException {
        // Given
        String townName = "test_town";
        when(resultSet.next()).thenReturn(true); // Town exists
        when(resultSet.getString("id")).thenReturn("test-town-id"); // Return town ID
        when(preparedStatement.getGeneratedKeys()).thenReturn(resultSet);
        when(resultSet.getString(1)).thenReturn(testPlotId.toString());

        // When
        TownBlock townBlock = plotService.createTownBlock(testX, testZ, testWorld, townName);

        // Then
        assertThat(townBlock).isNotNull();
        assertThat(townBlock.getX()).isEqualTo(testX);
        assertThat(townBlock.getZ()).isEqualTo(testZ);
        assertThat(townBlock.getWorld()).isEqualTo(testWorld);
        assertThat(townBlock.getTownId()).isEqualTo(townName);
        assertThat(townBlock.getPlotType()).isEqualTo(TownBlock.PlotType.DEFAULT);
        assertThat(townBlock.isTownOwned()).isTrue();

        verify(connection).prepareStatement(contains("INSERT INTO town_blocks"));
        verify(preparedStatement).setInt(1, testPlotId.toString().length());
        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("Should get town block by coordinates")
    void shouldGetTownBlockByCoordinates() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("id")).thenReturn(testPlotId.toString());
        when(resultSet.getInt("x")).thenReturn(testX);
        when(resultSet.getInt("z")).thenReturn(testZ);
        when(resultSet.getString("world")).thenReturn(testWorld);
        when(resultSet.getString("town_id")).thenReturn("test_town");
        when(resultSet.getString("plot_type")).thenReturn(TownBlock.PlotType.DEFAULT);
        when(resultSet.getDouble("price")).thenReturn(0.0);
        when(resultSet.getInt("permissions_flags")).thenReturn(Permission.Flag.DEFAULT_PLOT);
        when(resultSet.getString("claimed_at")).thenReturn("2024-01-01 00:00:00");
        when(resultSet.getString("custom_name")).thenReturn(null);
        when(resultSet.getString("owner_uuid")).thenReturn(null);

        // When
        Optional<TownBlock> result = plotService.getTownBlock(testX, testZ, testWorld);

        // Then
        assertThat(result).isPresent();
        TownBlock townBlock = result.get();
        assertThat(townBlock.getX()).isEqualTo(testX);
        assertThat(townBlock.getZ()).isEqualTo(testZ);
        assertThat(townBlock.getWorld()).isEqualTo(testWorld);

        verify(connection).prepareStatement(contains("SELECT id, x, z, world"));
        verify(preparedStatement).setInt(1, testX);
        verify(preparedStatement).setInt(2, testZ);
        verify(preparedStatement).setString(3, testWorld);
    }

    @Test
    @DisplayName("Should return empty when town block not found")
    void shouldReturnEmptyWhenTownBlockNotFound() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(false);

        // When
        Optional<TownBlock> result = plotService.getTownBlock(testX, testZ, testWorld);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should check if town block exists")
    void shouldCheckIfTownBlockExists() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true); // Must return true first
        when(resultSet.getInt(1)).thenReturn(1);

        // When
        boolean exists = plotService.townBlockExists(testX, testZ, testWorld);

        // Then
        assertThat(exists).isTrue();
        verify(connection).prepareStatement(contains("SELECT COUNT(*) FROM town_blocks"));
    }

    @Test
    @DisplayName("Should claim town block successfully")
    void shouldClaimTownBlockSuccessfully() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(false); // Town doesn't exist initially
        when(resultSet.getInt(1)).thenReturn(0); // Town block doesn't exist

        // When
        boolean result = plotService.claimTownBlock(testX, testZ, testWorld, "test_town");

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("INSERT INTO town_blocks"));
    }

    @Test
    @DisplayName("Should fail to claim existing town block")
    void shouldFailToClaimExistingTownBlock() throws SQLException {
        // Given
        when(resultSet.getInt(1)).thenReturn(1); // Town block already exists

        // When
        boolean result = plotService.claimTownBlock(testX, testZ, testWorld, "test_town");

        // Then
        assertThat(result).isFalse();
        verify(connection, never()).prepareStatement(contains("INSERT INTO town_blocks"));
    }

    @Test
    @DisplayName("Should unclaim town block successfully")
    void shouldUnclaimTownBlockSuccessfully() throws SQLException {
        // Given
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When
        boolean result = plotService.unclaimTownBlock(testX, testZ, testWorld);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("DELETE FROM town_blocks"));
        verify(preparedStatement).setInt(1, testX);
        verify(preparedStatement).setInt(2, testZ);
        verify(preparedStatement).setString(3, testWorld);
    }

    @Test
    @DisplayName("Should set town block owner successfully")
    void shouldSetTownBlockOwnerSuccessfully() throws SQLException {
        // When
        boolean result = plotService.setTownBlockOwner(testPlotId, testResidentId);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("UPDATE town_blocks SET owner_uuid = ?"));
        verify(preparedStatement).setString(1, testResidentId.toString());
        verify(preparedStatement).setString(2, testPlotId.toString());
    }

    @Test
    @DisplayName("Should set town block owner to null successfully")
    void shouldSetTownBlockOwnerToNullSuccessfully() throws SQLException {
        // When
        boolean result = plotService.setTownBlockOwner(testPlotId, null);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("UPDATE town_blocks SET owner_uuid = ?"));
        verify(preparedStatement).setNull(1, java.sql.Types.VARCHAR);
    }

    @Test
    @DisplayName("Should get town blocks in radius")
    void shouldGetTownBlocksInRadius() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true, false); // One result, then end
        mockTownBlockResultSet();

        // When
        List<TownBlock> result = plotService.getTownBlocksInRadius(testX, testZ, 2, testWorld);

        // Then
        assertThat(result).hasSize(1);
        TownBlock townBlock = result.get(0);
        assertThat(townBlock.getX()).isEqualTo(testX);
        assertThat(townBlock.getZ()).isEqualTo(testZ);
        assertThat(townBlock.getWorld()).isEqualTo(testWorld);

        verify(connection).prepareStatement(contains("SELECT id, x, z, world"));
        verify(preparedStatement).setString(1, testWorld);
        verify(preparedStatement).setInt(2, testX - 2);
        verify(preparedStatement).setInt(3, testX + 2);
        verify(preparedStatement).setInt(4, testZ - 2);
        verify(preparedStatement).setInt(5, testZ + 2);
    }

    @Test
    @DisplayName("Should claim plot for resident successfully")
    void shouldClaimPlotForResidentSuccessfully() throws SQLException {
        // Given
        when(resultSet.getInt(1)).thenReturn(0); // Town block doesn't exist
        when(resultSet.getString(1)).thenReturn("TestPlayer"); // Resident exists
        when(resultSet.getString(2)).thenReturn("test_town"); // Resident in town
        when(resultSet.next()).thenReturn(true, true, true, false); // Resident exists, in town, town exists

        // When
        boolean result = plotService.claimPlotForResident(testResidentId, testX, testZ, testWorld);

        // Then
        assertThat(result).isTrue();
        verify(connection, times(4)).prepareStatement(anyString()); // Multiple queries
    }

    @Test
    @DisplayName("Should buy plot successfully")
    void shouldBuyPlotSuccessfully() throws SQLException {
        // Given
        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setPrice(1000.0);
        testPlot.setOwnerId(null); // No owner

        when(resultSet.next()).thenReturn(true);
        mockTownBlockResultSet();
        when(resultSet.getDouble("price")).thenReturn(1000.0);
        when(resultSet.getString("owner_uuid")).thenReturn(null);

        // When
        boolean result = plotService.buyPlot(testResidentId, testPlotId, 1000.0);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("UPDATE town_blocks"));
    }

    @Test
    @DisplayName("Should fail to buy plot with wrong price")
    void shouldFailToBuyPlotWithWrongPrice() throws SQLException {
        // Given
        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setPrice(1000.0);

        when(resultSet.next()).thenReturn(true);
        mockTownBlockResultSet();
        when(resultSet.getDouble("price")).thenReturn(1000.0);

        // When
        boolean result = plotService.buyPlot(testResidentId, testPlotId, 500.0); // Wrong price

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should set plot for sale successfully")
    void shouldSetPlotForSaleSuccessfully() throws SQLException {
        // Given
        TownBlock testPlot = TestUtilities.createTestTownBlock();
        testPlot.setOwnerId(testResidentId);

        when(resultSet.next()).thenReturn(true);
        mockTownBlockResultSet();
        when(resultSet.getString("owner_uuid")).thenReturn(testResidentId.toString());

        // When
        boolean result = plotService.setPlotForSale(testPlotId, 1500.0, testResidentId);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("UPDATE town_blocks"));
    }

    @Test
    @DisplayName("Should fail to set plot for sale for non-owner")
    void shouldFailToSetPlotForSaleForNonOwner() throws SQLException {
        // Given
        UUID differentOwnerId = UUID.randomUUID();

        when(resultSet.next()).thenReturn(true);
        mockTownBlockResultSet();
        when(resultSet.getString("owner_uuid")).thenReturn(differentOwnerId.toString());

        // When
        boolean result = plotService.setPlotForSale(testPlotId, 1500.0, testResidentId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should set plot permission flag successfully")
    void shouldSetPlotPermissionFlagSuccessfully() throws SQLException {
        // Given
        when(resultSet.next()).thenReturn(true);
        mockTownBlockResultSet();

        // When
        boolean result = plotService.setPlotPermissionFlag(testPlotId, Permission.Flag.BUILD, true);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("UPDATE town_blocks"));
    }

    @Test
    @DisplayName("Should set multiple plot permission flags successfully")
    void shouldSetMultiplePlotPermissionFlagsSuccessfully() throws SQLException {
        // Given
        int testFlags = Permission.Flag.BUILD | Permission.Flag.DESTROY;
        when(resultSet.next()).thenReturn(true);
        mockTownBlockResultSet();

        // When
        boolean result = plotService.setPlotPermissionFlags(testPlotId, testFlags);

        // Then
        assertThat(result).isTrue();
        verify(connection).prepareStatement(contains("UPDATE town_blocks"));
    }

    @Test
    @DisplayName("Should handle database errors gracefully")
    void shouldHandleDatabaseErrorsGracefully() throws SQLException {
        // Given
        when(dataSource.getConnection()).thenThrow(new SQLException("Database error"));

        // When & Then
        assertThatThrownBy(() -> plotService.getTownBlock(testX, testZ, testWorld))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get town block");

        assertThatThrownBy(() -> plotService.createTownBlock(testX, testZ, testWorld, "test_town"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create town block");
    }

    @Test
    @DisplayName("Should get town blocks in chunk correctly")
    void shouldGetTownBlocksInChunkCorrectly() throws SQLException {
        // Given
        int chunkX = 0;
        int chunkZ = 0;
        int blockX = chunkX << 4; // 0
        int blockZ = chunkZ << 4; // 0

        when(resultSet.next()).thenReturn(true, false);
        mockTownBlockResultSet();
        when(resultSet.getInt("x")).thenReturn(blockX);
        when(resultSet.getInt("z")).thenReturn(blockZ);

        // When
        List<TownBlock> result = plotService.getTownBlocksInChunk(chunkX, chunkZ, testWorld);

        // Then
        assertThat(result).hasSize(1);
        verify(connection).prepareStatement(contains("SELECT id, x, z, world"));
        verify(preparedStatement).setString(1, testWorld); // First parameter is world string
        verify(preparedStatement).setInt(2, blockX);       // Second parameter is blockX
        verify(preparedStatement).setInt(3, blockX + 16);  // Third parameter is blockX + 16
        verify(preparedStatement).setInt(4, blockZ);       // Fourth parameter is blockZ
        verify(preparedStatement).setInt(5, blockZ + 16);  // Fifth parameter is blockZ + 16
    }

    /**
     * Helper method to mock town block result set
     */
    private void mockTownBlockResultSet() throws SQLException {
        when(resultSet.getString("id")).thenReturn(testPlotId.toString());
        when(resultSet.getInt("x")).thenReturn(testX);
        when(resultSet.getInt("z")).thenReturn(testZ);
        when(resultSet.getString("world")).thenReturn(testWorld);
        when(resultSet.getString("town_id")).thenReturn("test_town");
        when(resultSet.getString("plot_type")).thenReturn(TownBlock.PlotType.DEFAULT);
        when(resultSet.getDouble("price")).thenReturn(0.0);
        when(resultSet.getInt("permissions_flags")).thenReturn(Permission.Flag.DEFAULT_PLOT);
        when(resultSet.getString("claimed_at")).thenReturn("2024-01-01 00:00:00");
        when(resultSet.getString("custom_name")).thenReturn(null);
        when(resultSet.getString("owner_uuid")).thenReturn(null);
    }
}
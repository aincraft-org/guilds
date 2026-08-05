package org.aincraft.towny.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.aincraft.towny.base.BaseUnitTest;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.impl.EconomyServiceImpl;
import org.aincraft.towny.utils.TestDatabaseHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EconomyServiceImpl
 * Tests functionality related to economy operations for towns and players
 */
@ExtendWith(MockitoExtension.class)
class EconomyServiceImplTest extends BaseUnitTest {

    private EconomyService service;
    private DatabaseManager databaseManager;
    private DataSource testDataSource;

    @Mock
    private TownService townService;

    @BeforeEach
    @Override
    protected void setup() {
        super.setup();

        // Setup test database
        databaseManager = mock(DatabaseManager.class);
        testDataSource = TestDatabaseHelper.createTestDatabase();
        when(databaseManager.getConnection()).thenAnswer(invocation -> testDataSource.getConnection());

        // Setup service without vault (fallback mode)
        service = new EconomyServiceImpl(null, databaseManager, townService);
        
        // Verify vault is not available in test environment
        assertThat(service.isAvailable()).isFalse();
    }

    @AfterEach
    @Override
    protected void cleanup() {
        try (Connection conn = testDataSource.getConnection()) {
            TestDatabaseHelper.cleanupTestData(conn, testConfig);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        super.cleanup();
    }

    @Test
    @DisplayName("Should return 0 balance for non-existent town")
    void shouldReturnZeroBalanceForNonExistentTown() {
        // Given
        String townId = "non-existent";
        when(townService.getTownById(townId)).thenReturn(Optional.empty());

        // When
        double balance = service.getTownBalance(townId);

        // Then
        assertThat(balance).isZero();
    }

    @Test
    @DisplayName("Should return town balance when town exists")
    void shouldReturnTownBalanceWhenTownExists() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(1000.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        double balance = service.getTownBalance(townId);

        // Then
        assertThat(balance).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("Should deposit funds successfully")
    void shouldDepositFundsSuccessfully() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(100.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        service.depositTown(townId, 200.0);

        // Then
        assertThat(town.getBalance()).isEqualTo(300.0);
        verify(townService, times(1)).updateTown(town);
    }

    @Test
    @DisplayName("Should withdraw funds successfully")
    void shouldWithdrawFundsSuccessfully() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(500.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        service.withdrawTown(townId, 200.0);

        // Then
        assertThat(town.getBalance()).isEqualTo(300.0);
        verify(townService, times(1)).updateTown(town);
    }

    @Test
    @DisplayName("Should handle insufficient funds for withdrawal")
    void shouldHandleInsufficientFundsForWithdrawal() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(100.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        service.withdrawTown(townId, 200.0);

        // Then
        assertThat(town.getBalance()).isEqualTo(100.0); // No change
        verify(townService, never()).updateTown(town);
    }

    @Test
    @DisplayName("Should handle negative deposit amounts")
    void shouldHandleNegativeDepositAmounts() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(100.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        service.depositTown(townId, -50.0);

        // Then
        assertThat(town.getBalance()).isEqualTo(100.0); // No change
        verify(townService, never()).updateTown(town);
    }

    @Test
    @DisplayName("Should handle zero deposit amounts")
    void shouldHandleZeroDepositAmounts() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(100.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        service.depositTown(townId, 0.0);

        // Then
        assertThat(town.getBalance()).isEqualTo(100.0); // No change
        verify(townService, never()).updateTown(town);
    }

    @Test
    @DisplayName("Should handle negative withdrawal amounts")
    void shouldHandleNegativeWithdrawalAmounts() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(100.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        service.withdrawTown(townId, -50.0);

        // Then
        assertThat(town.getBalance()).isEqualTo(100.0); // No change
        verify(townService, never()).updateTown(town);
    }

    @Test
    @DisplayName("Should handle zero withdrawal amounts")
    void shouldHandleZeroWithdrawalAmounts() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(100.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When
        service.withdrawTown(townId, 0.0);

        // Then
        assertThat(town.getBalance()).isEqualTo(100.0); // No change
        verify(townService, never()).updateTown(town);
    }

    @Test
    @DisplayName("Should check town has sufficient funds")
    void shouldCheckTownHasSufficientFunds() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.addFunds(1000.0);
        when(townService.getTownById(townId)).thenReturn(Optional.of(town));

        // When & Then
        assertThat(service.townHas(townId, 500.0)).isTrue();
        assertThat(service.townHas(townId, 1500.0)).isFalse();
    }

    @Test
    @DisplayName("Should get player balance")
    void shouldGetPlayerBalance() {
        // Given
        UUID playerId = UUID.randomUUID();

        // When
        double balance = service.getPlayerBalance(playerId);

        // Then
        assertThat(balance).isZero(); // Default when vault unavailable
    }

    @Test
    @DisplayName("Should handle negative amounts in player operations")
    void shouldHandleNegativeAmountsInPlayerOperations() {
        // Given
        UUID playerId = UUID.randomUUID();

        // When
        service.depositPlayer(playerId, -100.0);
        service.withdrawPlayer(playerId, -50.0);

        // Then
        assertThat(service.has(playerId, 100.0)).isFalse();
    }

    @Test
    @DisplayName("Should format money correctly")
    void shouldFormatMoneyCorrectly() {
        // When
        String formatted = service.format(123.45);

        // Then
        assertThat(formatted).isEqualTo("$123.45");
    }
}
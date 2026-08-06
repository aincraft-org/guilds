package org.aincraft.guilds.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.aincraft.guilds.base.BaseUnitTest;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.models.TownSpecialization;
import org.aincraft.guilds.services.impl.SpecializationServiceImpl;
import org.aincraft.guilds.utils.TestDatabaseHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SpecializationServiceImpl
 * Tests functionality related to town specialization management
 */
@ExtendWith(MockitoExtension.class)
class SpecializationServiceImplTest extends BaseUnitTest {

    private SpecializationService service;
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

        service = new SpecializationServiceImpl(null, databaseManager, townService);
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
    @DisplayName("Should set specialization successfully")
    void shouldSetSpecializationSuccessfully() {
        // Given
        String townId = "test-town";
        TownSpecialization specialization = TownSpecialization.TRADING;

        // When
        service.setSpecialization(townId, specialization);

        // Then
        Optional<TownSpecialization> storedSpec = service.getSpecialization(townId);
        assertThat(storedSpec).isPresent();
        assertThat(storedSpec.get()).isEqualTo(specialization);
    }

    @Test
    @DisplayName("Should get specialization for town")
    void shouldGetSpecializationForTown() {
        // Given
        String townId = "test-town";
        TownSpecialization specialization = TownSpecialization.MINING;
        service.setSpecialization(townId, specialization);

        // When
        Optional<TownSpecialization> retrieved = service.getSpecialization(townId);

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo(specialization);
    }

    @Test
    @DisplayName("Should return empty optional for non-existent town specialization")
    void shouldReturnEmptyOptionalForNonExistentTownSpecialization() {
        // Given
        String townId = "non-existent-town";

        // When
        Optional<TownSpecialization> specialization = service.getSpecialization(townId);

        // Then
        assertThat(specialization).isEmpty();
    }

    @Test
    @DisplayName("Should remove specialization successfully")
    void shouldRemoveSpecializationSuccessfully() {
        // Given
        String townId = "test-town";
        TownSpecialization specialization = TownSpecialization.FARMING;
        service.setSpecialization(townId, specialization);
        assertThat(service.getSpecialization(townId)).isPresent();

        // When
        service.removeSpecialization(townId);

        // Then
        Optional<TownSpecialization> retrieved = service.getSpecialization(townId);
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Should check if town can specialize")
    void shouldCheckIfTownCanSpecialize() {
        // Given
        String townIdLowLevel = "low-level-town";
        String townIdHighLevel = "high-level-town";
        Town lowLevelTown = new Town(townIdLowLevel, "Low Level Town");
        lowLevelTown.setLevel(5);
        Town highLevelTown = new Town(townIdHighLevel, "High Level Town");
        highLevelTown.setLevel(15);

        when(townService.getTown(townIdLowLevel)).thenReturn(Optional.of(lowLevelTown));
        when(townService.getTown(townIdHighLevel)).thenReturn(Optional.of(highLevelTown));

        // When
        boolean canSpecializeLow = service.canSpecialize(townIdLowLevel);
        boolean canSpecializeHigh = service.canSpecialize(townIdHighLevel);

        // Then
        assertThat(canSpecializeLow).isFalse();
        assertThat(canSpecializeHigh).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-existent town when checking specialization")
    void shouldReturnFalseForNonExistentTownWhenCheckingSpecialization() {
        // Given
        String townId = "non-existent-town";
        when(townService.getTown(townId)).thenReturn(Optional.empty());

        // When
        boolean canSpecialize = service.canSpecialize(townId);

        // Then
        assertThat(canSpecialize).isFalse();
    }

    @Test
    @DisplayName("should get available specializations for town")
    void shouldGetAvailableSpecializationsForTown() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.setLevel(5);
        when(townService.getTown(townId)).thenReturn(Optional.of(town));

        // When
        List<TownSpecialization> available = service.getAvailableSpecializations(townId);

        // Then
        assertThat(available).isNotEmpty();
        // All returned specializations should require level <= 5
        assertThat(available).allMatch(spec -> spec.getRequiredLevel() <= 5);
    }

    @Test
    @DisplayName("should get higher level specializations for high level town")
    void shouldGetHigherLevelSpecializationsForHighLevelTown() {
        // Given
        String townId = "test-town";
        Town town = new Town(townId, "Test Town");
        town.setLevel(20);
        when(townService.getTown(townId)).thenReturn(Optional.of(town));

        // When
        List<TownSpecialization> available = service.getAvailableSpecializations(townId);

        // Then
        assertThat(available).isNotEmpty();
    }

    @Test
    @DisplayName("should get empty list for non-existent town")
    void shouldGetEmptyListForNonExistentTown() {
        // Given
        String townId = "non-existent-town";
        when(townService.getTown(townId)).thenReturn(Optional.empty());

        // When
        List<TownSpecialization> available = service.getAvailableSpecializations(townId);

        // Then
        assertThat(available).isEmpty();
    }

    @Test
    @DisplayName("should convert string to specialization")
    void shouldConvertStringToSpecialization() {
        // Given
        String validSpec = "TRADING";
        String invalidSpec = "INVALID";

        // When
        TownSpecialization validResult = service.fromString(validSpec);
        TownSpecialization invalidResult = service.fromString(invalidSpec);

        // Then
        assertThat(validResult).isEqualTo(TownSpecialization.TRADING);
        assertThat(invalidResult).isNull();
    }
}
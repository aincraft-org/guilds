package org.aincraft.guilds.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.aincraft.guilds.base.BaseUnitTest;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Nation;
import org.aincraft.guilds.models.Town;
import org.aincraft.guilds.services.impl.NationServiceImpl;
import org.aincraft.guilds.utils.TestDatabaseHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NationServiceImpl
 * Tests functionality related to nation management
 */
@ExtendWith(MockitoExtension.class)
class NationServiceImplTest extends BaseUnitTest {

    private NationService service;
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
        when(databaseManager.getDataSource()).thenReturn(testDataSource);
        when(databaseManager.getConnection()).thenAnswer(invocation -> testDataSource.getConnection());

        service = new NationServiceImpl(null, databaseManager, null, townService);
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
    @DisplayName("Should create nation successfully")
    void shouldCreateNationSuccessfully() {
        // Given
        String nationName = "TestNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        when(townService.getTownById(capitalTown.getId())).thenReturn(Optional.of(capitalTown));

        // When
        service.createNation(nationName, capitalTown, kingUuid);

        // Then
        Optional<Nation> nationOpt = service.getNation(nationName);
        assertThat(nationOpt).isPresent();
        Nation nation = nationOpt.get();
        assertThat(nation.getName()).isEqualTo(nationName);
        assertThat(nation.getKingUuid()).isEqualTo(kingUuid);
        assertThat(nation.getCapitalTownId()).isEqualTo(capitalTown.getId());
    }

    @Test
    @DisplayName("Should throw exception when creating duplicate nation")
    void shouldThrowExceptionWhenCreatingDuplicateNation() {
        // Given
        String nationName = "ExistingNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();

        // Create nation first
        service.createNation(nationName, capitalTown, kingUuid);

        // When & Then
        assertThatThrownBy(() -> service.createNation(nationName, capitalTown, kingUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Nation already exists: " + nationName);
    }

    @Test
    @DisplayName("Should get nation by name")
    void shouldGetNationByName() {
        // Given
        String nationName = "TestNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        // When
        Optional<Nation> nation = service.getNation(nationName);

        // Then
        assertThat(nation).isPresent();
        assertThat(nation.get().getName()).isEqualTo(nationName);
    }

    @Test
    @DisplayName("Should return empty optional for non-existent nation")
    void shouldReturnEmptyOptionalForNonExistentNation() {
        // When
        Optional<Nation> nation = service.getNation("NonExistent");

        // Then
        assertThat(nation).isEmpty();
    }

    @Test
    @DisplayName("Should get nation by ID")
    void shouldGetNationById() {
        // Given
        String nationName = "TestNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        Optional<Nation> nationOpt = service.getNation(nationName);
        String nationId = nationOpt.get().getId();

        // When
        Optional<Nation> nationById = service.getNationById(nationId);

        // Then
        assertThat(nationById).isPresent();
        assertThat(nationById.get().getId()).isEqualTo(nationId);
    }

    @Test
    @DisplayName("Should delete nation successfully")
    void shouldDeleteNationSuccessfully() {
        // Given
        String nationName = "TestNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        assertThat(service.getNation(nationName)).isPresent();

        // When
        service.deleteNation(nationName);

        // Then
        assertThat(service.getNation(nationName)).isEmpty();
    }

    @Test
    @DisplayName("Should handle deletion of non-existent nation")
    void shouldHandleDeletionOfNonExistentNation() {
        // When & Then
        assertThatCode(() -> service.deleteNation("NonExistent"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should add ally successfully")
    void shouldAddAllySuccessfully() {
        // Given
        String nationName = "TestNation";
        String allyName = "AllyNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        Optional<Nation> nationOpt = service.getNation(nationName);
        Nation nation = nationOpt.get();

        // When
        service.addAlly(nation, allyName);

        // Then
        assertThat(nation.getAllies()).contains(allyName);
    }

    @Test
    @DisplayName("Should remove ally successfully")
    void shouldRemoveAllySuccessfully() {
        // Given
        String nationName = "TestNation";
        String allyName = "AllyNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        Optional<Nation> nationOpt = service.getNation(nationName);
        Nation nation = nationOpt.get();
        service.addAlly(nation, allyName);
        assertThat(nation.getAllies()).contains(allyName);

        // When
        service.removeAlly(nation, allyName);

        // Then
        assertThat(nation.getAllies()).doesNotContain(allyName);
    }

    @Test
    @DisplayName("Should add enemy successfully")
    void shouldAddEnemySuccessfully() {
        // Given
        String nationName = "TestNation";
        String enemyName = "EnemyNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        Optional<Nation> nationOpt = service.getNation(nationName);
        Nation nation = nationOpt.get();

        // When
        service.addEnemy(nation, enemyName);

        // Then
        assertThat(nation.getEnemies()).contains(enemyName);
    }

    @Test
    @DisplayName("Should remove enemy successfully")
    void shouldRemoveEnemySuccessfully() {
        // Given
        String nationName = "TestNation";
        String enemyName = "EnemyNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        Optional<Nation> nationOpt = service.getNation(nationName);
        Nation nation = nationOpt.get();
        service.addEnemy(nation, enemyName);
        assertThat(nation.getEnemies()).contains(enemyName);

        // When
        service.removeEnemy(nation, enemyName);

        // Then
        assertThat(nation.getEnemies()).doesNotContain(enemyName);
    }

    @Test
    @DisplayName("Should add enemy removes existing ally relationship")
    void shouldAddEnemyRemovesExistingAllyRelationship() {
        // Given
        String nationName = "TestNation";
        String otherNation = "OtherNation";
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        Optional<Nation> nationOpt = service.getNation(nationName);
        Nation nation = nationOpt.get();
        service.addAlly(nation, otherNation);
        assertThat(nation.getAllies()).contains(otherNation);

        // When
        service.addEnemy(nation, otherNation);

        // Then
        assertThat(nation.getAllies()).doesNotContain(otherNation);
        assertThat(nation.getEnemies()).contains(otherNation);
    }

    @Test
    @DisplayName("Should set tax rate successfully")
    void shouldSetTaxRateSuccessfully() {
        // Given
        String nationName = "TestNation";
        double newTaxRate = 0.15;
        Town capitalTown = new Town("capital-town", "Capital Town");
        UUID kingUuid = UUID.randomUUID();
        service.createNation(nationName, capitalTown, kingUuid);

        Optional<Nation> nationOpt = service.getNation(nationName);
        Nation nation = nationOpt.get();

        // When
        service.setTaxRate(nation, newTaxRate);

        // Then
        assertThat(nation.getTaxRate()).isEqualTo(newTaxRate);
    }

    @Test
    @DisplayName("should get all nations")
    void shouldGetAllNations() {
        // Given
        Town capitalTown1 = new Town("capital-town-1", "Capital Town 1");
        Town capitalTown2 = new Town("capital-town-2", "Capital Town 2");
        UUID kingUuid = UUID.randomUUID();
        service.createNation("Nation1", capitalTown1, kingUuid);
        service.createNation("Nation2", capitalTown2, kingUuid);

        // When
        List<Nation> nations = service.getAllNations();

        // Then
        assertThat(nations).hasSize(2);
        assertThat(nations).extracting("name").contains("Nation1", "Nation2");
    }
}
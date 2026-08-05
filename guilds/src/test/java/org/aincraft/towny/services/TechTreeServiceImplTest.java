package org.aincraft.towny.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.aincraft.towny.base.BaseUnitTest;
import org.aincraft.towny.config.TechTreeConfigLoader;
import org.aincraft.towny.database.DatabaseManager;
import org.aincraft.towny.models.TechTreeBranch;
import org.aincraft.towny.models.TechTreeNode;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.impl.TechTreeServiceImpl;
import org.aincraft.towny.utils.TestDatabaseHelper;
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
 * Unit tests for TechTreeServiceImpl
 * Tests functionality related to technology tree management in towns
 */
@ExtendWith(MockitoExtension.class)
class TechTreeServiceImplTest extends BaseUnitTest {

    private TechTreeService service;
    private DatabaseManager databaseManager;
    private DataSource testDataSource;
    
    @Mock
    private TechTreeConfigLoader configLoader;
    
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
        
        // Setup config loader mock
        when(configLoader.getNodes()).thenReturn(Arrays.asList(
            new TechTreeNode("node1", "Basic Tech", TechTreeBranch.INFRASTRUCTURE, 100, Arrays.asList(), null, 0, 0),
            new TechTreeNode("node2", "Advanced Tech", TechTreeBranch.INFRASTRUCTURE, 200, Arrays.asList("node1"), null, 1, 0),
            new TechTreeNode("node3", "Military Tech", TechTreeBranch.MILITARY, 150, Arrays.asList(), null, 0, 1)
        ));
        
        service = new TechTreeServiceImpl(null, databaseManager, configLoader, townService);
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
    @DisplayName("Should return all nodes when getAllNodes is called")
    void shouldReturnAllNodesWhenGetAllNodesCalled() {
        // When
        List<TechTreeNode> nodes = service.getAllNodes();
        
        // Then
        assertThat(nodes).isNotNull().hasSize(3);
        assertThat(nodes).extracting("id").containsExactlyInAnyOrder("node1", "node2", "node3");
    }

    @Test
    @DisplayName("Should return specific node by ID when getNode is called")
    void shouldReturnSpecificNodeByIdWhenGetNodeCalled() {
        // When
        Optional<TechTreeNode> node = service.getNode("node1");
        
        // Then
        assertThat(node).isPresent();
        assertThat(node.get().getId()).isEqualTo("node1");
        assertThat(node.get().getName()).isEqualTo("Basic Tech");
    }

    @Test
    @DisplayName("Should return empty optional when getNode for non-existent ID")
    void shouldReturnEmptyOptionalWhenGetNodeForNonExistentId() {
        // When
        Optional<TechTreeNode> node = service.getNode("nonexistent");
        
        // Then
        assertThat(node).isEmpty();
    }

    @Test
    @DisplayName("Should check if tech node is unlocked for town")
    void shouldCheckIfTechNodeIsUnlockedForTown() {
        // Given
        Town town = new Town("town1", "Town1");
        town.unlockTechNode("node1");
        
        // When
        boolean unlocked = service.isTechNodeUnlocked(town, "node1");
        boolean notUnlocked = service.isTechNodeUnlocked(town, "node2");
        
        // Then
        assertThat(unlocked).isTrue();
        assertThat(notUnlocked).isFalse();
    }

    @Test
    @DisplayName("Should determine if town can unlock a node")
    void shouldDetermineIfTownCanUnlockNode() {
        // Given
        Town town = new Town("town1", "Town1");
        town.setTechPoints(200);
        town.unlockTechNode("node1");
        
        // When - test unlocking node2 (has prerequisite node1)
        boolean canUnlockWithPrereq = service.canUnlockNode(town, "node2");
        
        // When - test unlocking node3 (no prerequisites)
        boolean canUnlockNoPrereq = service.canUnlockNode(town, "node3");
        
        // When - test unlocking already unlocked node
        boolean canUnlockAlreadyUnlocked = service.canUnlockNode(town, "node1");
        
        // When - test with insufficient points
        town.setTechPoints(50);
        boolean canUnlockInsufficient = service.canUnlockNode(town, "node3");
        
        // Then
        assertThat(canUnlockWithPrereq).isTrue();
        assertThat(canUnlockNoPrereq).isTrue();
        assertThat(canUnlockAlreadyUnlocked).isFalse();
        assertThat(canUnlockInsufficient).isFalse();
    }

    @Test
    @DisplayName("Should get available nodes for town")
    void shouldGetAvailableNodesForTown() {
        // Given
        Town town = new Town("town1", "Town1");
        town.setTechPoints(300);
        
        // When
        List<TechTreeNode> available = service.getAvailableNodes(town);
        
        // Then - should get both node1 and node3 (node2 requires node1)
        assertThat(available).isNotNull().hasSize(2);
        assertThat(available).extracting("id").containsExactlyInAnyOrder("node1", "node3");
        
        // When - unlock node1
        town.unlockTechNode("node1");
        town.setTechPoints(200);
        
        // Then
        available = service.getAvailableNodes(town);
        assertThat(available).hasSize(1);
        assertThat(available.get(0).getId()).isEqualTo("node2");
    }

    @Test
    @DisplayName("Should get unlocked nodes for town")
    void shouldGetUnlockedNodesForTown() {
        // Given
        Town town = new Town("town1", "Town1");
        town.unlockTechNode("node1");
        town.unlockTechNode("node3");
        
        // When
        List<String> unlocked = service.getUnlockedNodes(town);
        
        // Then
        assertThat(unlocked).containsExactlyInAnyOrder("node1", "node3");
    }

    @Test
    @DisplayName("Should unlock tech node successfully")
    void shouldUnlockTechNodeSuccessfully() {
        // Given
        Town town = new Town("town1", "Town1");
        town.setTechPoints(200);
        town.unlockTechNode("node1");
        
        // When
        boolean result = service.unlockTechNode(town, "node2");
        
        // Then
        assertThat(result).isTrue();
        assertThat(service.isTechNodeUnlocked(town, "node2")).isTrue();
        assertThat(town.getTechPoints()).isEqualTo(0); // 200 - 200 cost = 0
        
        // Verify town service was called to update the town
        verify(townService, times(1)).updateTown(town);
    }

    @Test
    @DisplayName("Should fail to unlock node when requirements not met")
    void shouldFailToUnlockNodeWhenRequirementsNotMet() {
        // Given
        Town town = new Town("town1", "Town1");
        town.setTechPoints(50);
        
        // When
        boolean result = service.unlockTechNode(town, "node1");
        
        // Then
        assertThat(result).isFalse();
        assertThat(service.isTechNodeUnlocked(town, "node1")).isFalse();
        assertThat(town.getTechPoints()).isEqualTo(50); // No change
    }
}
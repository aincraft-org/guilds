package org.aincraft.guilds.services;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.aincraft.guilds.base.BaseUnitTest;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.TownQuest;
import org.aincraft.guilds.models.TownQuestType;
import org.aincraft.guilds.services.impl.QuestServiceImpl;
import org.aincraft.guilds.utils.TestDatabaseHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QuestServiceImpl
 * Tests functionality related to town quest management
 */
@ExtendWith(MockitoExtension.class)
class QuestServiceImplTest extends BaseUnitTest {

    private QuestService service;
    private DatabaseManager databaseManager;
    private DataSource testDataSource;

    @BeforeEach
    @Override
    protected void setup() {
        super.setup();

        // Setup test database
        databaseManager = mock(DatabaseManager.class);
        testDataSource = TestDatabaseHelper.createTestDatabase();
        when(databaseManager.getConnection()).thenAnswer(invocation -> testDataSource.getConnection());

        service = new QuestServiceImpl(null, databaseManager);
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
    @DisplayName("Should generate weekly quests successfully")
    void shouldGenerateWeeklyQuestsSuccessfully() {
        // Given
        String townId = "test-town";

        // When
        service.generateWeeklyQuests(townId);

        // Then
        List<TownQuest> activeQuests = service.getActiveQuests(townId);
        assertThat(activeQuests).hasSize(3);
        assertThat(activeQuests).allMatch(quest -> quest.isActive() && !quest.isCompleted());
        assertThat(activeQuests).extracting("townId").allMatch(id -> id.equals(townId));
    }

    @Test
    @DisplayName("Should generate weekly quests for town with no existing quests")
    void shouldGenerateWeeklyQuestsForTownWithNoExistingQuests() {
        // Given
        String townId = "new-town";

        // When
        service.generateWeeklyQuests(townId);

        // Then
        List<TownQuest> activeQuests = service.getActiveQuests(townId);
        assertThat(activeQuests).hasSize(3);
    }

    @Test
    @DisplayName("Should get active quests for town")
    void shouldGetActiveQuestsForTown() {
        // Given
        String townId = "test-town";
        service.generateWeeklyQuests(townId);

        // When
        List<TownQuest> activeQuests = service.getActiveQuests(townId);

        // Then
        assertThat(activeQuests).hasSize(3);
        assertThat(activeQuests).allMatch(TownQuest::isActive);
        assertThat(activeQuests).allMatch(quest -> !quest.isCompleted());
    }

    @Test
    @DisplayName("Should get empty active quests for non-existent town")
    void shouldGetEmptyActiveQuestsForNonExistentTown() {
        // Given
        String townId = "non-existent-town";

        // When
        List<TownQuest> activeQuests = service.getActiveQuests(townId);

        // Then
        assertThat(activeQuests).isEmpty();
    }

    @Test
    @DisplayName("Should get completed quests for town")
    void shouldGetCompletedQuestsForTown() {
        // Given
        String townId = "test-town";
        service.generateWeeklyQuests(townId);

        // Mark some quests as completed
        List<TownQuest> activeQuests = service.getActiveQuests(townId);
        if (!activeQuests.isEmpty()) {
            TownQuest quest = activeQuests.get(0);
            quest.setCompleted(true);
        }

        // When
        List<TownQuest> completedQuests = service.getCompletedQuests(townId);

        // Then
        assertThat(completedQuests).isNotEmpty();
        assertThat(completedQuests).allMatch(TownQuest::isCompleted);
    }

    @Test
    @DisplayName("Should get completed quests initially empty")
    void shouldGetCompletedQuestsInitiallyEmpty() {
        // Given
        String townId = "test-town";

        // When
        List<TownQuest> completedQuests = service.getCompletedQuests(townId);

        // Then
        assertThat(completedQuests).isEmpty();
    }

    @Test
    @DisplayName("Should increment quest progress successfully")
    void shouldIncrementQuestProgressSuccessfully() {
        // Given
        String townId = "test-town";
        service.generateWeeklyQuests(townId);
        List<TownQuest> activeQuests = service.getActiveQuests(townId);
        TownQuest quest = activeQuests.get(0);
        int initialProgress = quest.getCurrentProgress();

        // When
        service.incrementProgress(townId, quest.getId(), 5);

        // Then
        Optional<TownQuest> updatedQuest = service.getQuest(quest.getId());
        assertThat(updatedQuest).isPresent();
        assertThat(updatedQuest.get().getCurrentProgress()).isEqualTo(initialProgress + 5);
    }

    @Test
    @DisplayName("Should not increment progress for non-existent quest")
    void shouldNotIncrementProgressForNonExistentQuest() {
        // Given
        String townId = "test-town";
        String nonExistentQuestId = "fake-quest-123";

        // When & Then
        assertThatCode(() -> service.incrementProgress(townId, nonExistentQuestId, 5))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should not increment progress for completed quest")
    void shouldNotIncrementProgressForCompletedQuest() {
        // Given
        String townId = "test-town";
        service.generateWeeklyQuests(townId);
        List<TownQuest> activeQuests = service.getActiveQuests(townId);
        TownQuest quest = activeQuests.get(0);

        // Complete the quest first
        quest.setCompleted(true);
        int progressBefore = quest.getCurrentProgress();

        // When
        service.incrementProgress(townId, quest.getId(), 5);

        // Then
        Optional<TownQuest> updatedQuest = service.getQuest(quest.getId());
        assertThat(updatedQuest).isPresent();
        assertThat(updatedQuest.get().getCurrentProgress()).isEqualTo(progressBefore);
    }

    @Test
    @DisplayName("Should get quest by ID")
    void shouldGetQuestById() {
        // Given
        String townId = "test-town";
        service.generateWeeklyQuests(townId);
        List<TownQuest> activeQuests = service.getActiveQuests(townId);
        TownQuest quest = activeQuests.get(0);

        // When
        Optional<TownQuest> retrievedQuest = service.getQuest(quest.getId());

        // Then
        assertThat(retrievedQuest).isPresent();
        assertThat(retrievedQuest.get().getId()).isEqualTo(quest.getId());
    }

    @Test
    @DisplayName("Should not get quest for non-existent ID")
    void shouldNotGetQuestForNonExistentId() {
        // When
        Optional<TownQuest> quest = service.getQuest("non-existent-id");

        // Then
        assertThat(quest).isEmpty();
    }

    @Test
    @DisplayName("Should generate different quest types")
    void shouldGenerateDifferentQuestTypes() {
        // Given
        String townId = "test-town";

        // When
        service.generateWeeklyQuests(townId);
        List<TownQuest> activeQuests = service.getActiveQuests(townId);

        // Then
        assertThat(activeQuests).hasSize(3);
        List<TownQuestType> questTypes = activeQuests.stream()
                .map(TownQuest::getQuestType)
                .toList();
        assertThat(questTypes).contains(TownQuestType.RESOURCE_COLLECTION,
                                        TownQuestType.BUILDING,
                                        TownQuestType.POPULATION);
    }
}
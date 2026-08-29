package org.aincraft.guilds.services;

import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.GuildQuest;
import org.aincraft.guilds.services.impl.QuestServiceImpl;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestServiceImplTest {
    private static final String GUILD_ID = "guild-quest-test";

    private final DatabaseManager databaseManager = mock(DatabaseManager.class);
    private final TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
    private final TravelCurrencyConfig currencyConfig = TravelCurrencyConfig.defaults();
    private QuestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestServiceImpl(null, databaseManager, currencyService, currencyConfig);
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void thresholdCrossingAwardsOnceToTheContributor() {
        GuildQuest quest = activeQuest();
        UUID contributor = UUID.randomUUID();

        service.incrementProgress(GUILD_ID, quest.getId(), 4, contributor);
        service.incrementProgress(GUILD_ID, quest.getId(), 1, contributor);
        service.incrementProgress(GUILD_ID, quest.getId(), 1, contributor);

        assertTrue(quest.isCompleted());
        verify(currencyService, times(1)).award(
                eq(contributor),
                eq(TravelCurrencyRewardSource.QUEST_COMPLETION),
                eq("quest:" + GUILD_ID + ":" + quest.getId()),
                eq(currencyConfig.rewardAmount(TravelCurrencyRewardSource.QUEST_COMPLETION)),
                anyLong());
    }

    @Test
    void actorlessProgressMutatesQuestWithoutAwarding() {
        GuildQuest quest = activeQuest();

        service.incrementProgress(GUILD_ID, quest.getId(), 5, null);

        assertTrue(quest.isCompleted());
        verify(currencyService, never()).award(
                any(),
                eq(TravelCurrencyRewardSource.QUEST_COMPLETION),
                anyString(),
                anyLong(),
                anyLong());
    }

    private GuildQuest activeQuest() {
        service.generateWeeklyQuests(GUILD_ID);
        GuildQuest quest = service.getActiveQuests(GUILD_ID).get(0);
        quest.setTargetAmount(5);
        quest.setCurrentProgress(0);
        return quest;
    }
}

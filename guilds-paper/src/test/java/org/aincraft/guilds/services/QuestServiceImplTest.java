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
import java.util.EnumMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestServiceImplTest {
    private static final String GUILD_ID = "guild-quest-test";
    private static final long QUEST_REWARD = 37L;
    private final DatabaseManager databaseManager = mock(DatabaseManager.class);
    private final TravelCurrencyService currencyService = mock(TravelCurrencyService.class);
    private final TravelCurrencyConfig currencyConfig = rewardConfig(
            TravelCurrencyRewardSource.QUEST_COMPLETION, QUEST_REWARD);
    private QuestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestServiceImpl(null, databaseManager, currencyService, currencyConfig);
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(
                        new TravelCurrencyService.RewardResult(
                                TravelCurrencyService.RewardStatus.AWARDED, null)));
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

    @Test
    void exceptionalAwardIsObservedAndLoggedWithoutUndoingProgress() {
        Logger logger = mock(Logger.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(logger);
        service = new QuestServiceImpl(plugin, databaseManager, currencyService, currencyConfig);
        GuildQuest quest = activeQuest();
        UUID contributor = UUID.randomUUID();
        IllegalStateException failure = new IllegalStateException("wallet unavailable");
        when(currencyService.award(any(), any(TravelCurrencyRewardSource.class), anyString(), anyLong(), anyLong()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertDoesNotThrow(() -> service.incrementProgress(GUILD_ID, quest.getId(), 5, contributor));

        assertTrue(quest.isCompleted());
        verify(logger).log(
                eq(Level.WARNING),
                contains("source=QUEST_COMPLETION"),
                same(failure));
    }

    private GuildQuest activeQuest() {
        service.generateWeeklyQuests(GUILD_ID);
        GuildQuest quest = service.getActiveQuests(GUILD_ID).get(0);
        quest.setTargetAmount(5);
        quest.setCurrentProgress(0);
        return quest;
    }
    private static TravelCurrencyConfig rewardConfig(TravelCurrencyRewardSource source, long amount) {
        TravelCurrencyConfig defaults = TravelCurrencyConfig.defaults();
        EnumMap<TravelCurrencyRewardSource, Long> rewards =
                new EnumMap<>(TravelCurrencyRewardSource.class);
        rewards.put(source, amount);
        return new TravelCurrencyConfig(defaults.starterBalance(), defaults.maximumBalance(),
                defaults.baseCost(), defaults.distanceDivisor(), defaults.modeMultipliers(),
                defaults.reservationDurationMillis(), rewards);
    }

}

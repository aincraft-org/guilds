package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.GuildQuest;
import java.util.List;
import java.util.Optional;

/** Defines operations for quest service. */
public interface QuestService {
    /**
     * Returns the active quests.
     * @param guildId the guild id
     * @return the result
     */
    List<GuildQuest> getActiveQuests(String guildId);
    /**
     * Returns the completed quests.
     * @param guildId the guild id
     * @return the result
     */
    List<GuildQuest> getCompletedQuests(String guildId);
    /**
     * Performs the generate weekly quests operation.
     * @param guildId the guild id
     */
    void generateWeeklyQuests(String guildId);
    /**
     * Performs the increment progress operation.
     * @param guildId the guild id
     * @param questId the quest id
     * @param amount the amount
     */
    void incrementProgress(String guildId, String questId, int amount);
    /**
     * Returns the quest.
     * @param questId the quest id
     * @return the result
     */
    Optional<GuildQuest> getQuest(String questId);
}
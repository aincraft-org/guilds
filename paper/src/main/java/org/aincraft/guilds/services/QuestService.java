package org.aincraft.guilds.services;

import org.aincraft.guilds.models.TownQuest;
import java.util.List;
import java.util.Optional;

public interface QuestService {
    List<TownQuest> getActiveQuests(String townId);
    List<TownQuest> getCompletedQuests(String townId);
    void generateWeeklyQuests(String townId);
    void incrementProgress(String townId, String questId, int amount);
    Optional<TownQuest> getQuest(String questId);
}
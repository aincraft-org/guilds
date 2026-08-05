package org.aincraft.towny.services;

import org.aincraft.towny.models.TownQuest;
import java.util.List;
import java.util.Optional;

public interface QuestService {
    List<TownQuest> getActiveQuests(String townId);
    List<TownQuest> getCompletedQuests(String townId);
    void generateWeeklyQuests(String townId);
    void incrementProgress(String townId, String questId, int amount);
    Optional<TownQuest> getQuest(String questId);
}
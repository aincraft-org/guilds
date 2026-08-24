package org.aincraft.guilds.services;

import org.aincraft.guilds.models.GuildQuest;
import java.util.List;
import java.util.Optional;

public interface QuestService {
    List<GuildQuest> getActiveQuests(String guildId);
    List<GuildQuest> getCompletedQuests(String guildId);
    void generateWeeklyQuests(String guildId);
    void incrementProgress(String guildId, String questId, int amount);
    Optional<GuildQuest> getQuest(String questId);
}
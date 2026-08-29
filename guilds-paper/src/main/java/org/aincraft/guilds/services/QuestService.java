package org.aincraft.guilds.services;

import org.aincraft.guilds.models.GuildQuest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface QuestService {
    List<GuildQuest> getActiveQuests(String guildId);
    List<GuildQuest> getCompletedQuests(String guildId);
    void generateWeeklyQuests(String guildId);
    void incrementProgress(String guildId, String questId, int amount, UUID contributorUuid);
    Optional<GuildQuest> getQuest(String questId);
}
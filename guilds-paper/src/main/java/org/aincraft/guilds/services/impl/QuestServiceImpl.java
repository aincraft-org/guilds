package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.config.TravelCurrencyConfig;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.services.QuestService;
import org.aincraft.guilds.services.travel.TravelCurrencyRewardSource;
import org.aincraft.guilds.services.travel.TravelCurrencyService;
import org.aincraft.guilds.territory.persist.SqlStatements;
import org.aincraft.guilds.models.GuildQuest;
import org.aincraft.guilds.models.GuildQuestType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;


public class QuestServiceImpl implements QuestService {
    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final TravelCurrencyService travelCurrencyService;
    private final TravelCurrencyConfig travelCurrencyConfig;
    private final Map<String, List<GuildQuest>> questsByGuild = new HashMap<>();


    public QuestServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager) {
        this(plugin, databaseManager, null, null);
    }

    public QuestServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager,
                            TravelCurrencyService travelCurrencyService,
                            TravelCurrencyConfig travelCurrencyConfig) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.travelCurrencyService = travelCurrencyService;
        this.travelCurrencyConfig = travelCurrencyConfig;
        loadQuestsFromDatabase();
    }

    @Override
    public List<GuildQuest> getActiveQuests(String guildId) {
        List<GuildQuest> allQuests = questsByGuild.getOrDefault(guildId, new ArrayList<>());
        return allQuests.stream()
                .filter(GuildQuest::isActive)
                .filter(quest -> !quest.isCompleted())
                .toList();
    }

    @Override
    public List<GuildQuest> getCompletedQuests(String guildId) {
        List<GuildQuest> allQuests = questsByGuild.getOrDefault(guildId, new ArrayList<>());
        return allQuests.stream()
                .filter(GuildQuest::isCompleted)
                .toList();
    }

    @Override
    public void generateWeeklyQuests(String guildId) {
        // Remove existing active quests for this guild
        questsByGuild.computeIfAbsent(guildId, k -> new ArrayList<>())
                .removeIf(quest -> quest.isActive() && !quest.isCompleted());

        // Generate 3 random quests of different types
        List<GuildQuestType> availableTypes = new ArrayList<>(Arrays.asList(GuildQuestType.values()));
        Collections.shuffle(availableTypes);

        List<GuildQuest> newQuests = new ArrayList<>();
        for (int i = 0; i < Math.min(3, availableTypes.size()); i++) {
            GuildQuestType type = availableTypes.get(i);
            GuildQuest quest = createQuestForType(guildId, type);
            newQuests.add(quest);
            saveQuestToDatabase(quest);
        }

        questsByGuild.put(guildId, new ArrayList<>(
            questsByGuild.getOrDefault(guildId, new ArrayList<>())
                .stream()
                .filter(quest -> !quest.isActive())
                .toList()
        ));
        questsByGuild.get(guildId).addAll(newQuests);
    }

    @Override
    public void incrementProgress(String guildId, String questId, int amount, UUID contributorUuid) {
        List<GuildQuest> quests = questsByGuild.getOrDefault(guildId, new ArrayList<>());
        for (GuildQuest quest : quests) {
            if (quest.getId().equals(questId) && quest.isActive() && !quest.isCompleted()) {
                boolean wasCompleted = quest.isCompleted();
                quest.incrementProgress(amount);
                updateQuestInDatabase(quest);
                if (!wasCompleted && quest.isCompleted() && contributorUuid != null
                        && travelCurrencyService != null && travelCurrencyConfig != null) {
                    travelCurrencyService.award(
                            contributorUuid,
                            TravelCurrencyRewardSource.QUEST_COMPLETION,
                            "quest:" + guildId + ":" + questId,
                            travelCurrencyConfig.rewardAmount(TravelCurrencyRewardSource.QUEST_COMPLETION),
                            System.currentTimeMillis());
                }
                break;
            }
        }
    }

    @Override
    public Optional<GuildQuest> getQuest(String questId) {
        return questsByGuild.values().stream()
                .flatMap(List::stream)
                .filter(quest -> quest.getId().equals(questId))
                .findFirst();
    }

    private GuildQuest createQuestForType(String guildId, GuildQuestType type) {
        String questId = UUID.randomUUID().toString();
        String description = generateDescriptionForType(type);
        int targetAmount = generateTargetForType(type);
        int techPoints = generateTechPointsForType(type);

        return new GuildQuest(questId, guildId, type, description, targetAmount, techPoints);
    }

    private String generateDescriptionForType(GuildQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> "Collect valuable resources for your guild";
            case BUILDING -> "Construct new buildings to expand your guild";
            case POPULATION -> "Recruit new residents to grow your community";
            case ECONOMIC -> "Strengthen your guild's economy";
            case SOCIAL -> "Build relationships with neighboring communities";
        };
    }

    private int generateTargetForType(GuildQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> new Random().nextInt(100) + 50;
            case BUILDING -> new Random().nextInt(5) + 1;
            case POPULATION -> new Random().nextInt(10) + 1;
            case ECONOMIC -> new Random().nextInt(1000) + 500;
            case SOCIAL -> new Random().nextInt(20) + 5;
        };
    }

    private int generateTechPointsForType(GuildQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> new Random().nextInt(25) + 10;
            case BUILDING -> new Random().nextInt(50) + 20;
            case POPULATION -> new Random().nextInt(30) + 15;
            case ECONOMIC -> new Random().nextInt(40) + 20;
            case SOCIAL -> new Random().nextInt(35) + 15;
        };
    }

    private void loadQuestsFromDatabase() {
        String query = SqlStatements.load("quests/select-all.sql");
        databaseManager.executeTransaction(conn -> {
            try (var stmt = conn.prepareStatement(query);
                 var rs = stmt.executeQuery()) {

                while (rs.next()) {
                    GuildQuest quest = deserializeFromResultSet(rs);
                    questsByGuild
                        .computeIfAbsent(quest.getGuildId(), k -> new ArrayList<>())
                        .add(quest);
                }
            }
        });
    }

    private void saveQuestToDatabase(GuildQuest quest) {
        String query = SqlStatements.load("quests/insert.sql");
        databaseManager.executeTransaction(conn -> {
            try (var stmt = conn.prepareStatement(query)) {
                serializeToPreparedStatement(stmt, quest);
                stmt.executeUpdate();
            }
        });
    }

    private void updateQuestInDatabase(GuildQuest quest) {
        String query = SqlStatements.load("quests/update-progress.sql");
        databaseManager.executeTransaction(conn -> {
            try (var stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, quest.getCurrentProgress());
                stmt.setBoolean(2, quest.isActive());
                stmt.setBoolean(3, quest.isCompleted());
                stmt.setString(4, quest.isCompleted() ? quest.getCompletedAt().toString() : null);
                stmt.setString(5, quest.getId());
                stmt.executeUpdate();
            }
        });
    }

    private GuildQuest deserializeFromResultSet(ResultSet rs) throws SQLException {
        GuildQuest quest = new GuildQuest(
            rs.getString("id"),
            rs.getString("guild_id"),
            GuildQuestType.valueOf(rs.getString("quest_type")),
            rs.getString("description"),
            rs.getInt("target_amount"),
            rs.getInt("tech_point_reward")
        );

        quest.setCurrentProgress(rs.getInt("current_progress"));
        quest.setActive(rs.getBoolean("is_active"));
        quest.setCompleted(rs.getBoolean("is_completed"));
        quest.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));

        String completedAt = rs.getString("completed_at");
        if (completedAt != null) {
            quest.setCompletedAt(LocalDateTime.parse(completedAt));
        }

        return quest;
    }

    private void serializeToPreparedStatement(java.sql.PreparedStatement stmt, GuildQuest quest) throws SQLException {
        stmt.setString(1, quest.getId());
        stmt.setString(2, quest.getGuildId());
        stmt.setString(3, quest.getQuestType().name());
        stmt.setString(4, quest.getDescription());
        stmt.setInt(5, quest.getTargetAmount());
        stmt.setInt(6, quest.getCurrentProgress());
        stmt.setInt(7, quest.getTechPointReward());
        stmt.setBoolean(8, quest.isActive());
        stmt.setBoolean(9, quest.isCompleted());
        stmt.setString(10, quest.getCreatedAt().toString());
        stmt.setString(11, quest.isCompleted() ? quest.getCompletedAt().toString() : null);
    }
}
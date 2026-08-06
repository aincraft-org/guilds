package org.aincraft.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.TownQuest;
import org.aincraft.guilds.models.TownQuestType;
import org.aincraft.guilds.services.QuestService;

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
    private final Map<String, List<TownQuest>> questsByTown = new HashMap<>();


    public QuestServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        loadQuestsFromDatabase();
    }

    @Override
    public List<TownQuest> getActiveQuests(String townId) {
        List<TownQuest> allQuests = questsByTown.getOrDefault(townId, new ArrayList<>());
        return allQuests.stream()
                .filter(TownQuest::isActive)
                .filter(quest -> !quest.isCompleted())
                .toList();
    }

    @Override
    public List<TownQuest> getCompletedQuests(String townId) {
        List<TownQuest> allQuests = questsByTown.getOrDefault(townId, new ArrayList<>());
        return allQuests.stream()
                .filter(TownQuest::isCompleted)
                .toList();
    }

    @Override
    public void generateWeeklyQuests(String townId) {
        // Remove existing active quests for this town
        questsByTown.computeIfAbsent(townId, k -> new ArrayList<>())
                .removeIf(quest -> quest.isActive() && !quest.isCompleted());

        // Generate 3 random quests of different types
        List<TownQuestType> availableTypes = new ArrayList<>(Arrays.asList(TownQuestType.values()));
        Collections.shuffle(availableTypes);

        List<TownQuest> newQuests = new ArrayList<>();
        for (int i = 0; i < Math.min(3, availableTypes.size()); i++) {
            TownQuestType type = availableTypes.get(i);
            TownQuest quest = createQuestForType(townId, type);
            newQuests.add(quest);
            saveQuestToDatabase(quest);
        }

        questsByTown.put(townId, new ArrayList<>(
            questsByTown.getOrDefault(townId, new ArrayList<>())
                .stream()
                .filter(quest -> !quest.isActive())
                .toList()
        ));
        questsByTown.get(townId).addAll(newQuests);
    }

    @Override
    public void incrementProgress(String townId, String questId, int amount) {
        List<TownQuest> quests = questsByTown.getOrDefault(townId, new ArrayList<>());
        for (TownQuest quest : quests) {
            if (quest.getId().equals(questId) && quest.isActive() && !quest.isCompleted()) {
                quest.incrementProgress(amount);
                updateQuestInDatabase(quest);
                break;
            }
        }
    }

    @Override
    public Optional<TownQuest> getQuest(String questId) {
        return questsByTown.values().stream()
                .flatMap(List::stream)
                .filter(quest -> quest.getId().equals(questId))
                .findFirst();
    }

    private TownQuest createQuestForType(String townId, TownQuestType type) {
        String questId = UUID.randomUUID().toString();
        String description = generateDescriptionForType(type);
        int targetAmount = generateTargetForType(type);
        int techPoints = generateTechPointsForType(type);

        return new TownQuest(questId, townId, type, description, targetAmount, techPoints);
    }

    private String generateDescriptionForType(TownQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> "Collect valuable resources for your town";
            case BUILDING -> "Construct new buildings to expand your town";
            case POPULATION -> "Recruit new residents to grow your community";
            case ECONOMIC -> "Strengthen your town's economy";
            case SOCIAL -> "Build relationships with neighboring communities";
        };
    }

    private int generateTargetForType(TownQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> new Random().nextInt(100) + 50;
            case BUILDING -> new Random().nextInt(5) + 1;
            case POPULATION -> new Random().nextInt(10) + 1;
            case ECONOMIC -> new Random().nextInt(1000) + 500;
            case SOCIAL -> new Random().nextInt(20) + 5;
        };
    }

    private int generateTechPointsForType(TownQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> new Random().nextInt(25) + 10;
            case BUILDING -> new Random().nextInt(50) + 20;
            case POPULATION -> new Random().nextInt(30) + 15;
            case ECONOMIC -> new Random().nextInt(40) + 20;
            case SOCIAL -> new Random().nextInt(35) + 15;
        };
    }

    private void loadQuestsFromDatabase() {
        String query = "SELECT * FROM town_quests";
        databaseManager.executeTransaction(conn -> {
            try (var stmt = conn.prepareStatement(query);
                 var rs = stmt.executeQuery()) {

                while (rs.next()) {
                    TownQuest quest = deserializeFromResultSet(rs);
                    questsByTown
                        .computeIfAbsent(quest.getTownId(), k -> new ArrayList<>())
                        .add(quest);
                }
            }
        });
    }

    private void saveQuestToDatabase(TownQuest quest) {
        String query = "INSERT INTO town_quests (id, town_id, quest_type, description, target_amount, current_progress, tech_point_reward, is_active, is_completed, created_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        databaseManager.executeTransaction(conn -> {
            try (var stmt = conn.prepareStatement(query)) {
                serializeToPreparedStatement(stmt, quest);
                stmt.executeUpdate();
            }
        });
    }

    private void updateQuestInDatabase(TownQuest quest) {
        String query = "UPDATE town_quests SET current_progress = ?, is_active = ?, is_completed = ?, completed_at = ? WHERE id = ?";
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

    private TownQuest deserializeFromResultSet(ResultSet rs) throws SQLException {
        TownQuest quest = new TownQuest(
            rs.getString("id"),
            rs.getString("town_id"),
            TownQuestType.valueOf(rs.getString("quest_type")),
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

    private void serializeToPreparedStatement(java.sql.PreparedStatement stmt, TownQuest quest) throws SQLException {
        stmt.setString(1, quest.getId());
        stmt.setString(2, quest.getTownId());
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
package dev.mintychochip.guilds.services.impl;



import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.GuildQuest;
import dev.mintychochip.guilds.models.GuildQuestType;
import dev.mintychochip.guilds.services.QuestService;
import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.sql.SqlParams;

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


/** Implementation of quest service. */
public class QuestServiceImpl implements QuestService {
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The quests by guild. */
    private final Map<String, List<GuildQuest>> questsByGuild = new HashMap<>();


    /**
     * Creates a new quest service impl instance.
     * @param plugin the plugin
     * @param databaseManager the database manager
     */
    public QuestServiceImpl(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        loadQuestsFromDatabase();
    }

    /**
     * Returns the active quests.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public List<GuildQuest> getActiveQuests(String guildId) {
        List<GuildQuest> allQuests = questsByGuild.getOrDefault(guildId, new ArrayList<>());
        return allQuests.stream()
                .filter(GuildQuest::isActive)
                .filter(quest -> !quest.isCompleted())
                .toList();
    }

    /**
     * Returns the completed quests.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public List<GuildQuest> getCompletedQuests(String guildId) {
        List<GuildQuest> allQuests = questsByGuild.getOrDefault(guildId, new ArrayList<>());
        return allQuests.stream()
                .filter(GuildQuest::isCompleted)
                .toList();
    }

    /**
     * Performs the generate weekly quests operation.
     * @param guildId the guild id
     */
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

    /**
     * Performs the increment progress operation.
     * @param guildId the guild id
     * @param questId the quest id
     * @param amount the amount
     */
    @Override
    public void incrementProgress(String guildId, String questId, int amount) {
        List<GuildQuest> quests = questsByGuild.getOrDefault(guildId, new ArrayList<>());
        for (GuildQuest quest : quests) {
            if (quest.getId().equals(questId) && quest.isActive() && !quest.isCompleted()) {
                quest.incrementProgress(amount);
                updateQuestInDatabase(quest);
                break;
            }
        }
    }

    /**
     * Returns the quest.
     * @param questId the quest id
     * @return the result
     */
    @Override
    public Optional<GuildQuest> getQuest(String questId) {
        return questsByGuild.values().stream()
                .flatMap(List::stream)
                .filter(quest -> quest.getId().equals(questId))
                .findFirst();
    }

    /**
     * Creates a new quest for type.
     * @param guildId the guild id
     * @param type the type
     * @return the result
     */
    private GuildQuest createQuestForType(String guildId, GuildQuestType type) {
        String questId = UUID.randomUUID().toString();
        String description = generateDescriptionForType(type);
        int targetAmount = generateTargetForType(type);
        int techPoints = generateTechPointsForType(type);

        return new GuildQuest(questId, guildId, type, description, targetAmount, techPoints);
    }

    /**
     * Performs the generate description for type operation.
     * @param type the type
     * @return the result
     */
    private String generateDescriptionForType(GuildQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> "Collect valuable resources for your guild";
            case BUILDING -> "Construct new buildings to expand your guild";
            case POPULATION -> "Recruit new residents to grow your community";
            case ECONOMIC -> "Strengthen your guild's economy";
            case SOCIAL -> "Build relationships with neighboring communities";
        };
    }

    /**
     * Performs the generate target for type operation.
     * @param type the type
     * @return the result
     */
    private int generateTargetForType(GuildQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> new Random().nextInt(100) + 50;
            case BUILDING -> new Random().nextInt(5) + 1;
            case POPULATION -> new Random().nextInt(10) + 1;
            case ECONOMIC -> new Random().nextInt(1000) + 500;
            case SOCIAL -> new Random().nextInt(20) + 5;
        };
    }

    /**
     * Performs the generate tech points for type operation.
     * @param type the type
     * @return the result
     */
    private int generateTechPointsForType(GuildQuestType type) {
        return switch (type) {
            case RESOURCE_COLLECTION -> new Random().nextInt(25) + 10;
            case BUILDING -> new Random().nextInt(50) + 20;
            case POPULATION -> new Random().nextInt(30) + 15;
            case ECONOMIC -> new Random().nextInt(40) + 20;
            case SOCIAL -> new Random().nextInt(35) + 15;
        };
    }

    /** Loads the quests from database. */
    private void loadQuestsFromDatabase() {
        databaseManager.executeTransaction(conn -> {
            try (var stmt = SQL.prepare(conn, "quests/select-all.sql", Map.of());
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

    /**
     * Saves the quest to database.
     * @param quest the quest
     */
    private void saveQuestToDatabase(GuildQuest quest) {
        databaseManager.executeTransaction(conn -> {
            try (var stmt = SQL.prepare(conn, "quests/insert.sql", SqlParams.of(
                    "id", quest.getId(),
                    "guild_id", quest.getGuildId(),
                    "quest_type", quest.getQuestType().name(),
                    "description", quest.getDescription(),
                    "target_amount", quest.getTargetAmount(),
                    "current_progress", quest.getCurrentProgress(),
                    "tech_point_reward", quest.getTechPointReward(),
                    "is_active", quest.isActive(),
                    "is_completed", quest.isCompleted(),
                    "created_at", quest.getCreatedAt().toString(),
                    "completed_at", quest.isCompleted() ? quest.getCompletedAt().toString() : null))) {
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Updates the quest in database.
     * @param quest the quest
     */
    private void updateQuestInDatabase(GuildQuest quest) {
        databaseManager.executeTransaction(conn -> {
            try (var stmt = SQL.prepare(conn, "quests/update.sql", SqlParams.of(
                    "current_progress", quest.getCurrentProgress(),
                    "is_active", quest.isActive(),
                    "is_completed", quest.isCompleted(),
                    "completed_at", quest.isCompleted() ? quest.getCompletedAt().toString() : null,
                    "id", quest.getId()))) {
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Performs the deserialize from result set operation.
     * @param rs the rs
     * @return the result
     * @throws SQLException if an error occurs
     */
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
}
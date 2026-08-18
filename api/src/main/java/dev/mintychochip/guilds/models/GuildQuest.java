package dev.mintychochip.guilds.models;

import java.time.LocalDateTime;

/** guild quest. */
public class GuildQuest {
    /** The id. */
    private String id;
    /** The guild id. */
    private String guildId;
    /** The quest type. */
    private GuildQuestType questType;
    /** The description. */
    private String description;
    /** The target amount. */
    private int targetAmount;
    /** The current progress. */
    private int currentProgress;
    /** The tech point reward. */
    private int techPointReward;
    /** The is active. */
    private boolean isActive;
    /** The is completed. */
    private boolean isCompleted;
    /** The created at. */
    private LocalDateTime createdAt;
    /** The completed at. */
    private LocalDateTime completedAt;

    /**
     * Creates a new guild quest instance.
     * @param id the id
     * @param guildId the guild id
     * @param questType the quest type
     * @param description the description
     * @param targetAmount the target amount
     * @param techPointReward the tech point reward
     */
    public GuildQuest(String id, String guildId, GuildQuestType questType, String description, int targetAmount, int techPointReward) {
        this.id = id;
        this.guildId = guildId;
        this.questType = questType;
        this.description = description;
        this.targetAmount = targetAmount;
        this.currentProgress = 0;
        this.techPointReward = techPointReward;
        this.isActive = true;
        this.isCompleted = false;
        this.createdAt = LocalDateTime.now();
        this.completedAt = null;
    }

    /**
     * Returns the id.
     * @return the result
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id.
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the guild id.
     * @return the result
     */
    public String getGuildId() {
        return guildId;
    }

    /**
     * Sets the guild id.
     * @param guildId the guild id
     */
    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    /**
     * Returns the quest type.
     * @return the result
     */
    public GuildQuestType getQuestType() {
        return questType;
    }

    /**
     * Sets the quest type.
     * @param questType the quest type
     */
    public void setQuestType(GuildQuestType questType) {
        this.questType = questType;
    }

    /**
     * Returns the description.
     * @return the result
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the target amount.
     * @return the result
     */
    public int getTargetAmount() {
        return targetAmount;
    }

    /**
     * Sets the target amount.
     * @param targetAmount the target amount
     */
    public void setTargetAmount(int targetAmount) {
        this.targetAmount = targetAmount;
    }

    /**
     * Returns the current progress.
     * @return the result
     */
    public int getCurrentProgress() {
        return currentProgress;
    }

    /**
     * Sets the current progress.
     * @param currentProgress the current progress
     */
    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
    }

    /**
     * Returns the tech point reward.
     * @return the result
     */
    public int getTechPointReward() {
        return techPointReward;
    }

    /**
     * Sets the tech point reward.
     * @param techPointReward the tech point reward
     */
    public void setTechPointReward(int techPointReward) {
        this.techPointReward = techPointReward;
    }

    /**
     * Returns whether active.
     * @return the result
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Sets the active.
     * @param active the active
     */
    public void setActive(boolean active) {
        isActive = active;
    }

    /**
     * Returns whether completed.
     * @return the result
     */
    public boolean isCompleted() {
        return isCompleted;
    }

    /**
     * Sets the completed.
     * @param completed the completed
     */
    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    /**
     * Returns the created at.
     * @return the result
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the created at.
     * @param createdAt the created at
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the completed at.
     * @return the result
     */
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    /**
     * Sets the completed at.
     * @param completedAt the completed at
     */
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Performs the increment progress operation.
     * @param amount the amount
     */
    public void incrementProgress(int amount) {
        this.currentProgress = Math.min(this.currentProgress + amount, this.targetAmount);
        if (this.currentProgress >= this.targetAmount) {
            this.isCompleted = true;
            this.isActive = false;
            this.completedAt = LocalDateTime.now();
        }
    }
}
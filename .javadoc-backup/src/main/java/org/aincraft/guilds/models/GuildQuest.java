package org.aincraft.guilds.models;

import java.time.LocalDateTime;

public class GuildQuest {
    private String id;
    private String guildId;
    private GuildQuestType questType;
    private String description;
    private int targetAmount;
    private int currentProgress;
    private int techPointReward;
    private boolean isActive;
    private boolean isCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public GuildQuestType getQuestType() {
        return questType;
    }

    public void setQuestType(GuildQuestType questType) {
        this.questType = questType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(int targetAmount) {
        this.targetAmount = targetAmount;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
    }

    public int getTechPointReward() {
        return techPointReward;
    }

    public void setTechPointReward(int techPointReward) {
        this.techPointReward = techPointReward;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void incrementProgress(int amount) {
        this.currentProgress = Math.min(this.currentProgress + amount, this.targetAmount);
        if (this.currentProgress >= this.targetAmount) {
            this.isCompleted = true;
            this.isActive = false;
            this.completedAt = LocalDateTime.now();
        }
    }
}
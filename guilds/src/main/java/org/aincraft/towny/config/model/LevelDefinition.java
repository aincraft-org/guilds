package org.aincraft.towny.config.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A town level definition parsed from config.yml under {@code town_levels.levels}.
 *
 * <p>Definition levels go from 1 (starting level) to {@code town_levels.max_level}.
 * Level 1 has no requirements; higher levels list material requirements and benefits.
 */
public class LevelDefinition {

    private int level;
    private Map<String, Integer> requirements = new HashMap<>();
    private int techPoints;
    private int claimLimitBonus;
    private int assistantSlotsBonus;
    private double dailyIncomeBonus;
    private List<String> unlockedPlotTypes = new ArrayList<>();

    public LevelDefinition() {
    }

    public LevelDefinition(int level, Map<String, Integer> requirements, int techPoints,
                           int claimLimitBonus, int assistantSlotsBonus, double dailyIncomeBonus,
                           List<String> unlockedPlotTypes) {
        this.level = level;
        this.requirements = requirements != null ? new HashMap<>(requirements) : new HashMap<>();
        this.techPoints = techPoints;
        this.claimLimitBonus = claimLimitBonus;
        this.assistantSlotsBonus = assistantSlotsBonus;
        this.dailyIncomeBonus = dailyIncomeBonus;
        this.unlockedPlotTypes = unlockedPlotTypes != null ? new ArrayList<>(unlockedPlotTypes) : new ArrayList<>();
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Map<String, Integer> getRequirements() {
        return requirements;
    }

    public void setRequirements(Map<String, Integer> requirements) {
        this.requirements = requirements != null ? new HashMap<>(requirements) : new HashMap<>();
    }

    public int getTechPoints() {
        return techPoints;
    }

    public void setTechPoints(int techPoints) {
        this.techPoints = techPoints;
    }

    public int getClaimLimitBonus() {
        return claimLimitBonus;
    }

    public void setClaimLimitBonus(int claimLimitBonus) {
        this.claimLimitBonus = claimLimitBonus;
    }

    public int getAssistantSlotsBonus() {
        return assistantSlotsBonus;
    }

    public void setAssistantSlotsBonus(int assistantSlotsBonus) {
        this.assistantSlotsBonus = assistantSlotsBonus;
    }

    public double getDailyIncomeBonus() {
        return dailyIncomeBonus;
    }

    public void setDailyIncomeBonus(double dailyIncomeBonus) {
        this.dailyIncomeBonus = dailyIncomeBonus;
    }

    public List<String> getUnlockedPlotTypes() {
        return unlockedPlotTypes;
    }

    public void setUnlockedPlotTypes(List<String> unlockedPlotTypes) {
        this.unlockedPlotTypes = unlockedPlotTypes != null ? new ArrayList<>(unlockedPlotTypes) : new ArrayList<>();
    }
}

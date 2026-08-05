package org.aincraft.towny.models;

import java.util.HashMap;
import java.util.Map;

public enum TownSpecialization {
    MINING("Mining", "Increases ore yield and mining speed for town members", 2, new HashMap<>()),
    TRADE_HUB("Trade Hub", "Reduces market taxes and increases trading profits", 3, new HashMap<>()),
    MILITARY("Military", "Increases defense bonuses and combat effectiveness", 4, new HashMap<>()),
    ARCANE("Arcane", "Reduces XP costs for enchanting and magical activities", 5, new HashMap<>()),
    AGRICULTURAL("Agricultural", "Increases crop yields and reduces hunger penalties", 1, new HashMap<>());

    private final String displayName;
    private final String description;
    private final int requiredLevel;
    private final Map<String, Object> perks;

    static {
        MINING.perks.put("oreYieldMultiplier", 1.25);
        MINING.perks.put("miningSpeedMultiplier", 1.15);
        
        TRADE_HUB.perks.put("taxReduction", 0.2);
        TRADE_HUB.perks.put("profitMultiplier", 1.15);
        
        MILITARY.perks.put("defenseBonus", 0.15);
        MILITARY.perks.put("combatDamageMultiplier", 1.10);
        
        ARCANE.perks.put("xpCostReduction", 0.25);
        ARCANE.perks.put("enchantChanceMultiplier", 1.15);
        
        AGRICULTURAL.perks.put("cropYieldMultiplier", 1.3);
        AGRICULTURAL.perks.put("hungerReduction", 0.2);
    }

    TownSpecialization(String displayName, String description, int requiredLevel, Map<String, Object> perks) {
        this.displayName = displayName;
        this.description = description;
        this.requiredLevel = requiredLevel;
        this.perks = perks;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public Map<String, Object> getPerks() {
        return new HashMap<>(perks);
    }
}
package dev.mintychochip.guilds.models;

import java.util.HashMap;
import java.util.Map;

/** Defines the values of guild specialization. */
public enum GuildSpecialization {
    /** The mining constant. */
    MINING("Mining", "Increases ore yield and mining speed for guild members", 2, new HashMap<>()),
    /** The trade hub constant. */
    TRADE_HUB("Trade Hub", "Reduces market taxes and increases trading profits", 3, new HashMap<>()),
    /** The military constant. */
    MILITARY("Military", "Increases defense bonuses and combat effectiveness", 4, new HashMap<>()),
    /** The arcane constant. */
    ARCANE("Arcane", "Reduces XP costs for enchanting and magical activities", 5, new HashMap<>()),
    /** The agricultural constant. */
    AGRICULTURAL("Agricultural", "Increases crop yields and reduces hunger penalties", 1, new HashMap<>());

    /** The display name. */
    private final String displayName;
    /** The description. */
    private final String description;
    /** The required level. */
    private final int requiredLevel;
    /** The perks. */
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

    /**
     * Creates a new  instance.
     * @param displayName the display name
     * @param description the description
     * @param requiredLevel the required level
     * @param perks the perks
     */
    GuildSpecialization(String displayName, String description, int requiredLevel, Map<String, Object> perks) {
        this.displayName = displayName;
        this.description = description;
        this.requiredLevel = requiredLevel;
        this.perks = perks;
    }

    /**
     * Returns the display name.
     * @return the result
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the description.
     * @return the result
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the required level.
     * @return the result
     */
    public int getRequiredLevel() {
        return requiredLevel;
    }

    /**
     * Returns the perks.
     * @return the result
     */
    public Map<String, Object> getPerks() {
        return new HashMap<>(perks);
    }
}
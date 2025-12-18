package org.aincraft.skilltree;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Enum representing the three skill tree branches available to guilds.
 * Each branch has a display name, icon, and associated color for UI representation.
 */
public enum SkillBranch {
    ECONOMY("Economy", Material.GOLD_INGOT, NamedTextColor.GOLD),
    TERRITORY("Territory", Material.GRASS_BLOCK, NamedTextColor.GREEN),
    COMBAT("Combat", Material.DIAMOND_SWORD, NamedTextColor.RED);

    private final String displayName;
    private final Material icon;
    private final NamedTextColor color;

    SkillBranch(String displayName, Material icon, NamedTextColor color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    /**
     * Gets the display name of this skill branch.
     * @return the branch's display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the icon material for this skill branch.
     * @return the branch's icon material
     */
    public Material getIcon() {
        return icon;
    }

    /**
     * Gets the color associated with this skill branch.
     * @return the branch's color
     */
    public NamedTextColor getColor() {
        return color;
    }
}

package org.aincraft.skilltree.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.skilltree.GuildSkillTree;
import org.aincraft.skilltree.SkillDefinition;
import org.aincraft.skilltree.SkillTreeRegistry;
import org.aincraft.skilltree.SkillTreeService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Skill tree GUI displaying all skills in a DAG layout using chest inventory.
 * Positions skills using SkillTreeLayoutEngine and renders them with state-specific
 * visual indicators (unlocked, available, locked).
 *
 * Single Responsibility: Skill tree GUI rendering and management.
 */
public class SkillTreeGUI {
    private final UUID guildId;
    private final Player viewer;
    private final SkillTreeService skillTreeService;
    private final SkillTreeRegistry skillTreeRegistry;
    private final GuildSkillTree guildSkillTree;
    private final Gui gui;
    private final Map<String, SkillNodePosition> layout;

    /**
     * Creates a new skill tree GUI for a player.
     *
     * @param guildId the guild ID
     * @param viewer the player viewing the GUI
     * @param skillTreeService service for skill operations
     * @param skillTreeRegistry registry for skill definitions
     * @throws IllegalArgumentException if any parameter is null
     */
    public SkillTreeGUI(UUID guildId, Player viewer, SkillTreeService skillTreeService, SkillTreeRegistry skillTreeRegistry) {
        this.guildId = Objects.requireNonNull(guildId, "Guild ID cannot be null");
        this.viewer = Objects.requireNonNull(viewer, "Viewer cannot be null");
        this.skillTreeService = Objects.requireNonNull(skillTreeService, "SkillTreeService cannot be null");
        this.skillTreeRegistry = Objects.requireNonNull(skillTreeRegistry, "SkillTreeRegistry cannot be null");

        // Get or create skill tree for the guild
        this.guildSkillTree = skillTreeService.getOrCreateSkillTree(guildId);

        // Calculate layout using DAG engine
        Collection<SkillDefinition> allSkills = skillTreeRegistry.getAllSkills();
        SkillTreeLayoutEngine layoutEngine = new SkillTreeLayoutEngine(allSkills);
        this.layout = layoutEngine.calculateLayout();

        // Determine GUI size (number of rows needed)
        int maxRow = layout.values().stream()
                .mapToInt(SkillNodePosition::row)
                .max()
                .orElse(0);
        int guiRows = Math.min(Math.max(maxRow + 1, 3), 6); // Min 3 rows, max 6

        // Create GUI
        this.gui = Gui.gui()
                .title(Component.text("Skill Tree").color(NamedTextColor.DARK_PURPLE))
                .rows(guiRows)
                .create();

        // Prevent item removal and set click handler
        this.gui.setDefaultClickAction(event -> {
            event.setCancelled(true);
            // Click handler is handled in createSkillItem with inline handlers
        });

        renderSkills();
    }

    /**
     * Opens the GUI for the viewer.
     */
    public void open() {
        gui.open(viewer);
    }

    /**
     * Renders all skills in their calculated positions.
     */
    private void renderSkills() {
        // Max slots = rows * 9
        int maxSlots = gui.getRows() * 9;

        for (SkillDefinition skill : skillTreeRegistry.getAllSkills()) {
            SkillNodePosition position = layout.get(skill.id());
            if (position == null) {
                continue; // Skip skills without positions
            }

            int slot = position.toSlot();
            if (slot < 0 || slot >= maxSlots) {
                continue; // Skip out-of-bounds slots
            }

            dev.triumphteam.gui.guis.GuiItem guiItem = createSkillItem(skill);
            gui.setItem(slot, guiItem);
        }
    }

    /**
     * Creates a GUI item for a skill with appropriate visual state.
     * Determines if the skill is unlocked, available to unlock, or locked.
     *
     * @param skill the skill definition
     * @return the GUI item
     */
    private dev.triumphteam.gui.guis.GuiItem createSkillItem(SkillDefinition skill) {
        boolean isUnlocked = guildSkillTree.isUnlocked(skill.id());
        boolean canUnlock = guildSkillTree.canUnlock(skill);

        Material material;
        NamedTextColor nameColor;
        List<Component> lore = new ArrayList<>();

        if (isUnlocked) {
            // Unlocked: Enchanted book with glow
            material = Material.ENCHANTED_BOOK;
            nameColor = NamedTextColor.GREEN;
            lore.add(createLoreLine("Status", "UNLOCKED", NamedTextColor.GREEN));
        } else if (canUnlock) {
            // Available: Book in yellow
            material = Material.BOOK;
            nameColor = NamedTextColor.YELLOW;
            lore.add(Component.empty());
            lore.add(createLoreLine("Cost", skill.spCost() + " SP", NamedTextColor.GREEN));
            lore.add(Component.empty());
            lore.add(Component.text("CLICK TO UNLOCK").color(NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true));
        } else {
            // Locked: Barrier in red or book in gray
            if (guildSkillTree.getAvailableSp() < skill.spCost()) {
                material = Material.BOOK;
                nameColor = NamedTextColor.GRAY;
                lore.add(Component.empty());
                lore.add(createLoreLine("Cost", skill.spCost() + " SP", NamedTextColor.RED));
                lore.add(Component.empty());
                lore.add(Component.text("LOCKED - Insufficient SP").color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
            } else {
                material = Material.BARRIER;
                nameColor = NamedTextColor.RED;
                lore.add(Component.empty());
                lore.add(createLoreLine("Cost", skill.spCost() + " SP", NamedTextColor.RED));
                lore.add(Component.empty());
                addPrerequisitesLore(lore, skill);
                lore.add(Component.empty());
                lore.add(Component.text("LOCKED - Missing Prerequisites").color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
            }
        }

        // Add common lore
        lore.add(0, Component.empty());
        lore.add(1, Component.text(skill.description()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
        lore.add(2, Component.empty());
        lore.add(3, createLoreLine("Effect", skill.effect().displayName(), NamedTextColor.AQUA));

        if (isUnlocked) {
            lore.add(4, Component.empty());
            lore.add(5, createLoreLine("Cost", skill.spCost() + " SP", NamedTextColor.GREEN));
        }

        // Store skill ID in item for identification
        ItemBuilder itemBuilder = ItemBuilder.from(material)
                .name(Component.text(skill.name()).color(nameColor).decoration(TextDecoration.BOLD, true))
                .lore(lore);

        // Add glow effect for unlocked skills
        if (isUnlocked) {
            itemBuilder.enchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
        }

        ItemStack itemStack = itemBuilder.build();
        // Store skill name for recovery in click handler
        itemStack.editMeta(meta -> {
            if (meta != null) {
                meta.setDisplayName(skill.name()); // Identifier for skill lookup
            }
        });

        // Create GUI item with click handler
        return itemBuilder.asGuiItem(event -> {
            event.setCancelled(true);
            handleSkillClick(skill.id());
        });
    }

    /**
     * Adds prerequisite status information to lore.
     *
     * @param lore the lore list to add to
     * @param skill the skill to check prerequisites for
     */
    private void addPrerequisitesLore(List<Component> lore, SkillDefinition skill) {
        if (!skill.hasPrerequisites()) {
            return;
        }

        lore.add(Component.text("Prerequisites:").color(NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true));

        for (String prereqId : skill.prerequisites()) {
            Optional<SkillDefinition> prereqOpt = skillTreeRegistry.getSkill(prereqId);
            if (prereqOpt.isPresent()) {
                SkillDefinition prereq = prereqOpt.get();
                boolean unlocked = guildSkillTree.isUnlocked(prereqId);
                NamedTextColor color = unlocked ? NamedTextColor.GREEN : NamedTextColor.RED;
                String symbol = unlocked ? "✓" : "✗";
                lore.add(Component.text(symbol + " " + prereq.name()).color(color));
            }
        }
    }

    /**
     * Creates a formatted lore line with label and value.
     *
     * @param label the label text
     * @param value the value text
     * @param color the text color
     * @return the formatted component
     */
    private Component createLoreLine(String label, String value, NamedTextColor color) {
        return Component.text(label + ": ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value).color(color));
    }

    /**
     * Extracts skill ID from an item (used for click identification).
     * Currently uses skill name as identifier - can be enhanced with NBT storage.
     *
     * @param item the item stack
     * @return the skill ID or null
     */
    private String extractSkillIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        String displayName = item.getItemMeta().getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            // Find skill by name - improve by storing ID in NBT
            for (SkillDefinition skill : skillTreeRegistry.getAllSkills()) {
                if (skill.name().equals(displayName)) {
                    return skill.id();
                }
            }
        }
        return null;
    }

    /**
     * Handles a click on a skill item.
     * If available, attempts to unlock the skill.
     *
     * @param skillId the skill ID
     */
    private void handleSkillClick(String skillId) {
        Optional<SkillDefinition> skillOpt = skillTreeRegistry.getSkill(skillId);
        if (skillOpt.isEmpty()) {
            return;
        }

        SkillDefinition skill = skillOpt.get();
        if (!guildSkillTree.canUnlock(skill)) {
            return; // Skill not available to unlock
        }

        // Attempt to unlock
        var result = skillTreeService.unlockSkill(guildId, skillId);
        if (result.success()) {
            viewer.sendMessage(Component.text("Unlocked: " + skill.name()).color(NamedTextColor.GREEN));
            // Re-render the GUI to update states by reopening
            renderSkills();
            gui.open(viewer);
        } else {
            viewer.sendMessage(Component.text(result.message()).color(NamedTextColor.RED));
        }
    }

    /**
     * Gets the layout map for testing purposes.
     *
     * @return map of skill ID to position
     */
    protected Map<String, SkillNodePosition> getLayout() {
        return new HashMap<>(layout);
    }
}

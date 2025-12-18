package org.aincraft.skilltree.gui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.Guild;
import org.aincraft.skilltree.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Wynncraft-style skill tree GUI with scrollable branches and branch tabs.
 * Displays a 6-row chest inventory with skill tree, info bar, and controls.
 */
public class SkillTreeGUI {

    private final Guild guild;
    private final Player viewer;
    private final SkillTreeService skillTreeService;
    private final SkillTreeRegistry registry;

    private Gui gui;
    private SkillBranch currentBranch = SkillBranch.ECONOMY;
    private int scrollOffset = 0;
    private ItemStack[] originalInventory;

    // Layout constants
    private static final int VISIBLE_TIERS = 4;
    private static final int SKILL_START_ROW = 0;  // Rows 0-3 (slots 0-35)
    private static final int INFO_ROW = 4;          // Row 4 (slots 36-44)
    private static final int CONTROL_ROW = 5;       // Row 5 (slots 45-53)

    public SkillTreeGUI(Guild guild, Player viewer, SkillTreeService skillTreeService, SkillTreeRegistry registry) {
        this.guild = Objects.requireNonNull(guild, "Guild cannot be null");
        this.viewer = Objects.requireNonNull(viewer, "Viewer cannot be null");
        this.skillTreeService = Objects.requireNonNull(skillTreeService, "SkillTreeService cannot be null");
        this.registry = Objects.requireNonNull(registry, "SkillTreeRegistry cannot be null");
    }

    /**
     * Opens the skill tree GUI for the player.
     */
    public void open() {
        encodePlayerInventory();
        buildGUI();
        gui.open(viewer);
    }

    /**
     * Builds the GUI structure and sets up event handlers.
     */
    private void buildGUI() {
        gui = Gui.gui()
                .title(Component.text("Skill Tree - ")
                        .color(NamedTextColor.DARK_PURPLE)
                        .append(Component.text(currentBranch.getDisplayName())
                                .color(currentBranch.getColor())
                                .decorate(TextDecoration.BOLD)))
                .rows(6)
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));
        gui.setCloseGuiAction(event -> restorePlayerInventory());

        render();
    }

    /**
     * Renders all GUI elements: skill tree, info bar, and controls.
     */
    private void render() {
        gui.clearPageItems();

        renderSkillTree();
        renderInfoBar();
        renderControls();

        gui.update();
    }

    /**
     * Renders the skill tree section (rows 0-3).
     * Displays skills grouped by tier, scrollable with visible tier limit.
     */
    private void renderSkillTree() {
        GuildSkillTree tree = skillTreeService.getOrCreateSkillTree(guild.getId());
        List<SkillDefinition> branchSkills = registry.getSkillsForBranch(currentBranch);

        // Group skills by tier
        Map<Integer, List<SkillDefinition>> tierMap = new LinkedHashMap<>();
        for (SkillDefinition skill : branchSkills) {
            tierMap.computeIfAbsent(skill.tier(), k -> new ArrayList<>()).add(skill);
        }

        // Clear skill area with black glass
        for (int slot = 0; slot < 36; slot++) {
            gui.setItem(slot, ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem());
        }

        // Render visible tiers
        int displayRow = 0;
        for (int tier = scrollOffset + 1; tier <= scrollOffset + VISIBLE_TIERS; tier++) {
            List<SkillDefinition> tierSkills = tierMap.get(tier);
            if (tierSkills != null) {
                renderTier(tierSkills, displayRow, tree);
            }
            displayRow++;
        }
    }

    /**
     * Renders a single tier of skills in the given display row.
     * Skills are centered within the row.
     */
    private void renderTier(List<SkillDefinition> skills, int displayRow, GuildSkillTree tree) {
        int baseSlot = displayRow * 9;

        // Center skills in the row (9 slots per row)
        int startCol = (9 - Math.min(skills.size(), 9)) / 2;

        for (int i = 0; i < skills.size() && i < 9; i++) {
            SkillDefinition skill = skills.get(i);
            int slot = baseSlot + startCol + i;

            GuiItem item = createSkillItem(skill, tree);
            gui.setItem(slot, item);
        }
    }

    /**
     * Creates a clickable GUI item for a skill with appropriate state display.
     */
    private GuiItem createSkillItem(SkillDefinition skill, GuildSkillTree tree) {
        boolean isUnlocked = tree.hasSkill(skill.id());
        boolean canUnlock = skillTreeService.canUnlock(guild.getId(), skill.id());
        boolean hasPrereqs = skillTreeService.hasPrerequisites(guild.getId(), skill.id());

        // Determine material, state text, and color
        Material material;
        NamedTextColor nameColor;
        String stateText;

        if (isUnlocked) {
            material = Material.ENCHANTED_BOOK;
            nameColor = NamedTextColor.GREEN;
            stateText = "UNLOCKED";
        } else if (canUnlock) {
            material = Material.BOOK;
            nameColor = NamedTextColor.YELLOW;
            stateText = "CLICK TO UNLOCK";
        } else if (!hasPrereqs) {
            material = Material.BARRIER;
            nameColor = NamedTextColor.RED;
            stateText = "LOCKED - Missing Prerequisites";
        } else {
            material = Material.BOOK;
            nameColor = NamedTextColor.GRAY;
            stateText = "LOCKED - Not Enough SP";
        }

        // Build lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(skill.description()).color(NamedTextColor.GRAY));
        lore.add(Component.empty());

        // Effect
        lore.add(Component.text("Effect: ").color(NamedTextColor.AQUA)
                .append(Component.text(skill.effect().displayName()).color(NamedTextColor.WHITE)));

        // Cost
        lore.add(Component.text("Cost: ").color(NamedTextColor.GOLD)
                .append(Component.text(skill.spCost() + " SP").color(
                        tree.canAfford(skill.spCost()) ? NamedTextColor.GREEN : NamedTextColor.RED)));

        // Prerequisites
        if (!skill.prerequisites().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("Prerequisites:").color(NamedTextColor.YELLOW));
            for (String prereqId : skill.prerequisites()) {
                boolean hasPre = tree.hasSkill(prereqId);
                String prereqName = registry.getSkill(prereqId).map(SkillDefinition::name).orElse(prereqId);
                lore.add(Component.text("  " + (hasPre ? "✓ " : "✗ ") + prereqName)
                        .color(hasPre ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text(stateText).color(nameColor).decorate(TextDecoration.BOLD));

        var builder = ItemBuilder.from(material)
                .name(Component.text(skill.name()).color(nameColor).decorate(TextDecoration.BOLD))
                .lore(lore)
                .flags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        if (isUnlocked) {
            builder.glow(true);
        }

        return builder.asGuiItem(event -> handleSkillClick(skill, tree, isUnlocked, canUnlock));
    }

    /**
     * Handles clicking on a skill item.
     */
    private void handleSkillClick(SkillDefinition skill, GuildSkillTree tree, boolean isUnlocked, boolean canUnlock) {
        if (!isUnlocked && canUnlock) {
            SkillUnlockResult result = skillTreeService.unlockSkill(guild.getId(), viewer.getUniqueId(), skill.id());
            if (result.success()) {
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                render();
            } else {
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                viewer.sendMessage(Component.text(result.errorMessage()).color(NamedTextColor.RED));
            }
        } else if (isUnlocked) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
        } else {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * Renders the info bar (row 4) with SP display and branch progress.
     */
    private void renderInfoBar() {
        GuildSkillTree tree = skillTreeService.getOrCreateSkillTree(guild.getId());

        // Fill with gray glass
        for (int col = 0; col < 9; col++) {
            int slot = INFO_ROW * 9 + col;
            gui.setItem(slot, ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem());
        }

        // Left side: Branch info (slot 1)
        List<SkillDefinition> skills = registry.getSkillsForBranch(currentBranch);
        int totalSkills = skills.size();
        int unlockedCount = (int) skills.stream().filter(s -> tree.hasSkill(s.id())).count();

        gui.setItem(INFO_ROW * 9 + 1, ItemBuilder.from(currentBranch.getIcon())
                .name(Component.text(currentBranch.getDisplayName()).color(currentBranch.getColor()).decorate(TextDecoration.BOLD))
                .lore(
                        Component.text("Progress: ").color(NamedTextColor.GRAY)
                                .append(Component.text(unlockedCount + "/" + totalSkills).color(NamedTextColor.WHITE)),
                        Component.text(createProgressBar(unlockedCount, totalSkills)).color(NamedTextColor.GREEN)
                )
                .asGuiItem());

        // Center: SP display (slot 4)
        gui.setItem(INFO_ROW * 9 + 4, ItemBuilder.from(Material.EXPERIENCE_BOTTLE)
                .name(Component.text("Skill Points").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .lore(
                        Component.text("Available: ").color(NamedTextColor.GRAY)
                                .append(Component.text(tree.getAvailableSkillPoints() + " SP").color(NamedTextColor.GREEN)),
                        Component.text("Total Earned: ").color(NamedTextColor.GRAY)
                                .append(Component.text(tree.getTotalSkillPointsEarned() + " SP").color(NamedTextColor.GOLD))
                )
                .asGuiItem());
    }

    /**
     * Renders the control bar (row 5) with branch tabs, scroll buttons, and respec/close.
     */
    private void renderControls() {
        // Fill with black glass
        for (int col = 0; col < 9; col++) {
            int slot = CONTROL_ROW * 9 + col;
            gui.setItem(slot, ItemBuilder.from(Material.BLACK_STAINED_GLASS_PANE)
                    .name(Component.empty())
                    .asGuiItem());
        }

        // Branch tabs (slots 45-47)
        renderBranchTabs();

        // Scroll buttons (slots 49-50)
        renderScrollButtons();

        // Respec button (slot 52)
        renderRespecButton();

        // Close button (slot 53)
        gui.setItem(53, ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Close").color(NamedTextColor.RED))
                .asGuiItem(event -> viewer.closeInventory()));
    }

    /**
     * Renders the branch tab buttons.
     */
    private void renderBranchTabs() {
        for (SkillBranch branch : SkillBranch.values()) {
            int slot = 45 + branch.ordinal();
            boolean isActive = branch == currentBranch;

            gui.setItem(slot, ItemBuilder.from(branch.getIcon())
                    .name(Component.text(branch.getDisplayName())
                            .color(isActive ? branch.getColor() : NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, isActive))
                    .lore(isActive
                            ? Component.text("Currently viewing").color(NamedTextColor.GREEN)
                            : Component.text("Click to view").color(NamedTextColor.YELLOW))
                    .glow(isActive)
                    .asGuiItem(event -> {
                        if (branch != currentBranch) {
                            currentBranch = branch;
                            scrollOffset = 0;
                            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                            buildGUI();
                            gui.open(viewer);
                        }
                    }));
        }
    }

    /**
     * Renders the scroll up/down buttons.
     */
    private void renderScrollButtons() {
        int maxTier = registry.getMaxTier(currentBranch);

        // Scroll Up (slot 49)
        boolean canScrollUp = scrollOffset > 0;
        gui.setItem(49, ItemBuilder.from(canScrollUp ? Material.ARROW : Material.GRAY_DYE)
                .name(Component.text("Scroll Up").color(canScrollUp ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .asGuiItem(event -> {
                    if (canScrollUp) {
                        scrollOffset--;
                        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                        render();
                    }
                }));

        // Scroll Down (slot 50)
        boolean canScrollDown = scrollOffset + VISIBLE_TIERS < maxTier;
        gui.setItem(50, ItemBuilder.from(canScrollDown ? Material.ARROW : Material.GRAY_DYE)
                .name(Component.text("Scroll Down").color(canScrollDown ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .asGuiItem(event -> {
                    if (canScrollDown) {
                        scrollOffset++;
                        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                        render();
                    }
                }));
    }

    /**
     * Renders the respec button (slot 52).
     */
    private void renderRespecButton() {
        if (!registry.isRespecEnabled()) {
            return;
        }

        GuildSkillTree tree = skillTreeService.getOrCreateSkillTree(guild.getId());
        boolean hasSkills = !tree.getUnlockedSkillIds().isEmpty();

        gui.setItem(52, ItemBuilder.from(Material.CAULDRON)
                .name(Component.text("Respec Skills").color(hasSkills ? NamedTextColor.RED : NamedTextColor.GRAY).decorate(TextDecoration.BOLD))
                .lore(
                        Component.text("Reset all skills and refund SP").color(NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Cost: ").color(NamedTextColor.GOLD)
                                .append(Component.text(registry.getRespecAmount() + "x ").color(NamedTextColor.WHITE))
                                .append(Component.text(formatMaterialName(registry.getRespecMaterial())).color(NamedTextColor.AQUA)),
                        Component.empty(),
                        hasSkills
                                ? Component.text("Click to respec").color(NamedTextColor.YELLOW)
                                : Component.text("No skills to reset").color(NamedTextColor.GRAY)
                )
                .asGuiItem(event -> {
                    if (hasSkills) {
                        RespecResult result = skillTreeService.respec(guild.getId(), viewer.getUniqueId());
                        if (result.success()) {
                            viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
                            viewer.sendMessage(Component.text("Skills reset! Refunded " + result.refundedPoints() + " SP").color(NamedTextColor.GREEN));
                            render();
                        } else {
                            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            viewer.sendMessage(Component.text(result.errorMessage()).color(NamedTextColor.RED));
                        }
                    }
                }));
    }

    /**
     * Encodes the player's inventory with gray glass panes while viewing the GUI.
     */
    private void encodePlayerInventory() {
        originalInventory = viewer.getInventory().getContents().clone();

        ItemStack filler = ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();

        for (int i = 0; i < 36; i++) {
            viewer.getInventory().setItem(i, filler);
        }
    }

    /**
     * Restores the player's original inventory contents.
     */
    private void restorePlayerInventory() {
        if (originalInventory != null) {
            viewer.getInventory().setContents(originalInventory);
        }
    }

    /**
     * Creates a progress bar string (e.g., "[■■■□□□□□□□]").
     */
    private String createProgressBar(int current, int total) {
        int bars = 10;
        int filled = total > 0 ? (current * bars) / total : 0;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "■" : "□");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Formats a material name for display (e.g., NETHER_STAR -> "Nether Star").
     */
    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}

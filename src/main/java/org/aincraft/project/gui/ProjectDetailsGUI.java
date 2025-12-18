package org.aincraft.project.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.Guild;
import org.aincraft.project.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProjectDetailsGUI implements InventoryHolder {

    private static final int INVENTORY_SIZE = 54;

    private final Guild guild;
    private final Player viewer;
    private final ProjectService projectService;
    private final ProjectDefinition definition;
    private final GuildProject project;
    private final boolean isActive;
    private final Inventory inventory;

    public ProjectDetailsGUI(Guild guild, Player viewer, ProjectService projectService,
                             ProjectDefinition definition, GuildProject project) {
        this.guild = guild;
        this.viewer = viewer;
        this.projectService = projectService;
        this.definition = definition;
        this.project = project;
        this.isActive = project != null && project.getStatus() == ProjectStatus.IN_PROGRESS;

        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text(definition.name()).color(NamedTextColor.DARK_PURPLE));
        renderInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Guild getGuild() {
        return guild;
    }

    public Player getViewer() {
        return viewer;
    }

    public ProjectDefinition getDefinition() {
        return definition;
    }

    public GuildProject getProject() {
        return project;
    }

    public boolean isActive() {
        return isActive;
    }

    public void renderInventory() {
        inventory.clear();

        // Project info header (slot 4)
        inventory.setItem(4, createProjectInfoItem());

        // Buff preview (slot 13)
        inventory.setItem(13, createBuffPreviewItem());

        // Quest progress (slots 19-26)
        int questSlot = 19;
        for (QuestRequirement quest : definition.quests()) {
            if (questSlot > 26) break;
            long current = isActive ? project.getQuestProgress(quest.id()) : 0;
            inventory.setItem(questSlot, createQuestItem(quest, current));
            questSlot++;
        }

        // Material requirements (slots 28-35)
        int materialSlot = 28;
        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            if (materialSlot > 35) break;
            int contributed = isActive ? project.getMaterialContributed(entry.getKey()) : 0;
            inventory.setItem(materialSlot, createMaterialItem(entry.getKey(), entry.getValue(), contributed));
            materialSlot++;
        }

        // Overall progress bar (slot 40)
        if (isActive) {
            double progress = projectService.calculateProgress(project);
            inventory.setItem(40, createProgressBarItem(progress));
        }

        // Action buttons (bottom row)
        inventory.setItem(45, createButton(Material.ARROW, "Back", NamedTextColor.YELLOW));

        if (isActive) {
            inventory.setItem(47, createButton(Material.CHEST, "Contribute Materials", NamedTextColor.AQUA));

            boolean isComplete = projectService.isProjectComplete(project);
            if (isComplete) {
                inventory.setItem(49, createButton(Material.EMERALD_BLOCK, "Complete Project!", NamedTextColor.GREEN));
            } else {
                ItemStack incomplete = createButton(Material.GRAY_WOOL, "Not Complete Yet", NamedTextColor.GRAY);
                inventory.setItem(49, incomplete);
            }

            inventory.setItem(51, createButton(Material.RED_WOOL, "Abandon Project", NamedTextColor.RED));
        } else {
            inventory.setItem(49, createButton(Material.LIME_WOOL, "Start Project", NamedTextColor.GREEN));
        }
    }

    private ItemStack createProjectInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(definition.name()).color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(definition.description()).color(NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Required Level: " + definition.requiredLevel()).color(NamedTextColor.AQUA));
        lore.add(Component.text("Buff Type: " + definition.buffType().name()).color(NamedTextColor.LIGHT_PURPLE));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBuffPreviewItem() {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Buff Reward").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(definition.buff().displayName()).color(NamedTextColor.WHITE));
        lore.add(Component.text("Category: " + definition.buff().categoryId()).color(NamedTextColor.GRAY));
        lore.add(Component.text("Value: " + definition.buff().value()).color(NamedTextColor.AQUA));
        lore.add(Component.text("Duration: " + formatDuration(definition.buffDurationMillis())).color(NamedTextColor.YELLOW));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuestItem(QuestRequirement quest, long current) {
        boolean complete = current >= quest.targetCount();
        Material material = complete ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color = complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        meta.displayName(Component.text(quest.description()).color(color));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Progress: " + current + " / " + quest.targetCount()).color(NamedTextColor.GRAY));

        // Progress bar
        String progressBar = createTextProgressBar(current, quest.targetCount());
        lore.add(Component.text(progressBar).color(complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        if (complete) {
            lore.add(Component.text("COMPLETE").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMaterialItem(Material material, int required, int contributed) {
        boolean complete = contributed >= required;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color = complete ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        meta.displayName(Component.text(formatMaterialName(material)).color(color));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Contributed: " + contributed + " / " + required).color(NamedTextColor.GRAY));

        String progressBar = createTextProgressBar(contributed, required);
        lore.add(Component.text(progressBar).color(complete ? NamedTextColor.GREEN : NamedTextColor.AQUA));

        if (complete) {
            lore.add(Component.text("COMPLETE").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        } else {
            lore.add(Component.text("Need " + (required - contributed) + " more").color(NamedTextColor.RED));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProgressBarItem(double progress) {
        Material material = progress >= 1.0 ? Material.EMERALD_BLOCK : Material.EXPERIENCE_BOTTLE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color = progress >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.GOLD;
        meta.displayName(Component.text("Overall Progress").color(color).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("%.1f%% Complete", progress * 100)).color(NamedTextColor.WHITE));
        lore.add(Component.text(createTextProgressBar(progress)).color(color));

        if (progress >= 1.0) {
            lore.add(Component.empty());
            lore.add(Component.text("Ready to complete!").color(NamedTextColor.GREEN));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material material, String text, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text).color(color));
        item.setItemMeta(meta);
        return item;
    }

    private String createTextProgressBar(long current, long max) {
        return createTextProgressBar((double) current / max);
    }

    private String createTextProgressBar(double progress) {
        int bars = 20;
        int filled = (int) (progress * bars);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "■" : "□");
        }
        sb.append("]");
        return sb.toString();
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;

        if (days > 0) {
            return days + " days";
        } else {
            return hours + " hours";
        }
    }
}

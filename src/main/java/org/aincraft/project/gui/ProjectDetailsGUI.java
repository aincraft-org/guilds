package org.aincraft.project.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.aincraft.Guild;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.project.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * GUI for displaying detailed project information including quests, materials, and progress.
 * Uses Triumph GUI library for inventory management with inline click handlers.
 */
public class ProjectDetailsGUI {

    private final Guild guild;
    private final Player viewer;
    private final ProjectService projectService;
    private final ProjectRegistry registry;
    private final ProjectDefinition definition;
    private final GuildProject project;
    private final boolean isActive;
    private final int guildLevel;
    private final Gui gui;
    private final Map<Material, Integer> vaultAvailability;

    public ProjectDetailsGUI(Guild guild, Player viewer, ProjectService projectService, ProjectRegistry registry,
                             ProjectDefinition definition, GuildProject project, int guildLevel) {
        this.guild = guild;
        this.viewer = viewer;
        this.projectService = projectService;
        this.registry = registry;
        this.definition = definition;
        this.project = project;
        this.isActive = project != null && project.getStatus() == ProjectStatus.IN_PROGRESS;
        this.guildLevel = guildLevel;
        this.vaultAvailability = isActive ? projectService.calculateAvailableMaterials(guild.getId()) : Map.of();

        this.gui = Gui.gui()
                .title(Component.text(definition.name()).color(NamedTextColor.DARK_PURPLE))
                .rows(6)
                .create();

        this.gui.setDefaultClickAction(event -> event.setCancelled(true));

        renderInventory();
    }

    public void open() {
        gui.open(viewer);
    }

    private void renderInventory() {
        // Project info header (slot 4)
        gui.setItem(4, createProjectInfoItem());

        // Buff preview (slot 13)
        gui.setItem(13, createBuffPreviewItem());

        // Quest progress (slots 19-26)
        int questSlot = 19;
        for (QuestRequirement quest : definition.quests()) {
            if (questSlot > 26) break;
            long current = isActive ? project.getQuestProgress(quest.id()) : 0;
            gui.setItem(questSlot, createQuestItem(quest, current));
            questSlot++;
        }

        // Material requirements (slots 28-35)
        int materialSlot = 28;
        for (Map.Entry<Material, Integer> entry : definition.materials().entrySet()) {
            if (materialSlot > 35) break;
            int inVault = vaultAvailability.getOrDefault(entry.getKey(), 0);
            gui.setItem(materialSlot, createMaterialItem(entry.getKey(), entry.getValue(), inVault));
            materialSlot++;
        }

        // Overall progress bar (slot 40)
        if (isActive) {
            double progress = projectService.calculateProgress(project);
            gui.setItem(40, createProgressBarItem(progress));
        }

        // Action buttons (bottom row)
        setupActionButtons();
    }

    private void setupActionButtons() {
        // Back button
        gui.setItem(45, ItemBuilder.from(Material.ARROW)
                .name(Component.text("Back").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                .asGuiItem(event -> {
                    ProjectListGUI listGUI = new ProjectListGUI(guild, viewer, projectService, registry, guildLevel);
                    listGUI.open();
                }));

        if (isActive) {
            // Complete or incomplete project button
            boolean isComplete = projectService.isProjectComplete(project);
            if (isComplete) {
                gui.setItem(49, ItemBuilder.from(Material.EMERALD_BLOCK)
                        .name(Component.text("Complete Project!").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
                        .asGuiItem(event -> {
                            ProjectService.ProjectCompletionResult result =
                                    projectService.completeProject(guild.getId(), viewer.getUniqueId());

                            if (result.success()) {
                                Messages.send(viewer, MessageKey.PROJECT_COMPLETED);
                                viewer.closeInventory();
                            } else {
                                viewer.sendMessage(MiniMessage.miniMessage().deserialize(result.errorMessage()));
                            }
                        }));
            } else {
                gui.setItem(49, ItemBuilder.from(Material.GRAY_WOOL)
                        .name(Component.text("Not Complete Yet").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                        .asGuiItem());
            }

            // Abandon project button
            gui.setItem(51, ItemBuilder.from(Material.RED_WOOL)
                    .name(Component.text("Abandon Project").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                    .asGuiItem(event -> {
                        boolean abandoned = projectService.abandonProject(guild.getId(), viewer.getUniqueId());

                        if (abandoned) {
                            Messages.send(viewer, MessageKey.PROJECT_ABANDONED);
                            viewer.closeInventory();
                        } else {
                            Messages.send(viewer, MessageKey.ERROR_NO_PERMISSION);
                        }
                    }));
        } else {
            // Start project button
            gui.setItem(49, ItemBuilder.from(Material.LIME_WOOL)
                    .name(Component.text("Start Project").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
                    .asGuiItem(event -> {
                        ProjectService.ProjectStartResult result =
                                projectService.startProject(guild.getId(), viewer.getUniqueId(), definition.id());

                        if (result.success()) {
                            Messages.send(viewer, MessageKey.PROJECT_STARTED, definition.name());

                            // Open updated details GUI
                            ProjectDetailsGUI newGUI = new ProjectDetailsGUI(
                                    guild, viewer, projectService, registry, definition, result.project(), guildLevel);
                            newGUI.open();
                        } else {
                            viewer.sendMessage(MiniMessage.miniMessage().deserialize(result.errorMessage()));
                        }
                    }));
        }
    }

    private dev.triumphteam.gui.guis.GuiItem createProjectInfoItem() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(definition.description()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Required Level: " + definition.requiredLevel()).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Buff Type: " + definition.buffType().name()).color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));

        return ItemBuilder.from(Material.BOOK)
                .name(Component.text(definition.name()).color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private dev.triumphteam.gui.guis.GuiItem createBuffPreviewItem() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(definition.buff().displayName()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Category: " + definition.buff().categoryId()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Value: " + definition.buff().value()).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Duration: " + formatDuration(definition.buffDurationMillis())).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        return ItemBuilder.from(Material.BEACON)
                .name(Component.text("Buff Reward").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private dev.triumphteam.gui.guis.GuiItem createQuestItem(QuestRequirement quest, long current) {
        boolean complete = current >= quest.targetCount();
        Material material = complete ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE;
        NamedTextColor color = complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Progress: " + current + " / " + quest.targetCount()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        // Progress bar
        String progressBar = createTextProgressBar(current, quest.targetCount());
        lore.add(Component.text(progressBar).color(complete ? NamedTextColor.GREEN : NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        if (complete) {
            lore.add(Component.text("COMPLETE").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        }

        return ItemBuilder.from(material)
                .name(Component.text(quest.description()).color(color).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private dev.triumphteam.gui.guis.GuiItem createMaterialItem(Material material, int required, int inVault) {
        boolean hasEnough = inVault >= required;
        NamedTextColor nameColor = hasEnough ? NamedTextColor.GREEN : NamedTextColor.AQUA;

        List<Component> lore = new ArrayList<>();

        // Show vault contents vs required
        NamedTextColor vaultColor = hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED;
        lore.add(Component.text("In Vault: " + inVault + " / " + required)
                .color(vaultColor)
                .decoration(TextDecoration.ITALIC, false));

        // Progress bar based on vault contents
        String progressBar = createTextProgressBar(Math.min(inVault, required), required);
        lore.add(Component.text(progressBar)
                .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        if (hasEnough) {
            lore.add(Component.text("Materials available!").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));
        } else {
            int stillNeeded = required - inVault;
            lore.add(Component.text("Need " + stillNeeded + " more in vault").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        return ItemBuilder.from(material)
                .name(Component.text(formatMaterialName(material)).color(nameColor).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private dev.triumphteam.gui.guis.GuiItem createProgressBarItem(double progress) {
        Material material = progress >= 1.0 ? Material.EMERALD_BLOCK : Material.EXPERIENCE_BOTTLE;
        NamedTextColor color = progress >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.GOLD;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("%.1f%% Complete", progress * 100)).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(createTextProgressBar(progress)).color(color).decoration(TextDecoration.ITALIC, false));

        if (progress >= 1.0) {
            lore.add(Component.empty());
            lore.add(Component.text("Ready to complete!").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }

        return ItemBuilder.from(material)
                .name(Component.text("Overall Progress").color(color).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
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

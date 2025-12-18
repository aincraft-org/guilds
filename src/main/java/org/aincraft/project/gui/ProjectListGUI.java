package org.aincraft.project.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.Guild;
import org.aincraft.project.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * GUI for displaying the list of available guild projects.
 * Uses Triumph GUI library for inventory management with inline click handlers.
 */
public class ProjectListGUI {

    private final Guild guild;
    private final Player viewer;
    private final ProjectService projectService;
    private final ProjectRegistry registry;
    private final int guildLevel;
    private final Gui gui;

    public ProjectListGUI(Guild guild, Player viewer, ProjectService projectService, ProjectRegistry registry, int guildLevel) {
        this.guild = guild;
        this.viewer = viewer;
        this.projectService = projectService;
        this.registry = registry;
        this.guildLevel = guildLevel;

        this.gui = Gui.gui()
                .title(Component.text("Guild Projects").color(NamedTextColor.DARK_PURPLE))
                .rows(6)
                .create();

        this.gui.setDefaultClickAction(event -> event.setCancelled(true));

        renderInventory();
    }

    public void open() {
        gui.open(viewer);
    }

    private void renderInventory() {
        // Get available projects
        List<ProjectDefinition> availableProjects = projectService.getAvailableProjects(guild.getId());
        Optional<GuildProject> activeProject = projectService.getActiveProject(guild.getId());
        Optional<ActiveBuff> activeBuff = projectService.getActiveBuff(guild.getId());

        // Header info
        gui.setItem(4, createInfoItem(activeProject.orElse(null), activeBuff.orElse(null)));

        // Display available projects (slots 19-25)
        int slot = 19;
        for (ProjectDefinition project : availableProjects) {
            if (slot > 25) break;

            boolean isActive = activeProject.isPresent() &&
                    activeProject.get().getProjectDefinitionId().equals(project.id());
            boolean isLocked = guildLevel < project.requiredLevel();

            gui.setItem(slot, createProjectItem(project, isActive, isLocked, activeProject.orElse(null)));
            slot++;
        }

        // Active buff display
        if (activeBuff.isPresent()) {
            gui.setItem(40, createBuffItem(activeBuff.get()));
        }

        // Navigation - Close button
        gui.setItem(45, ItemBuilder.from(Material.BARRIER)
                .name(Component.text("Close").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                .asGuiItem(event -> viewer.closeInventory()));

        // View Active Buff button
        if (activeBuff.isPresent()) {
            gui.setItem(49, ItemBuilder.from(Material.GLOWSTONE)
                    .name(Component.text("View Active Buff").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
                    .asGuiItem(event -> {
                        BuffStatusGUI buffGUI = new BuffStatusGUI(guild, viewer, activeBuff.orElse(null), registry);
                        buffGUI.open();
                    }));
        }
    }

    private dev.triumphteam.gui.guis.GuiItem createInfoItem(GuildProject activeProject, ActiveBuff activeBuff) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Guild Level: " + guildLevel).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

        if (activeProject != null) {
            double progress = projectService.calculateProgress(activeProject);
            lore.add(Component.empty());
            lore.add(Component.text("Active Project: ").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(activeProject.getProjectDefinitionId()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
            lore.add(Component.text("Progress: " + String.format("%.1f%%", progress * 100)).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("No active project").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        if (activeBuff != null && !activeBuff.isExpired()) {
            lore.add(Component.empty());
            lore.add(Component.text("Active Buff: ").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(activeBuff.categoryId()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
            lore.add(Component.text("Time Left: " + formatDuration(activeBuff.getRemainingMillis())).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        return ItemBuilder.from(Material.BOOK)
                .name(Component.text("Guild Projects").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private dev.triumphteam.gui.guis.GuiItem createProjectItem(ProjectDefinition project, boolean isActive, boolean isLocked, GuildProject activeProjectState) {
        Material material;
        NamedTextColor color;

        if (isActive) {
            material = Material.FILLED_MAP;
            color = NamedTextColor.GOLD;
        } else if (isLocked) {
            material = Material.MAP;
            color = NamedTextColor.RED;
        } else {
            material = Material.FILLED_MAP;
            color = NamedTextColor.GREEN;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(project.description()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        // Buff type
        String buffTypeDisplay = project.buffType() == BuffType.GLOBAL ? "Global" : "Territorial";
        lore.add(Component.text("Type: ").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(buffTypeDisplay).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());

        // Material costs
        if (!project.materials().isEmpty()) {
            lore.add(Component.text("Materials:").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            for (Map.Entry<Material, Integer> entry : project.materials().entrySet()) {
                String materialName = formatMaterialName(entry.getKey());
                lore.add(Component.text("  " + entry.getValue() + "x " + materialName)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }

        // Buff info
        lore.add(Component.text("Buff: ").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(project.buff().displayName()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Duration: " + formatDuration(project.buffDurationMillis())).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (isLocked) {
            lore.add(Component.text("Requires Guild Level " + project.requiredLevel()).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            // Locked projects should not be clickable
            return ItemBuilder.from(material)
                    .name(Component.text(project.name()).color(color).decoration(TextDecoration.ITALIC, false))
                    .lore(lore)
                    .asGuiItem();
        } else if (isActive) {
            if (activeProjectState != null) {
                double progress = projectService.calculateProgress(activeProjectState);
                lore.add(Component.text("Progress: " + String.format("%.1f%%", progress * 100)).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("Click to view details").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click to start this project").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }

        // Add click handler for unlocked projects
        return ItemBuilder.from(material)
                .name(Component.text(project.name()).color(color).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem(event -> {
                    Optional<GuildProject> activeProject = projectService.getActiveProject(guild.getId());

                    // Check if this is the active project or a new one
                    GuildProject projectState = null;
                    if (activeProject.isPresent() &&
                            activeProject.get().getProjectDefinitionId().equals(project.id())) {
                        projectState = activeProject.get();
                    }

                    // Open details GUI
                    ProjectDetailsGUI detailsGUI = new ProjectDetailsGUI(
                            guild, viewer, projectService, registry, project, projectState, guildLevel);
                    detailsGUI.open();
                });
    }

    private dev.triumphteam.gui.guis.GuiItem createBuffItem(ActiveBuff buff) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(buff.categoryId()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Value: " + buff.value()).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Time Left: " + formatDuration(buff.getRemainingMillis())).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        return ItemBuilder.from(Material.GLOWSTONE)
                .name(Component.text("Active Buff").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
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
}

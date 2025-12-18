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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ProjectListGUI implements InventoryHolder {

    private static final int INVENTORY_SIZE = 54;

    private final Guild guild;
    private final Player viewer;
    private final ProjectService projectService;
    private final int guildLevel;
    private final Inventory inventory;

    public ProjectListGUI(Guild guild, Player viewer, ProjectService projectService, int guildLevel) {
        this.guild = guild;
        this.viewer = viewer;
        this.projectService = projectService;
        this.guildLevel = guildLevel;

        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Guild Projects").color(NamedTextColor.DARK_PURPLE));
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

    public void renderInventory() {
        inventory.clear();

        // Get available projects
        List<ProjectDefinition> availableProjects = projectService.getAvailableProjects(guild.getId());
        Optional<GuildProject> activeProject = projectService.getActiveProject(guild.getId());
        Optional<ActiveBuff> activeBuff = projectService.getActiveBuff(guild.getId());

        // Header info
        ItemStack infoItem = createInfoItem(activeProject.orElse(null), activeBuff.orElse(null));
        inventory.setItem(4, infoItem);

        // Display available projects (slots 19-25)
        int slot = 19;
        for (ProjectDefinition project : availableProjects) {
            if (slot > 25) break;

            boolean isActive = activeProject.isPresent() &&
                    activeProject.get().getProjectDefinitionId().equals(project.id());
            boolean isLocked = guildLevel < project.requiredLevel();

            ItemStack projectItem = createProjectItem(project, isActive, isLocked, activeProject.orElse(null));
            inventory.setItem(slot, projectItem);
            slot++;
        }

        // Active buff display
        if (activeBuff.isPresent()) {
            inventory.setItem(40, createBuffItem(activeBuff.get()));
        }

        // Navigation
        inventory.setItem(45, createButton(Material.BARRIER, "Close", NamedTextColor.RED));

        if (activeBuff.isPresent()) {
            inventory.setItem(49, createButton(Material.GLOWSTONE, "View Active Buff", NamedTextColor.GOLD));
        }
    }

    private ItemStack createInfoItem(GuildProject activeProject, ActiveBuff activeBuff) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Guild Projects").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Guild Level: " + guildLevel).color(NamedTextColor.AQUA));

        if (activeProject != null) {
            double progress = projectService.calculateProgress(activeProject);
            lore.add(Component.empty());
            lore.add(Component.text("Active Project: ").color(NamedTextColor.YELLOW)
                    .append(Component.text(activeProject.getProjectDefinitionId()).color(NamedTextColor.WHITE)));
            lore.add(Component.text("Progress: " + String.format("%.1f%%", progress * 100)).color(NamedTextColor.GREEN));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("No active project").color(NamedTextColor.GRAY));
        }

        if (activeBuff != null && !activeBuff.isExpired()) {
            lore.add(Component.empty());
            lore.add(Component.text("Active Buff: ").color(NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(activeBuff.category().name()).color(NamedTextColor.WHITE)));
            lore.add(Component.text("Time Left: " + formatDuration(activeBuff.getRemainingMillis())).color(NamedTextColor.GRAY));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProjectItem(ProjectDefinition project, boolean isActive, boolean isLocked, GuildProject activeProjectState) {
        Material material;
        NamedTextColor color;

        if (isActive) {
            material = Material.GOLD_BLOCK;
            color = NamedTextColor.GOLD;
        } else if (isLocked) {
            material = Material.RED_WOOL;
            color = NamedTextColor.RED;
        } else {
            material = Material.LIME_WOOL;
            color = NamedTextColor.GREEN;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(project.name()).color(color).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(project.description()).color(NamedTextColor.GRAY));
        lore.add(Component.empty());

        // Buff info
        lore.add(Component.text("Buff: ").color(NamedTextColor.YELLOW)
                .append(Component.text(project.buff().displayName()).color(NamedTextColor.WHITE)));
        lore.add(Component.text("Duration: " + formatDuration(project.buffDurationMillis())).color(NamedTextColor.GRAY));
        lore.add(Component.empty());

        if (isLocked) {
            lore.add(Component.text("Requires Guild Level " + project.requiredLevel()).color(NamedTextColor.RED));
        } else if (isActive) {
            if (activeProjectState != null) {
                double progress = projectService.calculateProgress(activeProjectState);
                lore.add(Component.text("Progress: " + String.format("%.1f%%", progress * 100)).color(NamedTextColor.GREEN));
            }
            lore.add(Component.text("Click to view details").color(NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("Click to start this project").color(NamedTextColor.GREEN));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBuffItem(ActiveBuff buff) {
        ItemStack item = new ItemStack(Material.GLOWSTONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Active Buff").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(buff.category().name()).color(NamedTextColor.WHITE));
        lore.add(Component.text("Value: " + buff.value()).color(NamedTextColor.AQUA));
        lore.add(Component.text("Time Left: " + formatDuration(buff.getRemainingMillis())).color(NamedTextColor.GRAY));

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
}

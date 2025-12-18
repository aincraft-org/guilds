package org.aincraft.project.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.Guild;
import org.aincraft.project.ActiveBuff;
import org.aincraft.project.ProjectRegistry;
import org.aincraft.project.ProjectService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GUI for displaying active buff status.
 * Uses Triumph GUI library for inventory management with inline click handlers.
 */
public class BuffStatusGUI {

    private final Guild guild;
    private final Player viewer;
    private final ActiveBuff activeBuff;
    private final ProjectRegistry registry;
    private final ProjectService projectService;
    private final int guildLevel;
    private final Gui gui;

    public BuffStatusGUI(Guild guild, Player viewer, ActiveBuff activeBuff, ProjectRegistry registry) {
        this(guild, viewer, activeBuff, registry, null, 1);
    }

    public BuffStatusGUI(Guild guild, Player viewer, ActiveBuff activeBuff, ProjectRegistry registry,
                         ProjectService projectService, int guildLevel) {
        this.guild = guild;
        this.viewer = viewer;
        this.activeBuff = activeBuff;
        this.registry = registry;
        this.projectService = projectService;
        this.guildLevel = guildLevel;

        this.gui = Gui.gui()
                .title(Component.text("Active Buff").color(NamedTextColor.LIGHT_PURPLE))
                .rows(3)
                .create();

        this.gui.setDefaultClickAction(event -> event.setCancelled(true));

        renderInventory();
    }

    public void open() {
        gui.open(viewer);
    }

    private void renderInventory() {
        if (activeBuff == null || activeBuff.isExpired()) {
            // No active buff
            gui.setItem(13, ItemBuilder.from(Material.BARRIER)
                    .name(Component.text("No Active Buff").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                    .lore(
                            Component.text("Complete a project to").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("earn a buff for your guild!").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    )
                    .asGuiItem());
        } else {
            // Display active buff
            gui.setItem(13, createBuffDisplayItem());
        }

        // Back button
        gui.setItem(18, ItemBuilder.from(Material.ARROW)
                .name(Component.text("Back").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                .asGuiItem(event -> {
                    if (projectService != null) {
                        ProjectListGUI listGUI = new ProjectListGUI(guild, viewer, projectService, registry, guildLevel);
                        listGUI.open();
                    } else {
                        viewer.closeInventory();
                    }
                }));
    }

    private dev.triumphteam.gui.guis.GuiItem createBuffDisplayItem() {
        Material material = getBuffMaterial();

        List<Component> lore = new ArrayList<>();

        // Source project name
        registry.getProject(activeBuff.projectDefinitionId()).ifPresent(def -> {
            lore.add(Component.text("From: " + def.name()).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        });

        lore.add(Component.empty());

        // Buff value
        String valueDisplay = formatBuffValue(activeBuff.categoryId(), activeBuff.value());
        lore.add(Component.text("Effect: " + valueDisplay).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

        // Time remaining
        lore.add(Component.empty());
        lore.add(Component.text("Time Remaining:").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(formatDuration(activeBuff.getRemainingMillis())).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

        // Progress bar for time
        double timeProgress = 1.0 - ((double) activeBuff.getRemainingMillis() /
                (activeBuff.expiresAt() - activeBuff.activatedAt()));
        lore.add(Component.text(createTimeBar(timeProgress)).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));

        return ItemBuilder.from(material)
                .name(Component.text(activeBuff.categoryId())
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .asGuiItem();
    }

    private Material getBuffMaterial() {
        return switch (activeBuff.categoryId()) {
            case "XP_MULTIPLIER" -> Material.EXPERIENCE_BOTTLE;
            case "LUCK_BONUS" -> Material.RABBIT_FOOT;
            case "CROP_GROWTH_SPEED" -> Material.WHEAT;
            case "MOB_SPAWN_RATE" -> Material.SPAWNER;
            case "PROTECTION_BOOST" -> Material.SHIELD;
            default -> Material.NETHER_STAR;
        };
    }

    private String formatBuffValue(String category, double value) {
        return switch (category) {
            case "XP_MULTIPLIER" -> String.format("+%.0f%% XP Gain", (value - 1) * 100);
            case "LUCK_BONUS" -> String.format("+%.0f%% Luck", (value - 1) * 100);
            case "CROP_GROWTH_SPEED" -> String.format("+%.0f%% Crop Growth", (value - 1) * 100);
            case "MOB_SPAWN_RATE" -> String.format("+%.0f%% Mob Spawns", (value - 1) * 100);
            case "PROTECTION_BOOST" -> String.format("-%.0f%% Damage Taken", (1 - value) * 100);
            default -> String.format("%.2fx", value);
        };
    }

    private String formatDuration(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m");
        return sb.toString().trim();
    }

    private String createTimeBar(double progress) {
        int bars = 20;
        int elapsed = (int) (progress * bars);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bars; i++) {
            sb.append(i < elapsed ? "□" : "■");
        }
        sb.append("]");
        return sb.toString();
    }
}

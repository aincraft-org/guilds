package org.aincraft.project.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.Guild;
import org.aincraft.project.ActiveBuff;
import org.aincraft.project.ProjectRegistry;
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

public class BuffStatusGUI implements InventoryHolder {

    private static final int INVENTORY_SIZE = 27;

    private final Guild guild;
    private final Player viewer;
    private final ActiveBuff activeBuff;
    private final ProjectRegistry registry;
    private final Inventory inventory;

    public BuffStatusGUI(Guild guild, Player viewer, ActiveBuff activeBuff, ProjectRegistry registry) {
        this.guild = guild;
        this.viewer = viewer;
        this.activeBuff = activeBuff;
        this.registry = registry;

        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Active Buff").color(NamedTextColor.LIGHT_PURPLE));
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

        if (activeBuff == null || activeBuff.isExpired()) {
            // No active buff
            ItemStack noBuffItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = noBuffItem.getItemMeta();
            meta.displayName(Component.text("No Active Buff").color(NamedTextColor.RED));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Complete a project to").color(NamedTextColor.GRAY));
            lore.add(Component.text("earn a buff for your guild!").color(NamedTextColor.GRAY));
            meta.lore(lore);

            noBuffItem.setItemMeta(meta);
            inventory.setItem(13, noBuffItem);
        } else {
            // Display active buff
            inventory.setItem(13, createBuffDisplayItem());
        }

        // Back button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(Component.text("Back").color(NamedTextColor.YELLOW));
        backButton.setItemMeta(backMeta);
        inventory.setItem(18, backButton);
    }

    private ItemStack createBuffDisplayItem() {
        Material material = getBuffMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(activeBuff.category().name())
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();

        // Source project name
        registry.getProject(activeBuff.projectDefinitionId()).ifPresent(def -> {
            lore.add(Component.text("From: " + def.name()).color(NamedTextColor.GRAY));
        });

        lore.add(Component.empty());

        // Buff value
        String valueDisplay = formatBuffValue(activeBuff.category().name(), activeBuff.value());
        lore.add(Component.text("Effect: " + valueDisplay).color(NamedTextColor.AQUA));

        // Time remaining
        lore.add(Component.empty());
        lore.add(Component.text("Time Remaining:").color(NamedTextColor.YELLOW));
        lore.add(Component.text(formatDuration(activeBuff.getRemainingMillis())).color(NamedTextColor.WHITE));

        // Progress bar for time
        double timeProgress = 1.0 - ((double) activeBuff.getRemainingMillis() /
                (activeBuff.expiresAt() - activeBuff.activatedAt()));
        lore.add(Component.text(createTimeBar(timeProgress)).color(NamedTextColor.GREEN));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material getBuffMaterial() {
        return switch (activeBuff.category()) {
            case XP_MULTIPLIER -> Material.EXPERIENCE_BOTTLE;
            case LUCK_BONUS -> Material.RABBIT_FOOT;
            case CROP_GROWTH_SPEED -> Material.WHEAT;
            case MOB_SPAWN_RATE -> Material.SPAWNER;
            case PROTECTION_BOOST -> Material.SHIELD;
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

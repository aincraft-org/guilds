package org.aincraft.towny.gui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.TechTreeNode;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.TechTreeService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.ResidentService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for browsing and unlocking tech tree nodes.
 * 6-row chest inventory (54 slots). Nodes shown as clickable items.
 *   - Green glass = unlocked
 *   - Yellow glass = available (can unlock)
 *   - Gray glass = locked (prerequisites not met)
 */
@Singleton
public class TechTreeGUI implements Listener, InventoryHolder {

    private static final String TITLE = ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Town Tech Tree";
    private static final int ROWS = 6;

    private final TownyPlugin plugin;
    private final TechTreeService techTreeService;
    private final TownService townService;
    private final ResidentService residentService;

    /** Track which player is viewing which town's tree. */
    private final Map<UUID, String> viewerTownIds = new WeakHashMap<>();

    @Inject
    public TechTreeGUI(TownyPlugin plugin, TechTreeService techTreeService,
                       TownService townService, ResidentService residentService) {
        this.plugin = plugin;
        this.techTreeService = techTreeService;
        this.townService = townService;
        this.residentService = residentService;
    }

    /**
     * Open the tech tree GUI for a player.
     */
    public void openTechTree(Player player, Town town) {
        List<TechTreeNode> allNodes = techTreeService.getAllNodes();

        Inventory inv = Bukkit.createInventory(this, ROWS * 9, TITLE);

        // Fill border with black glass
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        for (int i = 0; i < ROWS * 9; i++) {
            inv.setItem(i, border.clone());
        }

        // Place nodes
        for (TechTreeNode node : allNodes) {
            int slot = node.getSlot();
            if (slot < 0 || slot >= ROWS * 9) continue;

            boolean unlocked = techTreeService.isTechNodeUnlocked(town, node.getId());
            boolean available = techTreeService.canUnlockNode(town, node.getId());

            ItemStack item = createNodeItem(node, unlocked, available);
            inv.setItem(slot, item);
        }

        // Tech points display in bottom-right corner
        ItemStack infoItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Tech Points: " + ChatColor.WHITE + town.getTechPoints());
        infoMeta.setLore(List.of(
                ChatColor.GRAY + "Unlocked: " + ChatColor.GREEN + town.getTotalUnlockedTechNodes() + " nodes",
                ChatColor.GRAY + "Click a " + ChatColor.YELLOW + "yellow" + ChatColor.GRAY + " node to unlock"
        ));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(49, infoItem);

        viewerTownIds.put(player.getUniqueId(), town.getId());
        player.openInventory(inv);
    }

    private ItemStack createNodeItem(TechTreeNode node, boolean unlocked, boolean available) {
        Material material;
        String statusPrefix;
        String color;

        if (unlocked) {
            material = Material.LIME_STAINED_GLASS_PANE;
            statusPrefix = ChatColor.GREEN + "✓ ";
            color = ChatColor.GREEN.toString();
        } else if (available) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            statusPrefix = ChatColor.YELLOW + "▸ ";
            color = ChatColor.YELLOW.toString();
        } else {
            material = Material.GRAY_STAINED_GLASS_PANE;
            statusPrefix = ChatColor.DARK_GRAY + "✗ ";
            color = ChatColor.GRAY.toString();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String branchColor = node.getBranch() != null ? node.getBranch().getColorCode() : "§f";
        meta.setDisplayName(statusPrefix + branchColor + node.getName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "ID: " + node.getId());
        lore.add("");
        lore.add(color + node.getDescription());
        lore.add("");

        // Cost
        lore.add(ChatColor.GOLD + "Cost: " + ChatColor.WHITE + node.getCost() + " tech points");

        // Prerequisites
        if (node.getPrerequisites() != null && !node.getPrerequisites().isEmpty()) {
            lore.add("");
            lore.add(ChatColor.DARK_AQUA + "Requires:");
            for (String prereqId : node.getPrerequisites()) {
                techTreeService.getNode(prereqId).ifPresent(prereq -> {
                    boolean prereqMet = true; // will be re-evaluated by caller context; we show all
                    lore.add(ChatColor.GRAY + "  • " + ChatColor.WHITE + prereq.getName());
                });
                if (techTreeService.getNode(prereqId).isEmpty()) {
                    lore.add(ChatColor.GRAY + "  • " + ChatColor.RED + prereqId + " (unknown)");
                }
            }
        }

        // Effects
        if (node.getEffects() != null && !node.getEffects().isEmpty()) {
            lore.add("");
            lore.add(ChatColor.AQUA + "Effects:");
            for (Map.Entry<String, Object> effect : node.getEffects().entrySet()) {
                lore.add(ChatColor.GRAY + "  • " + ChatColor.WHITE + formatEffect(effect.getKey(), effect.getValue()));
            }
        }

        if (available) {
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Click to unlock!");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatEffect(String key, Object value) {
        // Convert snake_case to Title Case
        String[] parts = key.split("_");
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            if (!name.isEmpty()) name.append(" ");
            name.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return name + ": " + value;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TechTreeGUI)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        if (event.getCurrentItem().getType() == Material.NETHER_STAR) return;

        String townId = viewerTownIds.get(player.getUniqueId());
        if (townId == null) return;

        Optional<Town> townOpt = townService.getTown(townId);
        if (townOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Town not found.");
            player.closeInventory();
            return;
        }

        Town town = townOpt.get();

        // Determine which node was clicked by checking the slot
        int slot = event.getRawSlot();
        for (TechTreeNode node : techTreeService.getAllNodes()) {
            if (node.getSlot() == slot) {
                if (techTreeService.isTechNodeUnlocked(town, node.getId())) {
                    player.sendMessage(ChatColor.GREEN + node.getName() + " is already unlocked!");
                } else if (techTreeService.canUnlockNode(town, node.getId())) {
                    boolean success = techTreeService.unlockTechNode(town, node.getId());
                    if (success) {
                        player.sendMessage(ChatColor.GREEN + "✓ Unlocked " + node.getName() + "!");
                        // Refresh GUI
                        openTechTree(player, town);
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to unlock " + node.getName() + ".");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You cannot unlock " + node.getName() + " yet.");
                    player.sendMessage(ChatColor.GRAY + "Check prerequisites and tech points.");
                }
                return;
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return null; // Not a persistent inventory
    }
}

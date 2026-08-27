package dev.mintychochip.guilds.gui;



import org.bukkit.plugin.java.JavaPlugin;
import dev.mintychochip.guilds.models.TechTreeNode;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.TechTreeService;
import dev.mintychochip.guilds.services.GuildProjectService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.guilds.services.ResidentService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * GUI for browsing and unlocking tech tree nodes.
 * 6-row chest inventory (54 slots). Nodes shown as clickable items.
 *   - Green glass = unlocked
 *   - Yellow glass = available (can unlock)
 *   - Gray glass = locked (prerequisites not met)
 */

public class TechTreeGUI implements Listener, InventoryHolder {

    /** The title constant. */
    private static final String TITLE = ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Guild Projects";
    /** The rows constant. */
    private static final int ROWS = 6;

    /** The plugin. */
    private final JavaPlugin plugin;
    /** The tech tree service. */
    private final TechTreeService techTreeService;
    /** The guild project service. */
    private final GuildProjectService guildProjectService;
    /** The guild service. */
    private final GuildService guildService;
    /** The resident service. */
    private final ResidentService residentService;

    /** Track which player is viewing which guild's tree. */
    private final Map<UUID, String> viewerGuildIds = new WeakHashMap<>();


    /**
     * Creates a new tech tree gui instance.
     * @param plugin the plugin
     * @param techTreeService the tech tree service
     * @param guildProjectService the guild project service
     * @param guildService the guild service
     * @param residentService the resident service
     */
    public TechTreeGUI(JavaPlugin plugin, TechTreeService techTreeService,
                       GuildProjectService guildProjectService,
                       GuildService guildService, ResidentService residentService) {
        this.plugin = plugin;
        this.techTreeService = techTreeService;
        this.guildProjectService = guildProjectService;
        this.guildService = guildService;
        this.residentService = residentService;
    }

    /**
     * Open the tech tree GUI for a player.
     */
    public void openTechTree(Player player, Guild guild) {
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

            boolean unlocked = techTreeService.isTechNodeUnlocked(guild, node.getId());
            boolean available = techTreeService.canUnlockNode(guild, node.getId());

            ItemStack item = createNodeItem(node, unlocked, available);
            inv.setItem(slot, item);
        }

        // Tech points display in bottom-right corner
        ItemStack infoItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Tech Points: " + ChatColor.WHITE + guild.getTechPoints());
        infoMeta.setLore(List.of(
                ChatColor.GRAY + "Unlocked: " + ChatColor.GREEN + guild.getTotalUnlockedTechNodes() + " nodes",
                ChatColor.GRAY + "Click a " + ChatColor.YELLOW + "yellow" + ChatColor.GRAY + " node to unlock"
        ));
        infoItem.setItemMeta(infoMeta);
        inv.setItem(49, infoItem);

        viewerGuildIds.put(player.getUniqueId(), guild.getId());
        player.openInventory(inv);
    }

    /**
     * Creates a new node item.
     * @param node the node
     * @param unlocked the unlocked
     * @param available the available
     * @return the result
     */
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

    /**
     * Performs the format effect operation.
     * @param key the key
     * @param value the value
     * @return the result
     */
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

    /**
     * Handles the inventory click.
     * @param event the event
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TechTreeGUI)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        if (event.getCurrentItem().getType() == Material.NETHER_STAR) return;

        String guildId = viewerGuildIds.get(player.getUniqueId());
        if (guildId == null) return;

        Optional<Guild> guildOpt = guildService.getGuild(guildId);
        if (guildOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Guild not found.");
            player.closeInventory();
            return;
        }

        Guild guild = guildOpt.get();

        // Determine which node was clicked by checking the slot
        int slot = event.getRawSlot();
        for (TechTreeNode node : techTreeService.getAllNodes()) {
            if (node.getSlot() == slot) {
                if (techTreeService.isTechNodeUnlocked(guild, node.getId())) {
                    player.sendMessage(ChatColor.GREEN + node.getName() + " is already completed!");
                } else if (node.getId().equals(guild.getActiveProjectId())) {
                    player.sendMessage(ChatColor.YELLOW + node.getName() + " is already the active project.");
                } else {
                    var result = guildProjectService.startProject(guild, node.getId());
                    if (result.isSuccessful()) {
                        player.sendMessage(ChatColor.GREEN + "Started project " + node.getName() + ".");
                        player.sendMessage(ChatColor.GRAY + "Skill points remaining: " + result.getUnspentPoints());
                        openTechTree(player, guild);
                    } else {
                        player.sendMessage(ChatColor.RED + "Cannot start " + node.getName() + ".");
                        player.sendMessage(ChatColor.GRAY + "Need unmet requirements, enough skill points, and no other active project.");
                    }
                }
                return;
            }
        }
    }

    /**
     * Returns the inventory.
     * @return the result
     */
    @Override
    public Inventory getInventory() {
        return null; // Not a persistent inventory
    }
}

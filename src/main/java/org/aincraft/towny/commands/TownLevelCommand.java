package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.models.TownResource;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.aincraft.towny.services.TownLevelService;
import org.aincraft.towny.services.ResourceService;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Town level command handler
 */
public class TownLevelCommand extends TownyCommand implements CommandExecutor, TabCompleter {

    @Inject
    public TownLevelCommand(TownyPlugin plugin, ResidentService residentService, TownService townService,
                            PlotService plotService, PermissionService permissionService,
                            TownLevelService townLevelService, ResourceService resourceService) {
        super(plugin, residentService, townService, plotService, permissionService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "level":
                handleLevel(player, args);
                break;
            case "deposit":
                handleDeposit(player, args);
                break;
            case "bank":
                handleBank(player);
                break;
            case "upgrade":
                handleUpgrade(player);
                break;
            case "contributions":
                handleContributions(player);
                break;
            case "top":
                handleTop(player, args);
                break;
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    /**
     * Handle town level command
     */
    private void handleLevel(Player player, String[] args) {
        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            sendError(player, "You are not in a town!");
            return;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            sendError(player, "Town not found!");
            return;
        }

        Town town = townOpt.get();

        // Show town level information
        sendHelpHeader(player, "Town Level Information");

        player.sendMessage(ChatColor.YELLOW + "Town: " + ChatColor.AQUA + town.getName());
        player.sendMessage(ChatColor.YELLOW + "Current Level: " + ChatColor.GREEN + town.getTownLevel());
        player.sendMessage(ChatColor.YELLOW + "Tech Points: " + ChatColor.LIGHT_PURPLE + town.getTechPoints());
        player.sendMessage(ChatColor.YELLOW + "Claim Limit: " + ChatColor.GREEN + town.getMaxClaimLimit() + " chunks");
        player.sendMessage(ChatColor.YELLOW + "Assistant Slots: " + ChatColor.GREEN + town.getMaxAssistantSlots());
        player.sendMessage(ChatColor.YELLOW + "Daily Income Bonus: " + ChatColor.GOLD + "§" + String.format("%.2f", town.getDailyIncomeBonus()));

        // Show upgrade progress (basic implementation)
        if (town.getTownLevel() < 150) {
            player.sendMessage(ChatColor.YELLOW + "Next Level: " + ChatColor.GREEN + (town.getTownLevel() + 1));
            player.sendMessage(ChatColor.GRAY + "  Progress: " + ChatColor.YELLOW + "Use /town level deposit to contribute resources");
        } else {
            sendSuccess(player, "Your town is at the maximum level!");
        }
    }

    /**
     * Handle town deposit command
     */
    private void handleDeposit(Player player, String[] args) {
        if (args.length < 3) {
            sendError(player, "Usage: /townlevel deposit <resource> <amount>");
            sendInfo(player, "Resources: diamond, gold, iron, emerald, experience");
            return;
        }

        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            sendError(player, "You are not in a town!");
            return;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            sendError(player, "Town not found!");
            return;
        }

        Town town = townOpt.get();
        String resourceType = args[1].toLowerCase();

        if (!isSupportedResource(resourceType)) {
            sendError(player, "Unsupported resource type: " + resourceType);
            sendInfo(player, "Supported resources: diamond, gold, iron, emerald, experience");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sendError(player, "Amount must be positive!");
                return;
            }
        } catch (NumberFormatException e) {
            sendError(player, "Invalid amount! Please use a number.");
            return;
        }

        // Check if player has enough resources
        if (!hasEnoughResources(player, resourceType, amount)) {
            sendError(player, "You don't have enough " + resourceType + " in your inventory!");
            sendInfo(player, "Required: " + amount + ", Available: " + getResourceCount(player, resourceType));
            return;
        }

        // Remove resources from player inventory
        if (!removeResources(player, resourceType, amount)) {
            sendError(player, "Failed to remove resources from your inventory!");
            return;
        }

        // Add to town upgrade progress
        int previousAmount = town.getUpgradeProgress().getOrDefault(resourceType, 0);
        town.contributeToUpgrade(resourceType, amount);
        townService.updateTown(town);

        int newAmount = town.getUpgradeProgress().getOrDefault(resourceType, 0);
        sendSuccess(player, "Successfully contributed " + amount + " " + resourceType + " to town upgrade!");
        sendInfo(player, "Total contributed: " + newAmount + " " + resourceType);

        // Show upgrade progress if applicable
        if (town.getTownLevel() < 150) {
            Map<String, Integer> requirements = getLevelRequirements(town.getTownLevel() + 1);
            if (requirements.containsKey(resourceType)) {
                int required = requirements.get(resourceType);
                if (newAmount >= required) {
                    sendSuccess(player, "You have enough " + resourceType + " for the next level!");
                } else {
                    sendInfo(player, "Progress for next level: " + newAmount + "/" + required + " " + resourceType);
                }
            }
        }
    }

    /**
     * Handle town bank command
     */
    private void handleBank(Player player) {
        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            sendError(player, "You are not in a town!");
            return;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            sendError(player, "Town not found!");
            return;
        }

        Town town = townOpt.get();

        sendHelpHeader(player, "Town Resource Bank");

        Map<String, Integer> progress = town.getUpgradeProgress();
        if (progress.isEmpty()) {
            sendSecondary(player, "No resources contributed yet.");
            sendInfo(player, "Use /town level deposit <resource> <amount> to contribute!");
            return;
        }

        for (Map.Entry<String, Integer> entry : progress.entrySet()) {
            if (entry.getValue() > 0) {
                player.sendMessage(ChatColor.GRAY + entry.getKey().substring(0, 1).toUpperCase() +
                                 entry.getKey().substring(1) + ": " +
                                 ChatColor.GREEN + entry.getValue());
            }
        }

        sendSecondary(player, "");
        sendInfo(player, "Use '/town level' to see upgrade requirements");
    }

    /**
     * Handle town upgrade command
     */
    private void handleUpgrade(Player player) {
        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            sendError(player, "You are not in a town!");
            return;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            sendError(player, "Town not found!");
            return;
        }

        Town town = townOpt.get();

        // Basic upgrade check - simulate upgrade when enough resources are contributed
        Map<String, Integer> required = getLevelRequirements(town.getTownLevel() + 1);
        Map<String, Integer> contributed = town.getUpgradeProgress();

        boolean canUpgrade = true;
        for (Map.Entry<String, Integer> req : required.entrySet()) {
            int has = contributed.getOrDefault(req.getKey(), 0);
            if (has < req.getValue()) {
                canUpgrade = false;
                break;
            }
        }

        if (canUpgrade && town.getTownLevel() < 150) {
            int newLevel = town.getTownLevel() + 1;
            int techPoints = getTechPointsForLevel(newLevel);

            town.levelUp(newLevel, techPoints);
            townService.updateTown(town);

            // Send celebration message
            player.sendMessage("");
            sendHelpHeader(player, "🎉 TOWN UPGRADE COMPLETE! 🎉");
            sendSuccess(player, "Your town has been upgraded to level " + ChatColor.GREEN + newLevel + "!");
            sendInfo(player, "You earned " + ChatColor.LIGHT_PURPLE + techPoints + ChatColor.RESET + " tech points!");

            // Show new benefits
            player.sendMessage(ChatColor.YELLOW + "New Benefits:");
            player.sendMessage(ChatColor.GRAY + "  Claim Limit: " + ChatColor.GREEN + town.getMaxClaimLimit() + " chunks");
            player.sendMessage(ChatColor.GRAY + "  Assistant Slots: " + ChatColor.GREEN + town.getMaxAssistantSlots());
            player.sendMessage(ChatColor.GRAY + "  Daily Income: " + ChatColor.GOLD + "§" + String.format("%.2f", town.getDailyIncomeBonus()));

        } else if (town.getTownLevel() >= 150) {
            sendSuccess(player, "Your town is already at the maximum level!");
        } else {
            sendError(player, "Not enough resources for upgrade!");
            sendInfo(player, "Use '/town level' to see requirements");
        }
    }

    /**
     * Handle town contributions command
     */
    private void handleContributions(Player player) {
        String playerTown = getPlayerTown(player);
        if (playerTown == null) {
            sendError(player, "You are not in a town!");
            return;
        }

        Optional<Town> townOpt = townService.getTown(playerTown);
        if (townOpt.isEmpty()) {
            sendError(player, "Town not found!");
            return;
        }

        Town town = townOpt.get();

        sendHelpHeader(player, "Town Contribution Status");

        Map<String, Integer> contributions = town.getUpgradeProgress();
        if (contributions.isEmpty()) {
            sendSecondary(player, "No resources contributed yet.");
            sendInfo(player, "Use /town level deposit <resource> <amount> to contribute!");
            return;
        }

        int totalContributions = contributions.values().stream().mapToInt(Integer::intValue).sum();

        player.sendMessage(ChatColor.YELLOW + "Total Contributed Items: " + ChatColor.GREEN + totalContributions);
        player.sendMessage(ChatColor.YELLOW + "Town Level: " + ChatColor.GREEN + town.getTownLevel());

        player.sendMessage(ChatColor.YELLOW + "Contributions by Type:");
        for (Map.Entry<String, Integer> entry : contributions.entrySet()) {
            if (entry.getValue() > 0) {
                player.sendMessage(ChatColor.GRAY + "  " + entry.getKey() + ": " + ChatColor.AQUA + entry.getValue());
            }
        }

        // Show player's progress toward next level
        Map<String, Integer> required = getLevelRequirements(town.getTownLevel() + 1);
        if (!required.isEmpty() && town.getTownLevel() < 150) {
            player.sendMessage(ChatColor.YELLOW + "Progress to Level " + (town.getTownLevel() + 1) + ":");
            for (Map.Entry<String, Integer> req : required.entrySet()) {
                int has = contributions.getOrDefault(req.getKey(), 0);
                String status = has >= req.getValue() ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
                player.sendMessage(ChatColor.GRAY + "  " + status + " " + req.getKey() + ": " +
                                 ChatColor.YELLOW + has + ChatColor.GRAY + "/" + ChatColor.YELLOW + req.getValue());
            }
        }
    }

    /**
     * Handle top towns command
     */
    private void handleTop(Player player, String[] args) {
        String criteria = args.length > 1 ? args[1].toLowerCase() : "level";
        int limit = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        List<Town> topTowns = townService.getRankedTowns(criteria, limit);

        sendHelpHeader(player, "Top Towns by " + criteria.substring(0, 1).toUpperCase() + criteria.substring(1));

        for (int i = 0; i < topTowns.size(); i++) {
            Town town = topTowns.get(i);
            String value = switch (criteria) {
                case "level" -> String.valueOf(town.getTownLevel());
                case "residents" -> String.valueOf(town.getResidentCount());
                case "balance" -> String.format("%.2f", town.getBalance());
                case "techpoints" -> String.valueOf(town.getTechPoints());
                default -> String.valueOf(town.getTownLevel());
            };

            player.sendMessage(ChatColor.WHITE + String.valueOf(i + 1) + ". " + ChatColor.AQUA + town.getName() +
                             ChatColor.GRAY + " - " + ChatColor.YELLOW + value);
        }
    }

    /**
     * Show help for town level commands
     */
    private void showHelp(Player player) {
        sendHelpHeader(player, "Town Level Commands");

        sendHelpLine(player, "/townlevel level", "Show your town level and progress");
        sendHelpLine(player, "/townlevel deposit <resource> <amount>", "Contribute resources to upgrade");
        sendHelpLine(player, "/townlevel bank", "View town resource bank");
        sendHelpLine(player, "/townlevel upgrade", "Upgrade town to next level");
        sendHelpLine(player, "/townlevel contributions", "View contribution statistics");
        sendHelpLine(player, "/townlevel top [type] [count]", "Show top towns (level/residents/balance/techpoints)");

        sendSecondary(player, "");
        sendSecondary(player, "Supported Resources: diamond, gold, iron, emerald, experience");
        sendSecondary(player, "Example: /townlevel deposit diamond 10");
        sendSecondary(player, "Aliases: /tl deposit diamond 10");
    }

    /**
     * Get level requirements for a specific level (simplified)
     */
    private Map<String, Integer> getLevelRequirements(int level) {
        Map<String, Integer> requirements = new HashMap<>();

        if (level <= 5) {
            requirements.put("diamond", level * 10);
            requirements.put("gold", level * 20);
        } else if (level <= 10) {
            requirements.put("diamond", level * 20);
            requirements.put("gold", level * 40);
            requirements.put("iron", level * 15);
        } else {
            requirements.put("diamond", level * 50);
            requirements.put("gold", level * 100);
            requirements.put("iron", level * 50);
            if (level >= 10) {
                requirements.put("emerald", level * 5);
            }
            if (level >= 25) {
                requirements.put("experience", level * 25);
            }
        }

        return requirements;
    }

    /**
     * Get tech points for a level
     */
    private int getTechPointsForLevel(int level) {
        if (level <= 9) return 1;
        if (level <= 24) return 2;
        if (level <= 49) return 3;
        if (level <= 99) return 5;
        return 10;
    }

    /**
     * Check if resource type is supported
     */
    private boolean isSupportedResource(String resourceType) {
        return resourceType.equals("diamond") || resourceType.equals("gold") ||
               resourceType.equals("iron") || resourceType.equals("emerald") ||
               resourceType.equals("experience");
    }

    /**
     * Get the Minecraft material for a resource type
     */
    private org.bukkit.Material getMaterialForResource(String resourceType) {
        switch (resourceType) {
            case "diamond":
                return org.bukkit.Material.DIAMOND;
            case "gold":
                return org.bukkit.Material.GOLD_INGOT;
            case "iron":
                return org.bukkit.Material.IRON_INGOT;
            case "emerald":
                return org.bukkit.Material.EMERALD;
            case "experience":
                return org.bukkit.Material.EXPERIENCE_BOTTLE;
            default:
                return null;
        }
    }

    /**
     * Check if player has enough resources
     */
    private boolean hasEnoughResources(Player player, String resourceType, int amount) {
        org.bukkit.Material material = getMaterialForResource(resourceType);
        if (material == null) {
            return false;
        }

        int playerAmount = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                playerAmount += item.getAmount();
            }
        }

        return playerAmount >= amount;
    }

    /**
     * Get the count of a specific resource in player's inventory
     */
    private int getResourceCount(Player player, String resourceType) {
        org.bukkit.Material material = getMaterialForResource(resourceType);
        if (material == null) {
            return 0;
        }

        int count = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * Remove resources from player's inventory
     */
    private boolean removeResources(Player player, String resourceType, int amount) {
        org.bukkit.Material material = getMaterialForResource(resourceType);
        if (material == null) {
            return false;
        }

        int remaining = amount;
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            org.bukkit.inventory.ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int stackAmount = item.getAmount();
                if (stackAmount <= remaining) {
                    remaining -= stackAmount;
                    contents[i] = null;
                } else {
                    item.setAmount(stackAmount - remaining);
                    remaining = 0;
                }
            }
        }

        player.getInventory().setContents(contents);
        player.updateInventory();

        return remaining == 0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = Arrays.asList("level", "deposit", "bank", "upgrade", "contributions", "top");

        if (args.length == 1) {
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("deposit")) {
                return Arrays.asList("diamond", "gold", "iron", "emerald", "experience").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("top")) {
                return Arrays.asList("level", "residents", "balance", "techpoints").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("deposit") && isSupportedResource(args[1])) {
                return Arrays.asList("1", "5", "10", "25", "50", "100").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("top")) {
                return Arrays.asList("5", "10", "15", "20").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return null;
    }
}
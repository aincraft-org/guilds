package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PermissionService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Town command handler
 */
public class TownCommand implements CommandExecutor, TabCompleter {

    private final TownyPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PermissionService permissionService;

    @Inject
    public TownCommand(TownyPlugin plugin, ResidentService residentService, TownService townService, PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.permissionService = permissionService;
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
            case "create":
                handleCreate(player, args);
                break;
            case "join":
                handleJoin(player, args);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "list":
                handleList(player);
                break;
            case "info":
                handleInfo(player, args);
                break;
            case "spawn":
                handleSpawn(player);
                break;
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /town create <name>");
            return;
        }

        String townName = args[1];
        UUID playerUuid = player.getUniqueId();

        // Check if player already has a town
        residentService.getResident(playerUuid).ifPresent(resident -> {
            if (resident.hasTown()) {
                player.sendMessage(ChatColor.RED + "You are already in a town: " + resident.getTown());
                return;
            }
        });

        // Check if town already exists
        if (townService.townExists(townName)) {
            player.sendMessage(ChatColor.RED + "A town with that name already exists!");
            return;
        }

        // Validate town name
        if (townName.length() < 3 || townName.length() > 20) {
            player.sendMessage(ChatColor.RED + "Town name must be between 3 and 20 characters!");
            return;
        }

        try {
            // Ensure resident exists before creating town
            if (!residentService.residentExists(playerUuid)) {
                residentService.createResident(playerUuid, player.getName());
                plugin.getLogger().info("Created resident record for player: " + player.getName() + " (" + playerUuid + ")");
            }

            townService.createTown(townName, playerUuid);
            player.sendMessage(ChatColor.GREEN + "Successfully created town: " + ChatColor.YELLOW + townName);
            player.sendMessage(ChatColor.GREEN + "You are now the mayor of " + ChatColor.YELLOW + townName);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to create town: " + e.getMessage());
            plugin.getLogger().warning("Failed to create town " + townName + " for player " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /town join <name>");
            return;
        }

        String townName = args[1];
        UUID playerUuid = player.getUniqueId();

        // Check if player already has a town
        residentService.getResident(playerUuid).ifPresent(resident -> {
            if (resident.hasTown()) {
                player.sendMessage(ChatColor.RED + "You are already in a town: " + resident.getTown());
                return;
            }
        });

        // Check if town exists
        if (!townService.townExists(townName)) {
            player.sendMessage(ChatColor.RED + "Town '" + townName + "' does not exist!");
            return;
        }

        try {
            boolean success = townService.addResidentToTown(townName, playerUuid);
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Successfully joined town: " + ChatColor.YELLOW + townName);
            } else {
                player.sendMessage(ChatColor.RED + "Failed to join town. It may be full or closed.");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to join town: " + e.getMessage());
            plugin.getLogger().warning("Failed to join town " + townName + " for player " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleLeave(Player player) {
        UUID playerUuid = player.getUniqueId();

        if (residentService.getResident(playerUuid).isPresent()) {
            var resident = residentService.getResident(playerUuid).get();
            if (!resident.hasTown()) {
                player.sendMessage(ChatColor.RED + "You are not in a town!");
                return;
            }

            String townName = resident.getTown();

            // Check if player is the mayor
            if (permissionService.hasTownAdmin(playerUuid, townName)) {
                player.sendMessage(ChatColor.RED + "You cannot leave your town while you are the mayor! Set a new mayor first.");
                return;
            }

            try {
                boolean success = townService.removeResidentFromTown(townName, playerUuid);
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "You have left town: " + ChatColor.YELLOW + townName);
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to leave town.");
                }
            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Failed to leave town: " + e.getMessage());
                plugin.getLogger().warning("Failed to leave town for player " + player.getName() + ": " + e.getMessage());
            }
        } else {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
        }
    }

    private void handleList(Player player) {
        List<org.aincraft.towny.models.Town> towns = townService.getAllTowns();

        if (towns.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "There are no towns yet.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "=== Towns (" + towns.size() + ") ===");
        for (int i = 0; i < Math.min(towns.size(), 10); i++) {
            org.aincraft.towny.models.Town town = towns.get(i);
            int residentCount = townService.getTownResidentCount(town.getName());

            player.sendMessage(ChatColor.WHITE + String.valueOf(i + 1) + ". " + ChatColor.AQUA + town.getName() +
                             ChatColor.GRAY + " (" + residentCount + " residents)");
        }

        if (towns.size() > 10) {
            player.sendMessage(ChatColor.GRAY + "And " + (towns.size() - 10) + " more...");
        }
    }

    private void handleInfo(Player player, String[] args) {
        UUID playerUuid = player.getUniqueId();

        if (residentService.getResident(playerUuid).isPresent()) {
            var resident = residentService.getResident(playerUuid).get();
            if (!resident.hasTown()) {
                player.sendMessage(ChatColor.RED + "You are not in a town!");
                return;
            }

            String townName = resident.getTown();
            if (townService.getTown(townName).isPresent()) {
                var town = townService.getTown(townName).get();
                player.sendMessage(ChatColor.YELLOW + "=== " + townName + " ===");
                player.sendMessage(ChatColor.WHITE + "Mayor: " + ChatColor.AQUA + town.getMayorUuid());
                player.sendMessage(ChatColor.WHITE + "Residents: " + ChatColor.AQUA + town.getResidentCount());
                player.sendMessage(ChatColor.WHITE + "Balance: " + ChatColor.GOLD + "§" + String.format("%.2f", town.getBalance()));
                player.sendMessage(ChatColor.WHITE + "Open: " + (town.isOpen() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            } else {
                player.sendMessage(ChatColor.RED + "Town information could not be loaded.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
        }
    }

    private void handleSpawn(Player player) {
        UUID playerUuid = player.getUniqueId();

        if (residentService.getResident(playerUuid).isPresent()) {
            var resident = residentService.getResident(playerUuid).get();
            if (!resident.hasTown()) {
                player.sendMessage(ChatColor.RED + "You are not in a town!");
                return;
            }

            String townName = resident.getTown();

            // For now, teleport to player's current location
            // In the future, this would teleport to the town's spawn point
            player.sendMessage(ChatColor.YELLOW + "Teleporting to " + townName + " spawn...");
            player.sendMessage(ChatColor.GRAY + "(Note: Town spawn points will be implemented in a future update)");
        } else {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== Town Commands ===");
        player.sendMessage(ChatColor.WHITE + "/town create <name> " + ChatColor.GRAY + "- Create a new town");
        player.sendMessage(ChatColor.WHITE + "/town join <name> " + ChatColor.GRAY + "- Join an existing town");
        player.sendMessage(ChatColor.WHITE + "/town leave " + ChatColor.GRAY + "- Leave your current town");
        player.sendMessage(ChatColor.WHITE + "/town list " + ChatColor.GRAY + "- List all towns");
        player.sendMessage(ChatColor.WHITE + "/town info " + ChatColor.GRAY + "- Show town information");
        player.sendMessage(ChatColor.WHITE + "/town spawn " + ChatColor.GRAY + "- Teleport to town spawn");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = Arrays.asList("create", "join", "leave", "list", "info", "spawn");

        if (args.length == 1) {
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("join")) {
                // Return list of towns for join command
                return townService.getAllTowns().stream()
                        .map(town -> town.getName())
                        .filter(townName -> townName.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return null;
    }
}
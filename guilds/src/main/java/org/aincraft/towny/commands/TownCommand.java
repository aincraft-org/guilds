package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.aincraft.towny.models.Location;
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
    private final PlotService plotService;
    private final PermissionService permissionService;

    @Inject
    public TownCommand(TownyPlugin plugin, ResidentService residentService, TownService townService, PlotService plotService, PermissionService permissionService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
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
            case "delete":
                handleDelete(player, args);
                break;
            case "claim":
                handleClaim(player);
                break;
            case "unclaim":
                handleUnclaim(player);
                break;
            case "list":
                handleList(player);
                break;
            case "info":
                handleInfo(player, args);
                break;
            case "spawn":
                handleSpawn(player, args);
                break;
            case "setspawn":
                handleSetSpawn(player);
                break;
            case "toggle":
                handleToggle(player, args);
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
        var resident = residentService.getResident(playerUuid);
        if (resident.isPresent() && resident.get().hasTown()) {
            player.sendMessage(ChatColor.RED + "You are already in a town: " + resident.get().getTown());
            return;
        }

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

            // Get player's current location for home block
            org.bukkit.Location bukkitLocation = player.getLocation();
            Location homeBlockLocation = new Location(
                bukkitLocation.getX(),
                bukkitLocation.getY(),
                bukkitLocation.getZ(),
                bukkitLocation.getYaw(),
                bukkitLocation.getPitch(),
                bukkitLocation.getWorld().getName()
            );

            // Create town with home block at player's current location
            townService.createTown(townName, playerUuid, homeBlockLocation);

            // Get chunk coordinates for display and auto-claim
            int[] chunkCoords = homeBlockLocation.getChunkCoordinates();
            org.bukkit.Chunk chunk = player.getLocation().getChunk();
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            String world = player.getWorld().getName();

            // Auto-claim the home block chunk
            try {
                plotService.claimTownBlock(chunkX, chunkZ, world, townName);
                player.sendMessage(ChatColor.GREEN + "Successfully created town: " + ChatColor.YELLOW + townName);
                player.sendMessage(ChatColor.GREEN + "You are now the mayor of " + ChatColor.YELLOW + townName);
                player.sendMessage(ChatColor.GRAY + "Home block set at chunk [" + chunkCoords[0] + ", " + chunkCoords[1] + "] and automatically claimed!");
                player.sendMessage(ChatColor.GRAY + "Town spawn automatically set at your current location");
            } catch (Exception claimError) {
                player.sendMessage(ChatColor.GREEN + "Successfully created town: " + ChatColor.YELLOW + townName);
                player.sendMessage(ChatColor.GREEN + "You are now the mayor of " + ChatColor.YELLOW + townName);
                player.sendMessage(ChatColor.GRAY + "Home block set at chunk [" + chunkCoords[0] + ", " + chunkCoords[1] + "]");
                player.sendMessage(ChatColor.GRAY + "Town spawn automatically set at your current location");
                player.sendMessage(ChatColor.YELLOW + "Warning: Could not auto-claim home block chunk: " + claimError.getMessage());
            }
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
        var resident = residentService.getResident(playerUuid);
        if (resident.isPresent() && resident.get().hasTown()) {
            player.sendMessage(ChatColor.RED + "You are already in a town: " + resident.get().getTown());
            return;
        }

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

    private void handleDelete(Player player, String[] args) {
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
            return;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage(ChatColor.RED + "You are not in a town!");
            return;
        }

        String townName = resident.getTown();

        // Check if player is the mayor
        if (!permissionService.hasTownAdmin(playerUuid, townName)) {
            player.sendMessage(ChatColor.RED + "Only the mayor can delete the town!");
            return;
        }

        // Get town info for confirmation
        var townOpt = townService.getTown(townName);
        if (townOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Failed to load town data!");
            return;
        }

        var town = townOpt.get();

        // Get claim count for confirmation
        int claimCount = plotService.getTownBlockCount(townName);

        // Check for confirmation argument
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage(ChatColor.RED + "Are you sure you want to delete " + ChatColor.YELLOW + townName + ChatColor.RED + "?");
            player.sendMessage(ChatColor.YELLOW + "This action cannot be undone!");
            player.sendMessage(ChatColor.GRAY + "Town has " + town.getResidentCount() + " resident(s) and a balance of $" + String.format("%.2f", town.getBalance()));
            player.sendMessage(ChatColor.GRAY + "Town has " + claimCount + " claimed chunk(s) that will be unclaimed");
            player.sendMessage(ChatColor.GREEN + "Type " + ChatColor.WHITE + "/town delete confirm" + ChatColor.GREEN + " to confirm deletion.");
            return;
        }

        // Delete the town
        try {
            boolean success = townService.deleteTown(townName);
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Town " + ChatColor.YELLOW + townName + ChatColor.GREEN + " has been deleted!");
                player.sendMessage(ChatColor.GRAY + "All residents have been removed and " + claimCount + " chunk(s) have been unclaimed.");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to delete town!");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to delete town: " + e.getMessage());
            plugin.getLogger().warning("Failed to delete town " + townName + " for player " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleClaim(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
            return;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage(ChatColor.RED + "You are not in a town!");
            return;
        }

        String townName = resident.getTown();

        // Check if player has permission to claim
        if (!permissionService.hasPermission(playerUuid, "claim", "town", townName)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to claim land for your town!");
            return;
        }

        // Get the chunk player is standing in
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String world = player.getWorld().getName();

        // Check if chunk is already claimed
        if (plotService.townBlockExists(chunkX, chunkZ, world)) {
            player.sendMessage(ChatColor.RED + "This chunk is already claimed!");
            return;
        }

        // Check if this claim is adjacent to an existing town claim
        if (!isAdjacentToTownClaim(chunkX, chunkZ, world, townName)) {
            player.sendMessage(ChatColor.RED + "Claims must be adjacent to your existing town chunks!");
            player.sendMessage(ChatColor.GRAY + "You can only claim chunks that touch your town's territory.");
            return;
        }

        // Claim the chunk
        try {
            boolean success = plotService.claimTownBlock(chunkX, chunkZ, world, townName);
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Successfully claimed chunk [" + chunkX + ", " + chunkZ + "] for " + ChatColor.YELLOW + townName + ChatColor.GREEN + "!");
                plugin.getLogger().info("Player " + player.getName() + " claimed chunk [" + chunkX + ", " + chunkZ + "] for town " + townName);
            } else {
                player.sendMessage(ChatColor.RED + "Failed to claim chunk!");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to claim chunk: " + e.getMessage());
            plugin.getLogger().warning("Failed to claim chunk for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Check if a chunk is adjacent to any existing town claims
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param world World name
     * @param townName Town name
     * @return True if adjacent to town's claims, false otherwise
     */
    private boolean isAdjacentToTownClaim(int chunkX, int chunkZ, String world, String townName) {
        // Get all town blocks for this town
        var townBlocks = plotService.getTownBlocksInTown(townName);

        // If town has no claims yet, allow first claim (should only happen if home block wasn't claimed)
        if (townBlocks.isEmpty()) {
            return true;
        }

        // Check all 4 adjacent chunks (N, S, E, W)
        int[][] adjacentOffsets = {
            {0, 1},   // North
            {0, -1},  // South
            {1, 0},   // East
            {-1, 0}   // West
        };

        for (int[] offset : adjacentOffsets) {
            int adjacentX = chunkX + offset[0];
            int adjacentZ = chunkZ + offset[1];

            // Check if there's a town block at this adjacent position
            var adjacentBlock = plotService.getTownBlock(adjacentX, adjacentZ, world);
            if (adjacentBlock.isPresent()) {
                // Check if it belongs to this town
                var blockTown = townService.getTownById(adjacentBlock.get().getTownId());
                if (blockTown.isPresent() && blockTown.get().getName().equals(townName)) {
                    return true; // Found an adjacent claim from this town
                }
            }
        }

        return false; // No adjacent claims found
    }

    private void handleUnclaim(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
            return;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage(ChatColor.RED + "You are not in a town!");
            return;
        }

        String townName = resident.getTown();

        // Check if player has permission to unclaim
        if (!permissionService.hasPermission(playerUuid, "unclaim", "town", townName)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to unclaim land for your town!");
            return;
        }

        // Get the chunk player is standing in
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        String world = player.getWorld().getName();

        // Check if chunk is claimed by this town
        var townBlock = plotService.getTownBlock(chunkX, chunkZ, world);
        if (townBlock.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This chunk is not claimed!");
            return;
        }

        // Get the town that owns this chunk
        var blockTown = townService.getTownById(townBlock.get().getTownId());
        if (blockTown.isEmpty() || !blockTown.get().getName().equals(townName)) {
            player.sendMessage(ChatColor.RED + "This chunk doesn't belong to your town!");
            return;
        }

        // Unclaim the chunk
        try {
            boolean success = plotService.unclaimTownBlock(chunkX, chunkZ, world);
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Successfully unclaimed chunk [" + chunkX + ", " + chunkZ + "]!");
                plugin.getLogger().info("Player " + player.getName() + " unclaimed chunk [" + chunkX + ", " + chunkZ + "] from town " + townName);
            } else {
                player.sendMessage(ChatColor.RED + "Failed to unclaim chunk!");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to unclaim chunk: " + e.getMessage());
            plugin.getLogger().warning("Failed to unclaim chunk for player " + player.getName() + ": " + e.getMessage());
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

    private void handleSpawn(Player player, String[] args) {
        UUID playerUuid = player.getUniqueId();

        if (residentService.getResident(playerUuid).isPresent()) {
            var resident = residentService.getResident(playerUuid).get();

            String townName;
            if (args.length > 1) {
                // Teleport to specific town
                townName = args[1];
            } else {
                // Teleport to player's town
                if (!resident.hasTown()) {
                    player.sendMessage(ChatColor.RED + "You are not in a town!");
                    return;
                }
                townName = resident.getTown();
            }

            // Check if town exists
            if (!townService.townExists(townName)) {
                player.sendMessage(ChatColor.RED + "Town '" + townName + "' does not exist!");
                return;
            }

            // Check if player can teleport to this town's spawn
            if (!townService.canTeleportToSpawn(playerUuid, townName)) {
                player.sendMessage(ChatColor.RED + "You cannot teleport to " + townName + "'s spawn!");
                if (!resident.hasTown() || !resident.getTown().equals(townName)) {
                    player.sendMessage(ChatColor.GRAY + "You must be a resident of the town or the town must be open.");
                }
                return;
            }

            // Get spawn location
            var spawnLocation = townService.getTownSpawn(townName);
            if (spawnLocation.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Town " + townName + " does not have a spawn point set!");
                return;
            }

            // Convert our Location to Bukkit Location
            Location townSpawn = spawnLocation.get();
            org.bukkit.Location bukkitLocation = new org.bukkit.Location(
                org.bukkit.Bukkit.getWorld(townSpawn.getWorld()),
                townSpawn.getX(),
                townSpawn.getY(),
                townSpawn.getZ(),
                townSpawn.getYaw(),
                townSpawn.getPitch()
            );

            // Teleport player
            player.teleport(bukkitLocation);
            player.sendMessage(ChatColor.GREEN + "Teleported to " + ChatColor.YELLOW + townName + ChatColor.GREEN + " spawn!");

        } else {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
        }
    }

    private void handleSetSpawn(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        if (residentService.getResident(playerUuid).isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
            return;
        }

        var resident = residentService.getResident(playerUuid).get();
        if (!resident.hasTown()) {
            player.sendMessage(ChatColor.RED + "You are not in a town!");
            return;
        }

        String townName = resident.getTown();

        // Check if player has permission to set spawn
        if (!permissionService.hasPermission(playerUuid, "set_spawn", "town", townName)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to set the town spawn!");
            return;
        }

        // Get the town to check home block
        var townOpt = townService.getTown(townName);
        if (townOpt.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Failed to load town data!");
            return;
        }

        var town = townOpt.get();
        if (town.getHomeBlock() == null) {
            player.sendMessage(ChatColor.RED + "Your town does not have a home block set!");
            player.sendMessage(ChatColor.GRAY + "A home block must be set before setting a spawn.");
            return;
        }

        // Get player's current location
        org.bukkit.Location bukkitLocation = player.getLocation();
        Location townSpawn = new Location(
            bukkitLocation.getX(),
            bukkitLocation.getY(),
            bukkitLocation.getZ(),
            bukkitLocation.getYaw(),
            bukkitLocation.getPitch(),
            bukkitLocation.getWorld().getName()
        );

        // Check if player is in the home block chunk
        int[] spawnChunk = townSpawn.getChunkCoordinates();
        int[] homeBlockChunk = town.getHomeBlock().getChunkCoordinates();

        if (spawnChunk[0] != homeBlockChunk[0] || spawnChunk[1] != homeBlockChunk[1]) {
            player.sendMessage(ChatColor.RED + "You must be in your town's home block chunk to set the spawn!");
            player.sendMessage(ChatColor.GRAY + "Your chunk: [" + spawnChunk[0] + ", " + spawnChunk[1] + "]");
            player.sendMessage(ChatColor.GRAY + "Home block chunk: [" + homeBlockChunk[0] + ", " + homeBlockChunk[1] + "]");
            return;
        }

        // Check world matches
        if (!townSpawn.getWorld().equals(town.getHomeBlock().getWorld())) {
            player.sendMessage(ChatColor.RED + "You must be in the same world as your town's home block!");
            return;
        }

        // Set the town spawn
        if (townService.setTownSpawn(townName, townSpawn)) {
            player.sendMessage(ChatColor.GREEN + "Town spawn set for " + ChatColor.YELLOW + townName + ChatColor.GREEN + "!");
            player.sendMessage(ChatColor.GRAY + "Spawn location: " + townSpawn.toDisplayString());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to set town spawn!");
        }
    }

    private void handleToggle(Player player, String[] args) {
        UUID playerUuid = player.getUniqueId();

        // Check if player is in a town
        var resident = residentService.getResident(playerUuid);
        if (resident.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Your resident data could not be loaded!");
            return;
        }

        if (!resident.get().hasTown()) {
            player.sendMessage(ChatColor.RED + "You are not in a town!");
            return;
        }

        String townName = resident.get().getTown();

        // Check if player has permission to toggle town settings
        if (!permissionService.hasTownAdmin(playerUuid, townName)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to toggle town settings!");
            return;
        }

        // Handle different toggle subcommands
        if (args.length < 2) {
            showToggleHelp(player);
            return;
        }

        String subCommand = args[1].toLowerCase();

        if (subCommand.equals("list")) {
            // Show current toggle states
            var toggles = townService.getTownToggles(townName);
            if (toggles.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Failed to load toggle states!");
                return;
            }

            player.sendMessage(ChatColor.YELLOW + "=== " + ChatColor.AQUA + townName + ChatColor.YELLOW + " Toggles ===");
            player.sendMessage(ChatColor.GRAY + "PvP: " + (toggles.get("pvp") ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
            player.sendMessage(ChatColor.GRAY + "Fire: " + (toggles.get("fire") ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
            player.sendMessage(ChatColor.GRAY + "Explosions: " + (toggles.get("explosions") ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
            player.sendMessage(ChatColor.GRAY + "Mobs: " + (toggles.get("mobs") ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
            player.sendMessage(ChatColor.GRAY + "Public: " + (toggles.get("public") ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
            return;
        }

        // Handle specific toggle operations
        if (args.length < 3) {
            // Toggle the setting (no value specified)
            String toggleType = subCommand;
            if (isValidToggleType(toggleType)) {
                boolean success = townService.toggleTownPermission(townName, toggleType, playerUuid);
                if (success) {
                    boolean newState = townService.getTownToggle(townName, toggleType);
                    String displayName = getToggleDisplayName(toggleType);
                    player.sendMessage(ChatColor.GREEN + "Toggled " + ChatColor.YELLOW + displayName + ChatColor.GREEN + " " +
                                     (newState ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to toggle " + toggleType + "!");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Unknown toggle type: " + toggleType);
                showToggleHelp(player);
            }
        } else {
            // Set the toggle to a specific value
            String toggleType = subCommand;
            String valueStr = args[2].toLowerCase();

            if (!isValidToggleType(toggleType)) {
                player.sendMessage(ChatColor.RED + "Unknown toggle type: " + toggleType);
                showToggleHelp(player);
                return;
            }

            boolean value;
            if (valueStr.equals("on") || valueStr.equals("true") || valueStr.equals("enable")) {
                value = true;
            } else if (valueStr.equals("off") || valueStr.equals("false") || valueStr.equals("disable")) {
                value = false;
            } else {
                player.sendMessage(ChatColor.RED + "Invalid value. Use: on/off, true/false, or enable/disable");
                return;
            }

            boolean success = townService.setTownToggle(townName, toggleType, value, playerUuid);
            if (success) {
                String displayName = getToggleDisplayName(toggleType);
                player.sendMessage(ChatColor.GREEN + "Set " + ChatColor.YELLOW + displayName + ChatColor.GREEN + " to " +
                                 (value ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
            } else {
                player.sendMessage(ChatColor.RED + "Failed to set " + toggleType + " to " + valueStr + "!");
            }
        }
    }

    private boolean isValidToggleType(String toggleType) {
        return toggleType.equals("pvp") || toggleType.equals("fire") ||
               toggleType.equals("explosions") || toggleType.equals("mobs") ||
               toggleType.equals("public");
    }

    private String getToggleDisplayName(String toggleType) {
        switch (toggleType.toLowerCase()) {
            case "pvp": return "PvP";
            case "fire": return "Fire Spread";
            case "explosions": return "Explosions";
            case "mobs": return "Mob Spawning";
            case "public": return "Public Access";
            default: return toggleType;
        }
    }

    private void showToggleHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== Town Toggle Commands ===");
        player.sendMessage(ChatColor.GRAY + "/town toggle list" + ChatColor.WHITE + " - Show current toggle states");
        player.sendMessage(ChatColor.GRAY + "/town toggle <type>" + ChatColor.WHITE + " - Toggle a setting");
        player.sendMessage(ChatColor.GRAY + "/town toggle <type> <on|off>" + ChatColor.WHITE + " - Set a setting");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Available toggles:");
        player.sendMessage(ChatColor.WHITE + "  pvp" + ChatColor.GRAY + " - Player vs Player combat");
        player.sendMessage(ChatColor.WHITE + "  fire" + ChatColor.GRAY + " - Fire spread");
        player.sendMessage(ChatColor.WHITE + "  explosions" + ChatColor.GRAY + " - Explosions");
        player.sendMessage(ChatColor.WHITE + "  mobs" + ChatColor.GRAY + " - Mob spawning");
        player.sendMessage(ChatColor.WHITE + "  public" + ChatColor.GRAY + " - Public access");
    }

    private void showHelp(Player player) {
        player.sendMessage("§6╔══════════════════════════════════════════════╗");
        player.sendMessage("§6║          §e§lTOWN COMMANDS§r§6                    ║");
        player.sendMessage("§6╠══════════════════════════════════════════════╣");
        player.sendMessage("§6║ §f/town create §7<name>                       §6║");
        player.sendMessage("§6║   §8» Create a new town                      §6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town join §7<name>                         §6║");
        player.sendMessage("§6║   §8» Join an existing town                  §6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town leave                                §6║");
        player.sendMessage("§6║   §8» Leave your current town                §6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town claim                                §6║");
        player.sendMessage("§6║   §8» Claim the chunk you're in             §6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town unclaim                              §6║");
        player.sendMessage("§6║   §8» Unclaim the chunk you're in           §6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town spawn §7[town]                        §6║");
        player.sendMessage("§6║   §8» Teleport to town spawn                §6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town info §7[town]                         §6║");
        player.sendMessage("§6║   §8» Show town information                  §6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town list                                 §6║");
        player.sendMessage("§6║   §8» List all towns                         §6║");
        player.sendMessage("§6╠══════════════════════════════════════════════╣");
        player.sendMessage("§6║            §e§lMAYOR COMMANDS§r§6                 ║");
        player.sendMessage("§6╠══════════════════════════════════════════════╣");
        player.sendMessage("§6║ §f/town setspawn                             §6║");
        player.sendMessage("§6║   §8» Set town spawn (must be in home chunk)§6║");
        player.sendMessage("§6║                                              §6║");
        player.sendMessage("§6║ §f/town delete confirm                       §6║");
        player.sendMessage("§6║   §8» Delete your town permanently           §6║");
        player.sendMessage("§6╚══════════════════════════════════════════════╝");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = Arrays.asList("create", "join", "leave", "delete", "claim", "unclaim", "list", "info", "spawn", "setspawn", "toggle");

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
            } else if (subCommand.equals("spawn")) {
                // Return list of towns for spawn command
                return townService.getAllTowns().stream()
                        .map(town -> town.getName())
                        .filter(townName -> townName.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("delete")) {
                // Return "confirm" for delete command
                return Arrays.asList("confirm").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("toggle")) {
                // Return toggle types for toggle command
                return Arrays.asList("list", "pvp", "fire", "explosions", "mobs", "public").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("toggle")) {
                String toggleType = args[1].toLowerCase();
                if (!toggleType.equals("list") && isValidToggleType(toggleType)) {
                    // Return toggle values for specific toggle type
                    return Arrays.asList("on", "off", "true", "false", "enable", "disable").stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return null;
    }
}
package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.Permission;
import org.aincraft.towny.services.*;

import java.util.UUID;

/**
 * Debug command to test permissions
 */
public class PermCommand implements CommandExecutor {

    private final TownyPlugin plugin;
    private final PermissionService permissionService;
    private final PlotService plotService;
    private final TownService townService;

    @Inject
    public PermCommand(TownyPlugin plugin, PermissionService permissionService,
                      PlotService plotService, TownService townService) {
        this.plugin = plugin;
        this.permissionService = permissionService;
        this.plotService = plotService;
        this.townService = townService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        if (args.length == 0) {
            showUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check":
                checkPermissions(player, playerUuid);
                break;
            case "build":
                testBuildPermission(player, playerUuid);
                break;
            case "destroy":
                testDestroyPermission(player, playerUuid);
                break;
            case "plot":
                testPlotPermission(player, playerUuid, args);
                break;
            case "town":
                testTownPermission(player, playerUuid, args);
                break;
            case "flags":
                showPermissionFlags(player);
                break;
            case "here":
                showLocationInfo(player);
                break;
            default:
                showUsage(player);
                break;
        }

        return true;
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Permission Test Commands:");
        player.sendMessage(ChatColor.AQUA + "/perm check" + ChatColor.WHITE + " - Check all permissions at your location");
        player.sendMessage(ChatColor.AQUA + "/perm build" + ChatColor.WHITE + " - Test build permission here");
        player.sendMessage(ChatColor.AQUA + "/perm destroy" + ChatColor.WHITE + " - Test destroy permission here");
        player.sendMessage(ChatColor.AQUA + "/perm plot [flag]" + ChatColor.WHITE + " - Test specific plot permission");
        player.sendMessage(ChatColor.AQUA + "/perm town [flag]" + ChatColor.WHITE + " - Test town permissions");
        player.sendMessage(ChatColor.AQUA + "/perm flags" + ChatColor.WHITE + " - Show available permission flags");
        player.sendMessage(ChatColor.AQUA + "/perm here" + ChatColor.WHITE + " - Show current location info");
    }

    private void checkPermissions(Player player, UUID playerUuid) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        String world = player.getLocation().getWorld().getName();

        player.sendMessage(ChatColor.GOLD + "=== Permission Check at " + x + ", " + z + " in " + world + " ===");

        boolean canBuild = permissionService.canBuild(playerUuid, x, z, world);
        boolean canDestroy = permissionService.canDestroy(playerUuid, x, z, world);
        boolean canSwitch = permissionService.canSwitch(playerUuid, x, z, world);
        boolean canUseItems = permissionService.canUseItems(playerUuid, x, z, world);

        player.sendMessage(ChatColor.GREEN + "Build: " + (canBuild ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
        player.sendMessage(ChatColor.GREEN + "Destroy: " + (canDestroy ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
        player.sendMessage(ChatColor.GREEN + "Switch: " + (canSwitch ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
        player.sendMessage(ChatColor.GREEN + "Item Use: " + (canUseItems ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
    }

    private void testBuildPermission(Player player, UUID playerUuid) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        String world = player.getLocation().getWorld().getName();

        boolean canBuild = permissionService.canBuild(playerUuid, x, z, world);

        player.sendMessage(ChatColor.GOLD + "Build Permission Test:");
        player.sendMessage(ChatColor.WHITE + "Location: " + x + ", " + z + " in " + world);
        player.sendMessage(ChatColor.WHITE + "Result: " + (canBuild ? ChatColor.GREEN + "ALLOWED" : ChatColor.RED + "DENIED"));

        // Show detailed evaluation if we can get plot info
        showDetailedPermissionInfo(player, playerUuid, x, z, world, Permission.Flag.BUILD);
    }

    private void testDestroyPermission(Player player, UUID playerUuid) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        String world = player.getLocation().getWorld().getName();

        boolean canDestroy = permissionService.canDestroy(playerUuid, x, z, world);

        player.sendMessage(ChatColor.GOLD + "Destroy Permission Test:");
        player.sendMessage(ChatColor.WHITE + "Location: " + x + ", " + z + " in " + world);
        player.sendMessage(ChatColor.WHITE + "Result: " + (canDestroy ? ChatColor.GREEN + "ALLOWED" : ChatColor.RED + "DENIED"));

        // Show detailed evaluation
        showDetailedPermissionInfo(player, playerUuid, x, z, world, Permission.Flag.DESTROY);
    }

    private void testPlotPermission(Player player, UUID playerUuid, String[] args) {
        int flag = Permission.Flag.BUILD; // default
        String flagName = "BUILD";

        if (args.length > 1) {
            flag = getFlagFromName(args[1]);
            if (flag == -1) {
                player.sendMessage(ChatColor.RED + "Unknown permission flag: " + args[1]);
                player.sendMessage(ChatColor.YELLOW + "Use /perm flags to see available flags");
                return;
            }
            flagName = args[1].toUpperCase();
        }

        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        String world = player.getLocation().getWorld().getName();

        player.sendMessage(ChatColor.GOLD + "Plot Permission Test:");
        player.sendMessage(ChatColor.WHITE + "Flag: " + flagName);
        player.sendMessage(ChatColor.WHITE + "Location: " + x + ", " + z + " in " + world);

        showDetailedPermissionInfo(player, playerUuid, x, z, world, flag);
    }

    private void testTownPermission(Player player, UUID playerUuid, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /perm town [townname]");
            return;
        }

        String townName = args[1];

        player.sendMessage(ChatColor.GOLD + "Town Permission Test for " + townName + ":");

        boolean isMayor = permissionService.isTownMayor(playerUuid, townName);
        boolean isAssistant = permissionService.isTownAssistant(playerUuid, townName);
        boolean hasAdmin = permissionService.hasTownAdmin(playerUuid, townName);

        player.sendMessage(ChatColor.WHITE + "Mayor: " + (isMayor ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
        player.sendMessage(ChatColor.WHITE + "Assistant: " + (isAssistant ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
        player.sendMessage(ChatColor.WHITE + "Admin: " + (hasAdmin ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗"));
    }

    private void showPermissionFlags(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Permission Flags ===");
        player.sendMessage(ChatColor.AQUA + "Build Flags:");
        player.sendMessage(ChatColor.WHITE + "  BUILD (1) - Can build blocks");
        player.sendMessage(ChatColor.WHITE + "  DESTROY (2) - Can break blocks");
        player.sendMessage(ChatColor.WHITE + "  SWITCH (4) - Can use doors/levers/buttons");
        player.sendMessage(ChatColor.WHITE + "  ITEM_USE (8) - Can use items");

        player.sendMessage(ChatColor.AQUA + "Town Flags:");
        player.sendMessage(ChatColor.WHITE + "  CLAIM (16) - Can claim land");
        player.sendMessage(ChatColor.WHITE + "  UNCLAIM (32) - Can unclaim land");
        player.sendMessage(ChatColor.WHITE + "  SPAWN (64) - Can teleport to town");
        player.sendMessage(ChatColor.WHITE + "  SET_SPAWN (128) - Can set town spawn");

        player.sendMessage(ChatColor.AQUA + "Management Flags:");
        player.sendMessage(ChatColor.WHITE + "  INVITE (256) - Can invite players");
        player.sendMessage(ChatColor.WHITE + "  KICK (512) - Can kick players");
        player.sendMessage(ChatColor.WHITE + "  PROMOTE (1024) - Can promote players");
        player.sendMessage(ChatColor.WHITE + "  DEMOTE (2048) - Can demote players");
    }

    private void showLocationInfo(Player player) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        String world = player.getLocation().getWorld().getName();

        player.sendMessage(ChatColor.GOLD + "=== Location Information ===");
        player.sendMessage(ChatColor.WHITE + "Block: " + x + ", " + z);
        player.sendMessage(ChatColor.WHITE + "Chunk: " + chunkX + ", " + chunkZ);
        player.sendMessage(ChatColor.WHITE + "World: " + world);

        // Check if in town block
        plotService.getTownBlock(chunkX, chunkZ, world).ifPresent(townBlock -> {
            player.sendMessage(ChatColor.GREEN + "In Town Block!");
            player.sendMessage(ChatColor.WHITE + "Town ID: " + townBlock.getTownId());
            player.sendMessage(ChatColor.WHITE + "Owner ID: " +
                (townBlock.getOwnerId() != null ? townBlock.getOwnerId().toString() : "None (Town-owned)"));
            player.sendMessage(ChatColor.WHITE + "Plot Type: " + townBlock.getPlotType());
        });
    }

    private void showDetailedPermissionInfo(Player player, UUID playerUuid, int x, int z, String world, int permissionFlag) {
        try {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            plotService.getTownBlock(chunkX, chunkZ, world).ifPresent(townBlock -> {
                PermissionEvaluationResult result = permissionService.evaluatePlotPermission(
                    playerUuid, townBlock.getId(), permissionFlag);

                player.sendMessage(ChatColor.YELLOW + "Permission Source: " + ChatColor.WHITE + result.getSource());
                player.sendMessage(ChatColor.YELLOW + "Reason: " + ChatColor.WHITE + result.getReason());
            });

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error getting detailed permission info: " + e.getMessage());
        }
    }

    private int getFlagFromName(String flagName) {
        switch (flagName.toUpperCase()) {
            case "BUILD": return Permission.Flag.BUILD;
            case "DESTROY": return Permission.Flag.DESTROY;
            case "SWITCH": return Permission.Flag.SWITCH;
            case "ITEM_USE": case "ITEMUSE": return Permission.Flag.ITEM_USE;
            case "CLAIM": return Permission.Flag.CLAIM;
            case "UNCLAIM": return Permission.Flag.UNCLAIM;
            case "SPAWN": return Permission.Flag.SPAWN;
            case "SET_SPAWN": case "SETSPAWN": return Permission.Flag.SET_SPAWN;
            case "INVITE": return Permission.Flag.INVITE;
            case "KICK": return Permission.Flag.KICK;
            case "PROMOTE": return Permission.Flag.PROMOTE;
            case "DEMOTE": return Permission.Flag.DEMOTE;
            case "WITHDRAW": return Permission.Flag.WITHDRAW;
            case "DEPOSIT": return Permission.Flag.DEPOSIT;
            case "PLOT_PERM": case "PLOTPERM": return Permission.Flag.PLOT_PERM;
            case "PLOT_SET": case "PLOTSET": return Permission.Flag.PLOT_SET;
            case "PLOT_OWNER": case "PLOTOWNER": return Permission.Flag.PLOT_OWNER;
            case "ADMIN": return Permission.Flag.ADMIN;
            case "BYPASS": return Permission.Flag.BYPASS;
            default: return -1;
        }
    }
}
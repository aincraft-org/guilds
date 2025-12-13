package org.aincraft.towny.commands;

import com.google.inject.Inject;
import org.aincraft.towny.TownyPlugin;
import org.aincraft.towny.models.BroadcastMessage;
import org.aincraft.towny.models.Town;
import org.aincraft.towny.services.BroadcastService;
import org.aincraft.towny.services.ResidentService;
import org.aincraft.towny.services.TownService;
import org.aincraft.towny.services.PlotService;
import org.aincraft.towny.services.PermissionService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Town broadcast command handler
 */
public class TownBroadcastCommand implements CommandExecutor, TabCompleter {

    private final TownyPlugin plugin;
    private final ResidentService residentService;
    private final TownService townService;
    private final PlotService plotService;
    private final PermissionService permissionService;
    private final BroadcastService broadcastService;

    @Inject
    public TownBroadcastCommand(TownyPlugin plugin, ResidentService residentService, TownService townService,
                               PlotService plotService, PermissionService permissionService, BroadcastService broadcastService) {
        this.plugin = plugin;
        this.residentService = residentService;
        this.townService = townService;
        this.plotService = plotService;
        this.permissionService = permissionService;
        this.broadcastService = broadcastService;
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
            case "announce":
                handleCreateAnnouncement(player, args);
                break;
            case "alert":
                handleCreateAlert(player, args);
                break;
            case "welcome":
                handleCreateWelcome(player, args);
                break;
            case "list":
                handleListBroadcasts(player, args);
                break;
            case "read":
                handleReadBroadcasts(player, args);
                break;
            case "archive":
                handleArchive(player, args);
                break;
            case "delete":
                handleDelete(player, args);
                break;
            case "stats":
                handleStats(player, args);
                break;
            case "cleanup":
                handleCleanup(player);
                break;
            case "send":
                handleSend(player, args);
                break;
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void handleCreateAnnouncement(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        if (!canCreateBroadcast(player, BroadcastMessage.Type.ANNOUNCEMENT)) {
            sendError(player, "You don't have permission to create announcements!");
            return;
        }

        if (args.length < 3) {
            sendError(player, "Usage: /broadcast announce <title> <content>");
            return;
        }

        String townId = getTownId(player);
        String title = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        String content = args[args.length - 1];

        // Extract title and content properly
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("\"") && args[args.length - 1].endsWith("\"")) {
                // Handle quoted content
                title = String.join(" ", Arrays.copyOfRange(args, 1, i));
                content = String.join(" ", Arrays.copyOfRange(args, i, args.length))
                    .replaceAll("^\"|\"$", "");
                break;
            }
        }

        BroadcastMessage announcement = broadcastService.createAnnouncement(
            townId, title, content, player.getUniqueId(), player.getName(), 7 // 7 days default
        );

        sendSuccess(player, "Announcement created successfully!");
        sendInfo(player, "ID: " + announcement.getId());

        // Send to online members immediately
        int sentCount = broadcastService.sendBroadcastToOnlineMembers(announcement);
        sendInfo(player, "Sent to " + sentCount + " online town members.");
    }

    private void handleCreateAlert(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        if (!canCreateBroadcast(player, BroadcastMessage.Type.ALERT)) {
            sendError(player, "You don't have permission to create alerts!");
            return;
        }

        if (args.length < 3) {
            sendError(player, "Usage: /broadcast alert <priority 1-5> <title> <content>");
            return;
        }

        String townId = getTownId(player);
        int priority;

        try {
            priority = Integer.parseInt(args[1]);
            if (priority < 1 || priority > 5) {
                sendError(player, "Priority must be between 1 and 5!");
                return;
            }
        } catch (NumberFormatException e) {
            sendError(player, "Invalid priority! Use a number between 1 and 5.");
            return;
        }

        String title = String.join(" ", Arrays.copyOfRange(args, 2, args.length - 1));
        String content = args[args.length - 1];

        BroadcastMessage alert = broadcastService.createAlertMessage(
            townId, title, content, player.getUniqueId(), player.getName(), priority
        );

        sendSuccess(player, "Alert created successfully!");
        sendInfo(player, "Priority: " + priority + " | ID: " + alert.getId());

        // Send to online members immediately
        int sentCount = broadcastService.sendBroadcastToOnlineMembers(alert);
        sendInfo(player, "Sent to " + sentCount + " online town members.");
    }

    private void handleCreateWelcome(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        if (!canCreateBroadcast(player, BroadcastMessage.Type.WELCOME)) {
            sendError(player, "You don't have permission to create welcome messages!");
            return;
        }

        String townId = getTownId(player);

        // Welcome messages are typically automatic, but allow manual creation
        BroadcastMessage welcome = broadcastService.createWelcomeMessage(townId, "New Resident");
        welcome.setTitle(args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Welcome to our town!");

        broadcastService.updateBroadcast(welcome);

        sendSuccess(player, "Welcome message template created successfully!");
        sendInfo(player, "ID: " + welcome.getId());
    }

    private void handleListBroadcasts(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        String townId = getTownId(player);
        List<BroadcastMessage> broadcasts;

        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            broadcasts = broadcastService.getAllBroadcasts(townId);
        } else {
            broadcasts = broadcastService.getActiveBroadcasts(townId);
        }

        if (broadcasts.isEmpty()) {
            sendInfo(player, "No " + (args.length > 1 && args[1].equalsIgnoreCase("all") ? "" : "active ") + "broadcasts found.");
            return;
        }

        sendHelpHeader(player, "Town Broadcasts");
        for (BroadcastMessage broadcast : broadcasts) {
            String status = broadcast.shouldDisplay() ? "§aActive" : "§7Inactive";
            String priority = getPriorityDisplay(broadcast.getPriority());
            String audience = broadcast.getTargetAudience().equals("all") ? "All" : broadcast.getTargetAudience();

            sendInfo(player, String.format("§e%s §7- %s §7[%s] [%s]",
                broadcast.getTitle(), status, priority, audience));

            String content = broadcast.getContent();
            int contentLength = content.length();
            sendSecondary(player, "  " + (contentLength > 50 ? content.substring(0, 50) + "..." : content));
            sendSecondary(player, "  ID: " + broadcast.getId() + " | By: " + broadcast.getSenderName());
        }
    }

    private void handleReadBroadcasts(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        String townId = getTownId(player);
        String playerRole = getPlayerRole(player, townId);
        List<BroadcastMessage> broadcasts = broadcastService.getBroadcastsForPlayer(townId, player.getUniqueId(), playerRole);

        if (broadcasts.isEmpty()) {
            sendInfo(player, "No broadcasts available for you.");
            return;
        }

        sendHelpHeader(player, "Your Broadcasts");
        for (BroadcastMessage broadcast : broadcasts) {
            String formattedMessage = formatBroadcastForPlayer(broadcast);
            player.sendMessage(formattedMessage);
        }
    }

    private void handleArchive(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        String townName = getPlayerTown(player);
        if (townName == null) return;

        if (!isTownAssistant(player, townName)) {
            sendError(player, "Only town assistants and mayor can archive broadcasts!");
            return;
        }

        if (args.length < 2) {
            sendError(player, "Usage: /broadcast archive <broadcast_id>");
            return;
        }

        String broadcastId = args[1];

        if (broadcastService.archiveBroadcast(broadcastId)) {
            sendSuccess(player, "Broadcast archived successfully!");
        } else {
            sendError(player, "Failed to archive broadcast or broadcast not found.");
        }
    }

    private void handleDelete(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        String townName = getPlayerTown(player);
        if (townName == null) return;

        if (!isTownMayor(player, townName)) {
            sendError(player, "Only town mayor can delete broadcasts!");
            return;
        }

        if (args.length < 2) {
            sendError(player, "Usage: /broadcast delete <broadcast_id>");
            return;
        }

        String broadcastId = args[1];

        if (broadcastService.deleteBroadcast(broadcastId)) {
            sendSuccess(player, "Broadcast deleted permanently!");
        } else {
            sendError(player, "Failed to delete broadcast or broadcast not found.");
        }
    }

    private void handleStats(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        String townName = getPlayerTown(player);
        if (townName == null) return;

        if (!isTownAssistant(player, townName)) {
            sendError(player, "Only town assistants and mayor can view broadcast statistics!");
            return;
        }

        String townId = getTownId(player);
        BroadcastService.BroadcastStatistics stats = broadcastService.getBroadcastStatistics(townId);

        sendHelpHeader(player, "Broadcast Statistics");
        sendInfo(player, "Total Broadcasts: " + stats.getTotalBroadcasts());
        sendInfo(player, "Active Broadcasts: " + stats.getActiveBroadcasts());
        sendInfo(player, "Expired Broadcasts: " + stats.getExpiredBroadcasts());
        sendInfo(player, "Announcements: " + stats.getAnnouncements());
        sendInfo(player, "Alerts: " + stats.getAlerts());
        sendInfo(player, "Welcome Messages: " + stats.getWelcomeMessages());

        if (stats.getLastBroadcast() != null) {
            sendInfo(player, "Last Broadcast: " + stats.getLastBroadcast().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
        }

        if (stats.getMostActiveMessageType() != null) {
            sendInfo(player, "Most Active Type: " + stats.getMostActiveMessageType());
        }
    }

    private void handleCleanup(Player player) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        String townName = getPlayerTown(player);
        if (townName == null) return;

        if (!isTownMayor(player, townName)) {
            sendError(player, "Only town mayor can cleanup broadcasts!");
            return;
        }

        String townId = getTownId(player);
        int cleanedCount = broadcastService.cleanupExpiredBroadcasts(townId);

        if (cleanedCount > 0) {
            sendSuccess(player, "Cleaned up " + cleanedCount + " expired broadcasts!");
        } else {
            sendInfo(player, "No expired broadcasts to clean up.");
        }
    }

    private void handleSend(Player player, String[] args) {
        if (!requirePlayerInTown(player)) {
            return;
        }

        String townName = getPlayerTown(player);
        if (townName == null) return;

        if (!isTownAssistant(player, townName)) {
            sendError(player, "Only town assistants and mayor can send broadcasts!");
            return;
        }

        if (args.length < 2) {
            sendError(player, "Usage: /broadcast send <broadcast_id>");
            return;
        }

        String broadcastId = args[1];

        broadcastService.getBroadcast(broadcastId).ifPresent(broadcast -> {
            int sentCount = broadcastService.sendBroadcastToOnlineMembers(broadcast);
            sendSuccess(player, "Broadcast sent to " + sentCount + " online town members!");
        });
    }

    private String getPriorityDisplay(int priority) {
        switch (priority) {
            case BroadcastMessage.Priority.LOW: return "§7Low";
            case BroadcastMessage.Priority.NORMAL: return "§aNormal";
            case BroadcastMessage.Priority.HIGH: return "§eHigh";
            case BroadcastMessage.Priority.URGENT: return "§cUrgent";
            case BroadcastMessage.Priority.CRITICAL: return "§4Critical";
            default: return "§7Unknown";
        }
    }

    private String formatBroadcastForPlayer(BroadcastMessage broadcast) {
        StringBuilder message = new StringBuilder();

        // Add header based on message type
        switch (broadcast.getMessageType()) {
            case BroadcastMessage.Type.ALERT:
                message.append("§c[§6ALERT§c] ");
                break;
            case BroadcastMessage.Type.ANNOUNCEMENT:
                message.append("§e[§6ANNOUNCEMENT§e] ");
                break;
            case BroadcastMessage.Type.WELCOME:
                message.append("§a[§bWELCOME§a] ");
                break;
            case BroadcastMessage.Type.WARNING:
                message.append("§c[§4WARNING§c] ");
                break;
            case BroadcastMessage.Type.CELEBRATION:
                message.append("§6[§eCELEBRATION§6] ");
                break;
            case BroadcastMessage.Type.ECONOMIC:
                message.append("§2[§aECONOMY§2] ");
                break;
            default:
                message.append("§7[§fBROADCAST§7] ");
        }

        message.append("§f").append(broadcast.getTitle()).append("\n");
        message.append("§7").append(broadcast.getContent()).append("\n");
        message.append("§8- ").append(broadcast.getSenderName()).append(" §8(")
               .append(broadcast.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm")))
               .append("§8)");

        return message.toString();
    }

    private boolean canCreateBroadcast(Player player, String messageType) {
        String townId = getTownId(player);
        return broadcastService.canCreateBroadcast(player.getUniqueId(), townId, messageType);
    }

    private String getTownId(Player player) {
        return residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown())
                .map(resident -> {
                    Optional<Town> town = townService.getTown(resident.getTown());
                    return town.map(Town::getId).orElse(null);
                })
                .orElse(null);
    }

    private String getPlayerRole(Player player, String townId) {
        Optional<Town> town = townService.getTownById(townId);
        if (town.isEmpty()) {
            return "resident";
        }

        Town t = town.get();
        if (t.getMayorUuid().equals(player.getUniqueId())) {
            return "mayor";
        } else if (t.getAssistants().contains(player.getUniqueId())) {
            return "assistant";
        } else {
            return "resident";
        }
    }

    private boolean isTownMayor(Player player, String townName) {
        return residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown() && resident.getTown().equals(townName))
                .flatMap(resident -> townService.getTown(townName))
                .map(town -> town.getMayorUuid().equals(player.getUniqueId()))
                .orElse(false);
    }

    private boolean isTownAssistant(Player player, String townName) {
        return residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown() && resident.getTown().equals(townName))
                .flatMap(resident -> townService.getTown(townName))
                .map(town -> town.getMayorUuid().equals(player.getUniqueId()) || town.getAssistants().contains(player.getUniqueId()))
                .orElse(false);
    }

    private void showHelp(Player player) {
        sendHelpHeader(player, "Town Broadcast Commands");
        sendHelpLine(player, "/broadcast announce <title> <content>", "Create an announcement");
        sendHelpLine(player, "/broadcast alert <priority> <title> <content>", "Create a high-priority alert");
        sendHelpLine(player, "/broadcast welcome [message]", "Create welcome message template");
        sendHelpLine(player, "/broadcast list [all]", "List active (or all) broadcasts");
        sendHelpLine(player, "/broadcast read", "Read broadcasts available to you");
        sendHelpLine(player, "/broadcast archive <id>", "Archive a broadcast (Assistant+)");
        sendHelpLine(player, "/broadcast delete <id>", "Delete a broadcast (Mayor only)");
        sendHelpLine(player, "/broadcast stats", "Show broadcast statistics (Assistant+)");
        sendHelpLine(player, "/broadcast cleanup", "Remove expired broadcasts (Mayor only)");
        sendHelpLine(player, "/broadcast send <id>", "Resend a broadcast to online members (Assistant+)");

        sendSecondary(player, "");
        sendSecondary(player, "Priority levels: 1=Low, 2=Normal, 3=High, 4=Urgent, 5=Critical");
        sendSecondary(player, "Aliases: /townbroadcast, /tb");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length == 0) {
            return Arrays.asList();
        }

        Player player = (Player) sender;
        String townName = getPlayerTown(player);

        if (townName == null) {
            return Arrays.asList();
        }

        String subCommand = args[0].toLowerCase();

        switch (args.length) {
            case 1:
                return Arrays.asList("announce", "alert", "welcome", "list", "read", "archive", "delete", "stats", "cleanup", "send")
                    .stream()
                    .filter(s -> s.startsWith(subCommand))
                    .collect(Collectors.toList());

            case 2:
                switch (subCommand) {
                    case "list":
                        return Arrays.asList("all").stream()
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                    case "alert":
                        return Arrays.asList("1", "2", "3", "4", "5").stream()
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                    default:
                        return Arrays.asList();
                }

            default:
                return Arrays.asList();
        }
    }

    // Helper methods from TownyCommand base class
    private void sendError(Player player, String message) {
        player.sendMessage(ChatColor.RED + message);
    }

    private void sendSuccess(Player player, String message) {
        player.sendMessage(ChatColor.GREEN + message);
    }

    private void sendInfo(Player player, String message) {
        player.sendMessage(ChatColor.YELLOW + message);
    }

    private void sendSecondary(Player player, String message) {
        player.sendMessage(ChatColor.GRAY + message);
    }

    private void sendHelpHeader(Player player, String title) {
        player.sendMessage(ChatColor.YELLOW + "=== " + title + " ===");
    }

    private void sendHelpLine(Player player, String command, String description) {
        player.sendMessage(ChatColor.WHITE + command + " " + ChatColor.GRAY + "- " + description);
    }

    private boolean requirePlayerInTown(Player player) {
        if (getPlayerTown(player) == null) {
            sendError(player, "You are not in a town!");
            return false;
        }
        return true;
    }

    private String getPlayerTown(Player player) {
        return residentService.getResident(player.getUniqueId())
                .filter(resident -> resident.hasTown())
                .map(org.aincraft.towny.models.Resident::getTown)
                .orElse(null);
    }
}
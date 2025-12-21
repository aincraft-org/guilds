package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.outpost.Outpost;
import org.aincraft.outpost.OutpostService;
import org.aincraft.service.GuildMemberService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command handler for /g outpost subcommands.
 * Single Responsibility: Route and execute outpost-related commands.
 * Dependency Inversion: Depends on service abstraction.
 */
public class OutpostComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final OutpostService outpostService;

    @Inject
    public OutpostComponent(GuildMemberService memberService, OutpostService outpostService) {
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.outpostService = Objects.requireNonNull(outpostService, "Outpost service cannot be null");
    }

    @Override
    public String getName() {
        return "outpost";
    }

    @Override
    public String getPermission() {
        return "guilds.outpost";
    }

    @Override
    public String getUsage() {
        return "/g outpost <create|delete|list|tp|setspawn|rename>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You are not in a guild"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(player);
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "create" -> handleCreate(player, guild, args);
            case "delete" -> handleDelete(player, guild, args);
            case "list" -> handleList(player, guild);
            case "tp" -> handleTeleport(player, guild, args);
            case "setspawn" -> handleSetspawn(player, guild, args);
            case "rename" -> handleRename(player, guild, args);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    /**
     * Handles /g outpost create <name>
     */
    private boolean handleCreate(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g outpost create <name>"));
            return true;
        }

        String name = args[2];
        Location location = player.getLocation();

        OutpostService.CreateOutpostResult result = outpostService.createOutpost(
            guild.getId(), player.getUniqueId(), name, location);

        if (result.isSuccess()) {
            Outpost outpost = result.getOutpost();
            player.sendMessage(MessageFormatter.deserialize(
                "<green>✓ Outpost created: <gold>" + outpost.getName() +
                "</gold> at <aqua>" + outpost.getLocation() + "</aqua></green>"
            ));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.getMessage()));
        }

        return true;
    }

    /**
     * Handles /g outpost delete <name>
     */
    private boolean handleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g outpost delete <name>"));
            return true;
        }

        String name = args[2];

        if (outpostService.deleteOutpost(guild.getId(), player.getUniqueId(), name)) {
            player.sendMessage(MessageFormatter.deserialize(
                "<green>✓ Outpost <gold>" + name + "</gold> deleted</green>"
            ));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "Failed to delete outpost. Ensure it exists and you have permission."));
        }

        return true;
    }

    /**
     * Handles /g outpost list
     */
    private boolean handleList(Player player, Guild guild) {
        List<Outpost> outposts = outpostService.getOutposts(guild.getId());

        if (outposts.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "Your guild has no outposts"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Outposts", ""));
        for (Outpost outpost : outposts) {
            player.sendMessage(MessageFormatter.deserialize(
                "<yellow>  " + outpost.getName() + "</yellow> <gray>at " +
                outpost.getLocation() + "</gray>"
            ));
        }

        return true;
    }

    /**
     * Handles /g outpost tp <name>
     */
    private boolean handleTeleport(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g outpost tp <name>"));
            return true;
        }

        String name = args[2];
        Optional<Outpost> outpostOpt = outpostService.getOutpost(guild.getId(), name);

        if (outpostOpt.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Outpost not found"));
            return true;
        }

        Outpost outpost = outpostOpt.get();
        Location spawnLoc = outpostService.getOutpostSpawnLocation(outpost);

        if (spawnLoc == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR,
                "Outpost world is not loaded. Try again later."));
            return true;
        }

        player.teleport(spawnLoc);
        player.sendMessage(MessageFormatter.deserialize(
            "<green>✓ Teleported to <gold>" + outpost.getName() + "</gold>!</green>"
        ));

        return true;
    }

    /**
     * Handles /g outpost setspawn <name>
     */
    private boolean handleSetspawn(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g outpost setspawn <name>"));
            return true;
        }

        String name = args[2];
        Location location = player.getLocation();

        OutpostService.SetSpawnResult result = outpostService.setOutpostSpawn(
            guild.getId(), player.getUniqueId(), name, location);

        if (result.isSuccess()) {
            player.sendMessage(MessageFormatter.deserialize(
                "<green>✓ Outpost spawn updated to your location</green>"
            ));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.getMessage()));
        }

        return true;
    }

    /**
     * Handles /g outpost rename <old> <new>
     */
    private boolean handleRename(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g outpost rename <old> <new>"));
            return true;
        }

        String oldName = args[2];
        String newName = args[3];

        OutpostService.RenameResult result = outpostService.renameOutpost(
            guild.getId(), player.getUniqueId(), oldName, newName);

        if (result.isSuccess()) {
            player.sendMessage(MessageFormatter.deserialize(
                "<green>✓ Outpost renamed from <gold>" + oldName +
                "</gold> to <gold>" + newName + "</gold></green>"
            ));
        } else {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, result.getMessage()));
        }

        return true;
    }

    /**
     * Sends usage information to the player.
     */
    private void sendUsage(Player player) {
        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Outpost Commands", ""));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE,
            "/g outpost create <name>", "Create outpost at current location"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE,
            "/g outpost delete <name>", "Delete an outpost"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE,
            "/g outpost list", "List all guild outposts"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE,
            "/g outpost tp <name>", "Teleport to outpost spawn"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE,
            "/g outpost setspawn <name>", "Update outpost spawn location"));
        player.sendMessage(MessageFormatter.format(MessageFormatter.USAGE,
            "/g outpost rename <old> <new>", "Rename an outpost"));
    }
}

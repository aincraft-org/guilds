package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import dev.mintychochip.mint.Mint;
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
            Mint.sendMessage(sender, "<error>Only players can use this command</error>");
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
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
            Mint.sendMessage(player, "<error>Usage: /g outpost create <name></error>");
            return true;
        }

        String name = args[2];
        Location location = player.getLocation();

        OutpostService.CreateOutpostResult result = outpostService.createOutpost(
            guild.getId(), player.getUniqueId(), name, location);

        if (result.isSuccess()) {
            Outpost outpost = result.getOutpost();
            Mint.sendMessage(player, "<success>Outpost <secondary>" + name + "</secondary> created!</success>");
        } else {
            Mint.sendMessage(player, result.getMessage());
        }

        return true;
    }

    /**
     * Handles /g outpost delete <name>
     */
    private boolean handleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g outpost delete <name></error>");
            return true;
        }

        String name = args[2];

        if (outpostService.deleteOutpost(guild.getId(), player.getUniqueId(), name)) {
            Mint.sendMessage(player, "<success>Outpost <secondary>" + name + "</secondary> deleted</success>");
        } else {
            Mint.sendMessage(player, "<error>You don't have permission to manage outposts</error>");
        }

        return true;
    }

    /**
     * Handles /g outpost list
     */
    private boolean handleList(Player player, Guild guild) {
        List<Outpost> outposts = outpostService.getOutposts(guild.getId());

        if (outposts.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
            return true;
        }

        Mint.sendMessage(player, "<info>=== List ===</info>");
        for (Outpost outpost : outposts) {
            Mint.sendMessage(player,
                "<secondary>  " + outpost.getName() + "</secondary> <neutral>at " +
                outpost.getLocation() + "</neutral>"
            );
        }

        return true;
    }

    /**
     * Handles /g outpost tp <name>
     */
    private boolean handleTeleport(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g outpost tp <name></error>");
            return true;
        }

        String name = args[2];
        Optional<Outpost> outpostOpt = outpostService.getOutpost(guild.getId(), name);

        if (outpostOpt.isEmpty()) {
            Mint.sendMessage(player, "<error>Outpost not found: <secondary>" + name + "</secondary></error>");
            return true;
        }

        Outpost outpost = outpostOpt.get();
        Location spawnLoc = outpostService.getOutpostSpawnLocation(outpost);

        if (spawnLoc == null) {
            Mint.sendMessage(player, "<error>Outpost not found: <secondary>" + name + "</secondary></error>");
            return true;
        }

        player.teleport(spawnLoc);
        Mint.sendMessage(player, "<success>Teleported to outpost <secondary>" + outpost.getName() + "</secondary></success>");

        return true;
    }

    /**
     * Handles /g outpost setspawn <name>
     */
    private boolean handleSetspawn(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g outpost setspawn <name></error>");
            return true;
        }

        String name = args[2];
        Location location = player.getLocation();

        OutpostService.SetSpawnResult result = outpostService.setOutpostSpawn(
            guild.getId(), player.getUniqueId(), name, location);

        if (result.isSuccess()) {
            Mint.sendMessage(player, "<success>Outpost spawn set!</success>");
        } else {
            Mint.sendMessage(player, result.getMessage());
        }

        return true;
    }

    /**
     * Handles /g outpost rename <old> <new>
     */
    private boolean handleRename(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Mint.sendMessage(player, "<error>Usage: /g outpost rename <old> <new></error>");
            return true;
        }

        String oldName = args[2];
        String newName = args[3];

        OutpostService.RenameResult result = outpostService.renameOutpost(
            guild.getId(), player.getUniqueId(), oldName, newName);

        if (result.isSuccess()) {
            Mint.sendMessage(player,
                "<success>✓ Outpost renamed from <secondary>" + oldName +
                "</secondary> to <secondary>" + newName + "</secondary></success>"
            );
        } else {
            Mint.sendMessage(player, result.getMessage());
        }

        return true;
    }

    /**
     * Sends usage information to the player.
     */
    private void sendUsage(Player player) {
        Mint.sendMessage(player, "<info>Outpost Commands</info>");
        Mint.sendMessage(player,
            "<neutral>/g outpost create <name> <neutral>- Create outpost at current location</neutral></neutral>"
        );
        Mint.sendMessage(player,
            "<neutral>/g outpost delete <name> <neutral>- Delete an outpost</neutral></neutral>"
        );
        Mint.sendMessage(player,
            "<neutral>/g outpost list <neutral>- List all guild outposts</neutral></neutral>"
        );
        Mint.sendMessage(player,
            "<neutral>/g outpost tp <name> <neutral>- Teleport to outpost spawn</neutral></neutral>"
        );
        Mint.sendMessage(player,
            "<neutral>/g outpost setspawn <name> <neutral>- Update outpost spawn location</neutral></neutral>"
        );
        Mint.sendMessage(player,
            "<neutral>/g outpost rename <old> <new> <neutral>- Rename an outpost</neutral></neutral>"
        );
    }
}

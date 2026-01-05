package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.aincraft.Guild;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        // Get player's guild
        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
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
            Messages.send(player, MessageKey.ERROR_USAGE, "/g outpost create <name>");
            return true;
        }

        String name = args[2];
        Location location = player.getLocation();

        OutpostService.CreateOutpostResult result = outpostService.createOutpost(
            guild.getId(), player.getUniqueId(), name, location);

        if (result.isSuccess()) {
            Outpost outpost = result.getOutpost();
            Messages.send(player, MessageKey.OUTPOST_CREATED, name);
        } else {
            player.sendMessage(MiniMessage.miniMessage().deserialize(result.getMessage()));
        }

        return true;
    }

    /**
     * Handles /g outpost delete <name>
     */
    private boolean handleDelete(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g outpost delete <name>");
            return true;
        }

        String name = args[2];

        if (outpostService.deleteOutpost(guild.getId(), player.getUniqueId(), name)) {
            Messages.send(player, MessageKey.OUTPOST_DELETED, name);
        } else {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
        }

        return true;
    }

    /**
     * Handles /g outpost list
     */
    private boolean handleList(Player player, Guild guild) {
        List<Outpost> outposts = outpostService.getOutposts(guild.getId());

        if (outposts.isEmpty()) {
            Messages.send(player, MessageKey.LIST_EMPTY);
            return true;
        }

        player.sendMessage(Messages.get(MessageKey.LIST_HEADER, "Outposts"));
        for (Outpost outpost : outposts) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
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
            Messages.send(player, MessageKey.ERROR_USAGE, "/g outpost tp <name>");
            return true;
        }

        String name = args[2];
        Optional<Outpost> outpostOpt = outpostService.getOutpost(guild.getId(), name);

        if (outpostOpt.isEmpty()) {
            Messages.send(player, MessageKey.OUTPOST_NOT_FOUND);
            return true;
        }

        Outpost outpost = outpostOpt.get();
        Location spawnLoc = outpostService.getOutpostSpawnLocation(outpost);

        if (spawnLoc == null) {
            Messages.send(player, MessageKey.OUTPOST_NOT_FOUND);
            return true;
        }

        player.teleport(spawnLoc);
        Messages.send(player, MessageKey.OUTPOST_TELEPORTED, outpost.getName());

        return true;
    }

    /**
     * Handles /g outpost setspawn <name>
     */
    private boolean handleSetspawn(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g outpost setspawn <name>");
            return true;
        }

        String name = args[2];
        Location location = player.getLocation();

        OutpostService.SetSpawnResult result = outpostService.setOutpostSpawn(
            guild.getId(), player.getUniqueId(), name, location);

        if (result.isSuccess()) {
            Messages.send(player, MessageKey.OUTPOST_SPAWN_SET);
        } else {
            player.sendMessage(MiniMessage.miniMessage().deserialize(result.getMessage()));
        }

        return true;
    }

    /**
     * Handles /g outpost rename <old> <new>
     */
    private boolean handleRename(Player player, Guild guild, String[] args) {
        if (args.length < 4) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g outpost rename <old> <new>");
            return true;
        }

        String oldName = args[2];
        String newName = args[3];

        OutpostService.RenameResult result = outpostService.renameOutpost(
            guild.getId(), player.getUniqueId(), oldName, newName);

        if (result.isSuccess()) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<green>✓ Outpost renamed from <gold>" + oldName +
                "</gold> to <gold>" + newName + "</gold></green>"
            ));
        } else {
            player.sendMessage(MiniMessage.miniMessage().deserialize(result.getMessage()));
        }

        return true;
    }

    /**
     * Sends usage information to the player.
     */
    private void sendUsage(Player player) {
        player.sendMessage(Messages.get(MessageKey.INFO_HEADER, "Outpost Commands"));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>/g outpost create <name> <dark_gray>- Create outpost at current location</dark_gray></gray>"
        ));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>/g outpost delete <name> <dark_gray>- Delete an outpost</dark_gray></gray>"
        ));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>/g outpost list <dark_gray>- List all guild outposts</dark_gray></gray>"
        ));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>/g outpost tp <name> <dark_gray>- Teleport to outpost spawn</dark_gray></gray>"
        ));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>/g outpost setspawn <name> <dark_gray>- Update outpost spawn location</dark_gray></gray>"
        ));
        player.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>/g outpost rename <old> <new> <dark_gray>- Rename an outpost</dark_gray></gray>"
        ));
    }
}

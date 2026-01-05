package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildRelationship;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for managing guild alliances.
 */
public class AllyComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;
    private final RelationshipService relationshipService;

    @Inject
    public AllyComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService,
                        RelationshipService relationshipService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
        this.relationshipService = relationshipService;
    }

    @Override
    public String getName() {
        return "ally";
    }

    @Override
    public String getPermission() {
        return "guilds.ally";
    }

    @Override
    public String getUsage() {
        return "/g ally <guild-name|accept|reject|break|list> [guild-name]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(sender, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        if (!playerGuild.isOwner(player.getUniqueId())) {
            Messages.send(player, MessageKey.ERROR_NOT_GUILD_OWNER);
            return true;
        }

        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
            return true;
        }

        String subcommand = args[1].toLowerCase();

        return switch (subcommand) {
            case "accept" -> handleAccept(player, playerGuild, args);
            case "reject" -> handleReject(player, playerGuild, args);
            case "break" -> handleBreak(player, playerGuild, args);
            case "list" -> handleList(player, playerGuild);
            default -> {
                // Default: treat first arg as guild name for proposing alliance
                handlePropose(player, playerGuild, args);
                yield true;
            }
        };
    }

    private boolean handlePropose(Player player, Guild guild, String[] args) {
        if (args.length < 2) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g ally <guild-name>");
            return true;
        }

        String targetGuildName = args[1];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, targetGuildName);
            return true;
        }

        if (targetGuild.getId().equals(guild.getId())) {
            Messages.send(player, MessageKey.ALLY_CANNOT_SELF);
            return true;
        }

        GuildRelationship relationship = relationshipService.proposeAlliance(
            guild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (relationship == null) {
            Messages.send(player, MessageKey.ALLY_ALREADY_EXISTS);
            return true;
        }

        Messages.send(player, MessageKey.ALLY_REQUEST_SENT, targetGuild.getName());

        // Notify all online members of the target guild
        notifyTargetGuildMembers(targetGuild, guild);

        return true;
    }

    private boolean handleAccept(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g ally accept <guild-name>");
            return true;
        }

        String sourceGuildName = args[2];
        Guild sourceGuild = lifecycleService.getGuildByName(sourceGuildName);

        if (sourceGuild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, sourceGuildName);
            return true;
        }

        boolean accepted = relationshipService.acceptAlliance(
            guild.getId(), sourceGuild.getId(), player.getUniqueId()
        );

        if (!accepted) {
            Messages.send(player, MessageKey.ALLY_NO_PENDING);
            return true;
        }

        Messages.send(player, MessageKey.ALLY_ACCEPTED, sourceGuild.getName());
        return true;
    }

    private boolean handleReject(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g ally reject <guild-name>");
            return true;
        }

        String sourceGuildName = args[2];
        Guild sourceGuild = lifecycleService.getGuildByName(sourceGuildName);

        if (sourceGuild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, sourceGuildName);
            return true;
        }

        boolean rejected = relationshipService.rejectAlliance(
            guild.getId(), sourceGuild.getId(), player.getUniqueId()
        );

        if (!rejected) {
            Messages.send(player, MessageKey.ALLY_NO_PENDING);
            return true;
        }

        Messages.send(player, MessageKey.ALLY_REJECTED, sourceGuild.getName());
        return true;
    }

    private boolean handleBreak(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g ally break <guild-name>");
            return true;
        }

        String allyGuildName = args[2];
        Guild allyGuild = lifecycleService.getGuildByName(allyGuildName);

        if (allyGuild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, allyGuildName);
            return true;
        }

        boolean broken = relationshipService.breakAlliance(
            guild.getId(), allyGuild.getId(), player.getUniqueId()
        );

        if (!broken) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        Messages.send(player, MessageKey.ALLY_BROKEN, allyGuild.getName());
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<UUID> allies = relationshipService.getAllies(guild.getId());
        List<GuildRelationship> pendingRequests = relationshipService.getPendingAllyRequests(guild.getId());
        List<GuildRelationship> sentRequests = relationshipService.getSentAllyRequests(guild.getId());

        Messages.send(player, MessageKey.ALLY_LIST_HEADER);

        if (allies.isEmpty()) {
            Messages.send(player, MessageKey.ALLY_LIST_EMPTY);
        } else {
            player.sendMessage(Messages.get(MessageKey.ALLY_LIST_HEADER));
            for (UUID allyId : allies) {
                Guild ally = lifecycleService.getGuildById(allyId);
                if (ally != null) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("  <green>•</green> <white>" + ally.getName()));
                }
            }
        }

        if (!pendingRequests.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<yellow>Incoming Requests<reset>:"));
            for (GuildRelationship request : pendingRequests) {
                Guild source = lifecycleService.getGuildById(request.getSourceGuildId());
                if (source != null) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("  <aqua>↓</aqua> <white>" + source.getName()));
                }
            }
        }

        if (!sentRequests.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<yellow>Sent Requests<reset>:"));
            for (GuildRelationship request : sentRequests) {
                Guild target = lifecycleService.getGuildById(request.getTargetGuildId());
                if (target != null) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("  <gray>↑</gray> <white>" + target.getName()));
                }
            }
        }

        return true;
    }

    /**
     * Notifies all online members of the target guild about the alliance proposal.
     *
     * @param targetGuild the guild receiving the alliance proposal
     * @param sourceGuild the guild sending the alliance proposal
     */
    private void notifyTargetGuildMembers(Guild targetGuild, Guild sourceGuild) {
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> {
                Guild playerGuild = memberService.getPlayerGuild(p.getUniqueId());
                return playerGuild != null && playerGuild.getId().equals(targetGuild.getId());
            })
            .forEach(p -> p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(
                    "<aqua>✉ Alliance request from <gold>" + sourceGuild.getName() +
                    "</gold>. Use <white>/g ally accept " + sourceGuild.getName() + "</white> to accept.</aqua>"
                )));
    }
}

package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.Guild;
import org.aincraft.GuildRelationship;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to manage alliances"));
            return true;
        }

        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You must be in a guild to manage alliances"));
            return true;
        }

        if (!playerGuild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can manage alliances"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
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
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g ally <guild-name>"));
            return true;
        }

        String targetGuildName = args[1];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + targetGuildName + "' not found"));
            return true;
        }

        if (targetGuild.getId().equals(guild.getId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You cannot ally with your own guild"));
            return true;
        }

        GuildRelationship relationship = relationshipService.proposeAlliance(
            guild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (relationship == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to propose alliance. You may already have a relationship or hit the ally limit"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
            "✓ Alliance proposal sent to '" + targetGuild.getName() + "'"));
        return true;
    }

    private boolean handleAccept(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g ally accept <guild-name>"));
            return true;
        }

        String sourceGuildName = args[2];
        Guild sourceGuild = lifecycleService.getGuildByName(sourceGuildName);

        if (sourceGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + sourceGuildName + "' not found"));
            return true;
        }

        boolean accepted = relationshipService.acceptAlliance(
            guild.getId(), sourceGuild.getId(), player.getUniqueId()
        );

        if (!accepted) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ No pending alliance request from '" + sourceGuildName + "'"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
            "✓ Alliance with '" + sourceGuild.getName() + "' accepted"));
        return true;
    }

    private boolean handleReject(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g ally reject <guild-name>"));
            return true;
        }

        String sourceGuildName = args[2];
        Guild sourceGuild = lifecycleService.getGuildByName(sourceGuildName);

        if (sourceGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + sourceGuildName + "' not found"));
            return true;
        }

        boolean rejected = relationshipService.rejectAlliance(
            guild.getId(), sourceGuild.getId(), player.getUniqueId()
        );

        if (!rejected) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ No pending alliance request from '" + sourceGuildName + "'"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
            "✓ Alliance request from '" + sourceGuild.getName() + "' rejected"));
        return true;
    }

    private boolean handleBreak(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g ally break <guild-name>"));
            return true;
        }

        String allyGuildName = args[2];
        Guild allyGuild = lifecycleService.getGuildByName(allyGuildName);

        if (allyGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + allyGuildName + "' not found"));
            return true;
        }

        boolean broken = relationshipService.breakAlliance(
            guild.getId(), allyGuild.getId(), player.getUniqueId()
        );

        if (!broken) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ No active alliance with '" + allyGuildName + "'"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
            "✓ Alliance with '" + allyGuild.getName() + "' broken"));
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<String> allies = relationshipService.getAllies(guild.getId());
        List<GuildRelationship> pendingRequests = relationshipService.getPendingAllyRequests(guild.getId());
        List<GuildRelationship> sentRequests = relationshipService.getSentAllyRequests(guild.getId());

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Alliance Status", ""));

        if (allies.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "No active allies"));
        } else {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Active Allies<reset>:"));
            for (String allyId : allies) {
                Guild ally = lifecycleService.getGuildById(allyId);
                if (ally != null) {
                    player.sendMessage(MessageFormatter.deserialize("  <green>•</green> <white>" + ally.getName()));
                }
            }
        }

        if (!pendingRequests.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Incoming Requests<reset>:"));
            for (GuildRelationship request : pendingRequests) {
                Guild source = lifecycleService.getGuildById(request.getSourceGuildId());
                if (source != null) {
                    player.sendMessage(MessageFormatter.deserialize("  <aqua>↓</aqua> <white>" + source.getName()));
                }
            }
        }

        if (!sentRequests.isEmpty()) {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Sent Requests<reset>:"));
            for (GuildRelationship request : sentRequests) {
                Guild target = lifecycleService.getGuildById(request.getTargetGuildId());
                if (target != null) {
                    player.sendMessage(MessageFormatter.deserialize("  <gray>↑</gray> <white>" + target.getName()));
                }
            }
        }

        return true;
    }
}

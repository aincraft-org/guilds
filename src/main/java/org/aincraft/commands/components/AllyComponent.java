package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.util.List;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.GuildRelationship;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(sender, "<error>You don't have permission to use this command.</error>");
            return true;
        }

        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild.</error>");
            return true;
        }

        if (!playerGuild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>Only the guild owner can use this command.</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
            return true;
        }

        String subcommand = args[1].toLowerCase();

        return switch (subcommand) {
            case "accept" -> handleAccept(player, playerGuild, args);
            case "reject" -> handleReject(player, playerGuild, args);
            case "break" -> handleBreak(player, playerGuild, args);
            case "list" -> handleList(player, playerGuild);
            default -> {
                handlePropose(player, playerGuild, args);
                yield true;
            }
        };
    }

    private boolean handlePropose(Player player, Guild guild, String[] args) {
        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: /g ally <guild-name></error>");
            return true;
        }

        String targetGuildName = args[1];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            Mint.sendMessage(player, "<error>Guild '" + targetGuildName + "' not found.</error>");
            return true;
        }

        if (targetGuild.getId().equals(guild.getId())) {
            Mint.sendMessage(player, "<error>You cannot ally with your own guild.</error>");
            return true;
        }

        GuildRelationship relationship = relationshipService.proposeAlliance(
            guild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (relationship == null) {
            Mint.sendMessage(player, "<error>Alliance already exists or pending.</error>");
            return true;
        }

        Mint.sendMessage(player, "<success>Ally request sent to <secondary>" + targetGuild.getName() + "</secondary>.</success>");

        notifyTargetGuildMembers(targetGuild, guild);

        return true;
    }

    private boolean handleAccept(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g ally accept <guild-name></error>");
            return true;
        }

        String sourceGuildName = args[2];
        Guild sourceGuild = lifecycleService.getGuildByName(sourceGuildName);

        if (sourceGuild == null) {
            Mint.sendMessage(player, "<error>Guild '" + sourceGuildName + "' not found.</error>");
            return true;
        }

        boolean accepted = relationshipService.acceptAlliance(
            guild.getId(), sourceGuild.getId(), player.getUniqueId()
        );

        if (!accepted) {
            Mint.sendMessage(player, "<error>No pending alliance request from that guild.</error>");
            return true;
        }

        Mint.sendMessage(player, "<success>Accepted alliance with <secondary>" + sourceGuild.getName() + "</secondary>.</success>");
        return true;
    }

    private boolean handleReject(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g ally reject <guild-name></error>");
            return true;
        }

        String sourceGuildName = args[2];
        Guild sourceGuild = lifecycleService.getGuildByName(sourceGuildName);

        if (sourceGuild == null) {
            Mint.sendMessage(player, "<error>Guild '" + sourceGuildName + "' not found.</error>");
            return true;
        }

        boolean rejected = relationshipService.rejectAlliance(
            guild.getId(), sourceGuild.getId(), player.getUniqueId()
        );

        if (!rejected) {
            Mint.sendMessage(player, "<error>No pending alliance request from that guild.</error>");
            return true;
        }

        Mint.sendMessage(player, "<neutral>Rejected alliance request from <secondary>" + sourceGuild.getName() + "</secondary>.</neutral>");
        return true;
    }

    private boolean handleBreak(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g ally break <guild-name></error>");
            return true;
        }

        String allyGuildName = args[2];
        Guild allyGuild = lifecycleService.getGuildByName(allyGuildName);

        if (allyGuild == null) {
            Mint.sendMessage(player, "<error>Guild '" + allyGuildName + "' not found.</error>");
            return true;
        }

        boolean broken = relationshipService.breakAlliance(
            guild.getId(), allyGuild.getId(), player.getUniqueId()
        );

        if (!broken) {
            Mint.sendMessage(player, "<error>You are not in a guild.</error>");
            return true;
        }

        Mint.sendMessage(player, "<warning>Broke alliance with <secondary>" + allyGuild.getName() + "</secondary>.</warning>");
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<UUID> allies = relationshipService.getAllies(guild.getId());
        List<GuildRelationship> pendingRequests = relationshipService.getPendingAllyRequests(guild.getId());
        List<GuildRelationship> sentRequests = relationshipService.getSentAllyRequests(guild.getId());

        Mint.sendMessage(player, "<secondary>=== Allies ===</secondary>");

        if (allies.isEmpty()) {
            Mint.sendMessage(player, "<neutral>No allies found.</neutral>");
        } else {
            for (UUID allyId : allies) {
                Guild ally = lifecycleService.getGuildById(allyId);
                if (ally != null) {
                    Mint.sendMessage(player, "  <success>•</success> <primary>" + ally.getName() + "</primary>");
                }
            }
        }

        if (!pendingRequests.isEmpty()) {
            Mint.sendMessage(player, "<warning>Incoming Requests:");
            for (GuildRelationship request : pendingRequests) {
                Guild source = lifecycleService.getGuildById(request.getSourceGuildId());
                if (source != null) {
                    Mint.sendMessage(player, "  <info>↓</info> <primary>" + source.getName() + "</primary>");
                }
            }
        }

        if (!sentRequests.isEmpty()) {
            Mint.sendMessage(player, "<warning>Sent Requests:");
            for (GuildRelationship request : sentRequests) {
                Guild target = lifecycleService.getGuildById(request.getTargetGuildId());
                if (target != null) {
                    Mint.sendMessage(player, "  <neutral>↑</neutral> <primary>" + target.getName() + "</primary>");
                }
            }
        }

        return true;
    }

    private void notifyTargetGuildMembers(Guild targetGuild, Guild sourceGuild) {
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> {
                Guild playerGuild = memberService.getPlayerGuild(p.getUniqueId());
                return playerGuild != null && playerGuild.getId().equals(targetGuild.getId());
            })
            .forEach(p -> Mint.sendMessage(p,
                "<info>✉ Alliance request from <secondary>" + sourceGuild.getName() +
                "</secondary>. Use <primary>/g ally accept " + sourceGuild.getName() + "</primary> to accept.</info>"
            ));
    }
}

package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import java.util.List;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.service.GuildLifecycleService;
import org.aincraft.service.GuildMemberService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for managing guild enemies.
 */
public class EnemyComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;
    private final RelationshipService relationshipService;

    @Inject
    public EnemyComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService,
                         RelationshipService relationshipService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
        this.relationshipService = relationshipService;
    }

    @Override
    public String getName() {
        return "enemy";
    }

    @Override
    public String getPermission() {
        return "guilds.enemy";
    }

    @Override
    public String getUsage() {
        return "/g enemy <declare|list> [guild-name]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Mint.sendMessage(sender, "<error>This command can only be used by players.</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(sender, "<error>You don't have <accent>permission</accent> to use this command.</error>");
            return true;
        }

        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild.</error>");
            return true;
        }

        if (!playerGuild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>Only the <secondary>guild owner</secondary> can use this command.</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
            return true;
        }

        String subcommand = args[1].toLowerCase();

        return switch (subcommand) {
            case "declare" -> handleDeclare(player, playerGuild, args);
            case "list" -> handleList(player, playerGuild);
            default -> {
                Mint.sendMessage(player, "<error>Usage: " + getUsage() + "</error>");
                yield true;
            }
        };
    }

    private boolean handleDeclare(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Mint.sendMessage(player, "<error>Usage: /g enemy declare <guild-name></error>");
            return true;
        }

        String targetGuildName = args[2];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            Mint.sendMessage(player, "<error>Guild '" + targetGuildName + "' not found.</error>");
            return true;
        }

        if (targetGuild.getId().equals(guild.getId())) {
            Mint.sendMessage(player, "<error>You cannot declare war on your own guild.</error>");
            return true;
        }

        var relationship = relationshipService.declareEnemy(
            guild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (relationship == null) {
            Mint.sendMessage(player, "<error>Enemy relationship already exists.</error>");
            return true;
        }

        Mint.sendMessage(player, "<warning>Declared war on <secondary>" + targetGuild.getName() + "</secondary>.</warning>");
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<UUID> enemies = relationshipService.getEnemies(guild.getId());

        Mint.sendMessage(player, "<secondary>=== <accent>Enemies</accent> ===</secondary>");

        if (enemies.isEmpty()) {
            Mint.sendMessage(player, "<neutral>List is empty</neutral>");
        } else {
            Mint.sendMessage(player, "<warning><accent>Enemies</accent>:");
            for (UUID enemyId : enemies) {
                Guild enemy = lifecycleService.getGuildById(enemyId);
                if (enemy != null) {
                    Mint.sendMessage(player, "  <error>⚔</error> <primary>" + enemy.getName() + "</primary>");
                }
            }
        }

        return true;
    }
}

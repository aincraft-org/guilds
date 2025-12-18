package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import org.aincraft.Guild;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to manage enemies"));
            return true;
        }

        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You must be in a guild to manage enemies"));
            return true;
        }

        if (!playerGuild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can manage enemies"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return true;
        }

        String subcommand = args[1].toLowerCase();

        return switch (subcommand) {
            case "declare" -> handleDeclare(player, playerGuild, args);
            case "list" -> handleList(player, playerGuild);
            default -> {
                player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Unknown subcommand. " + getUsage()));
                yield true;
            }
        };
    }

    private boolean handleDeclare(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: /g enemy declare <guild-name>"));
            return true;
        }

        String targetGuildName = args[2];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + targetGuildName + "' not found"));
            return true;
        }

        if (targetGuild.getId().equals(guild.getId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You cannot declare war on your own guild"));
            return true;
        }

        var relationship = relationshipService.declareEnemy(
            guild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (relationship == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Failed to declare enemy. You may be allied with this guild"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
            "✓ '" + targetGuild.getName() + "' declared as enemy"));
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<String> enemies = relationshipService.getEnemies(guild.getId());

        player.sendMessage(MessageFormatter.format(MessageFormatter.HEADER, "Enemy Guilds", ""));

        if (enemies.isEmpty()) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.INFO, "No active enemies"));
        } else {
            player.sendMessage(MessageFormatter.deserialize("<yellow>Enemies<reset>:"));
            for (String enemyId : enemies) {
                Guild enemy = lifecycleService.getGuildById(enemyId);
                if (enemy != null) {
                    player.sendMessage(MessageFormatter.deserialize("  <red>⚔</red> <white>" + enemy.getName()));
                }
            }
        }

        return true;
    }
}

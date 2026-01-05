package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.aincraft.Guild;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
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
            case "declare" -> handleDeclare(player, playerGuild, args);
            case "list" -> handleList(player, playerGuild);
            default -> {
                Messages.send(player, MessageKey.ERROR_USAGE, getUsage());
                yield true;
            }
        };
    }

    private boolean handleDeclare(Player player, Guild guild, String[] args) {
        if (args.length < 3) {
            Messages.send(player, MessageKey.ERROR_USAGE, "/g enemy declare <guild-name>");
            return true;
        }

        String targetGuildName = args[2];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, targetGuildName);
            return true;
        }

        if (targetGuild.getId().equals(guild.getId())) {
            Messages.send(player, MessageKey.ALLY_CANNOT_SELF);
            return true;
        }

        var relationship = relationshipService.declareEnemy(
            guild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (relationship == null) {
            Messages.send(player, MessageKey.ENEMY_ALREADY_EXISTS);
            return true;
        }

        Messages.send(player, MessageKey.ENEMY_DECLARED, targetGuild.getName());
        return true;
    }

    private boolean handleList(Player player, Guild guild) {
        List<UUID> enemies = relationshipService.getEnemies(guild.getId());

        Messages.send(player, MessageKey.ENEMY_DECLARED);

        if (enemies.isEmpty()) {
            Messages.send(player, MessageKey.LIST_EMPTY);
        } else {
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<yellow>Enemies<reset>:"));
            for (UUID enemyId : enemies) {
                Guild enemy = lifecycleService.getGuildById(enemyId);
                if (enemy != null) {
                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .deserialize("  <red>⚔</red> <white>" + enemy.getName()));
                }
            }
        }

        return true;
    }
}

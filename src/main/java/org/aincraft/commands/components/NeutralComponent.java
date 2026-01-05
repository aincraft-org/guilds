package org.aincraft.commands.components;

import com.google.inject.Inject;
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
 * Component for declaring neutral status with guilds.
 */
public class NeutralComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final GuildLifecycleService lifecycleService;
    private final RelationshipService relationshipService;

    @Inject
    public NeutralComponent(GuildMemberService memberService, GuildLifecycleService lifecycleService,
                           RelationshipService relationshipService) {
        this.memberService = memberService;
        this.lifecycleService = lifecycleService;
        this.relationshipService = relationshipService;
    }

    @Override
    public String getName() {
        return "neutral";
    }

    @Override
    public String getPermission() {
        return "guilds.neutral";
    }

    @Override
    public String getUsage() {
        return "/g neutral <guild-name>";
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

        String targetGuildName = args[1];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            Messages.send(player, MessageKey.ERROR_GUILD_NOT_FOUND, targetGuildName);
            return true;
        }

        if (targetGuild.getId().equals(playerGuild.getId())) {
            Messages.send(player, MessageKey.ALLY_CANNOT_SELF);
            return true;
        }

        boolean success = relationshipService.declareNeutral(
            playerGuild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (!success) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        Messages.send(player, MessageKey.NEUTRAL_SET, targetGuild.getName());
        return true;
    }
}

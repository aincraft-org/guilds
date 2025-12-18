package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
import org.aincraft.commands.MessageFormatter;
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
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Only players can use this command"));
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            sender.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "You don't have permission to declare neutral"));
            return true;
        }

        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You must be in a guild to declare neutral"));
            return true;
        }

        if (!playerGuild.isOwner(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Only the guild owner can declare neutral"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "Usage: " + getUsage()));
            return true;
        }

        String targetGuildName = args[1];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ Guild '" + targetGuildName + "' not found"));
            return true;
        }

        if (targetGuild.getId().equals(playerGuild.getId())) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ You cannot declare neutral with your own guild"));
            return true;
        }

        boolean success = relationshipService.declareNeutral(
            playerGuild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (!success) {
            player.sendMessage(MessageFormatter.format(MessageFormatter.ERROR, "✗ No active relationship with '" + targetGuildName + "'"));
            return true;
        }

        player.sendMessage(MessageFormatter.format(MessageFormatter.SUCCESS,
            "✓ Declared neutral with '" + targetGuild.getName() + "'"));
        return true;
    }
}

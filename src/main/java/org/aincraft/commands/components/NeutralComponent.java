package org.aincraft.commands.components;

import com.google.inject.Inject;
import dev.mintychochip.mint.Mint;
import org.aincraft.Guild;
import org.aincraft.RelationshipService;
import org.aincraft.commands.GuildCommand;
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
            Mint.sendMessage(sender, "<error>This command can only be used by players</error>");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Mint.sendMessage(sender, "<error>You don't have permission to use this command</error>");
            return true;
        }

        Guild playerGuild = memberService.getPlayerGuild(player.getUniqueId());
        if (playerGuild == null) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        if (!playerGuild.isOwner(player.getUniqueId())) {
            Mint.sendMessage(player, "<error>Only the guild owner can use this command</error>");
            return true;
        }

        if (args.length < 2) {
            Mint.sendMessage(player, "<error>Usage: <accent>" + getUsage() + "</accent></error>");
            return true;
        }

        String targetGuildName = args[1];
        Guild targetGuild = lifecycleService.getGuildByName(targetGuildName);

        if (targetGuild == null) {
            Mint.sendMessage(player, "<error>Guild not found: <secondary>" + targetGuildName + "</secondary></error>");
            return true;
        }

        if (targetGuild.getId().equals(playerGuild.getId())) {
            Mint.sendMessage(player, "<error>You cannot perform this action on your own guild</error>");
            return true;
        }

        boolean success = relationshipService.declareNeutral(
            playerGuild.getId(), targetGuild.getId(), player.getUniqueId()
        );

        if (!success) {
            Mint.sendMessage(player, "<error>You are not in a guild</error>");
            return true;
        }

        Mint.sendMessage(player, "<success>Neutral status set with guild <secondary>" + targetGuild.getName() + "</secondary></success>");
        return true;
    }
}

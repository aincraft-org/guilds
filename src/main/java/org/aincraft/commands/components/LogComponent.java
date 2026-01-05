package org.aincraft.commands.components;

import com.google.inject.Inject;
import java.util.Objects;
import org.aincraft.GuildPermission;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.progression.storage.ProgressionLogRepository;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.aincraft.service.TerritoryService;
import org.aincraft.vault.VaultService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Parent coordinator for /g log commands.
 * Routes to claim, vault, or progression subcomponents and enforces VIEW_LOGS permission.
 */
public class LogComponent {
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final ClaimLogSubcomponent claimLogSubcomponent;
    private final VaultLogSubcomponent vaultLogSubcomponent;
    private final ProgressionLogSubcomponent progressionLogSubcomponent;

    @Inject
    public LogComponent(GuildMemberService memberService, PermissionService permissionService,
                       TerritoryService territoryService, VaultService vaultService, ProgressionLogRepository progressionLogRepository) {
        this.memberService = Objects.requireNonNull(memberService, "Member service cannot be null");
        this.permissionService = Objects.requireNonNull(permissionService, "Permission service cannot be null");
        this.claimLogSubcomponent = new ClaimLogSubcomponent(memberService, territoryService);
        this.vaultLogSubcomponent = new VaultLogSubcomponent(vaultService);
        this.progressionLogSubcomponent = new ProgressionLogSubcomponent(memberService, progressionLogRepository);
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (args.length < 2) {
            showHelp(player);
            return true;
        }

        // Check if player is in a guild
        org.aincraft.Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check VIEW_LOGS permission
        if (!permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.VIEW_LOGS)) {
            Messages.send(player, MessageKey.ERROR_NO_PERMISSION);
            return true;
        }

        String subCommand = args[1].toLowerCase();

        return switch (subCommand) {
            case "claim" -> claimLogSubcomponent.execute(player, args);
            case "vault" -> vaultLogSubcomponent.execute(player, args);
            case "progression", "xp" -> progressionLogSubcomponent.execute(player, args);
            default -> {
                showHelp(player);
                yield true;
            }
        };
    }

    private void showHelp(Player player) {
        Messages.send(player, MessageKey.LIST_HEADER);
    }
}

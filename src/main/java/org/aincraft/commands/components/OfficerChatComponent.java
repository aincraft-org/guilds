package org.aincraft.commands.components;

import com.google.inject.Inject;
import org.aincraft.Guild;
import org.aincraft.GuildPermission;
import org.aincraft.chat.ChatModeService;
import org.aincraft.chat.ChatModeService.ChatMode;
import org.aincraft.commands.GuildCommand;
import org.aincraft.messages.MessageKey;
import org.aincraft.messages.Messages;
import org.aincraft.service.GuildMemberService;
import org.aincraft.service.PermissionService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Component for officer chat command (/oc).
 * No args: Toggle officer chat mode.
 * With args: Send one-time message to officer chat.
 *
 * Officers are guild members with CHAT_OFFICER permission.
 * Only officers can send and receive officer-level messages.
 */
public class OfficerChatComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final ChatModeService chatModeService;

    @Inject
    public OfficerChatComponent(GuildMemberService memberService, PermissionService permissionService,
                               ChatModeService chatModeService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
        this.chatModeService = chatModeService;
    }

    @Override
    public String getName() {
        return "oc";
    }

    @Override
    public String getPermission() {
        return "guilds.chat";
    }

    @Override
    public String getUsage() {
        return "/oc [message]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.CHAT_NO_PERMISSION);
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check CHAT_OFFICER permission (owners bypass)
        if (!guild.isOwner(player.getUniqueId()) &&
            !permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.CHAT_OFFICER)) {
            Messages.send(player, MessageKey.CHAT_NO_PERMISSION);
            return true;
        }

        // No message - toggle mode
        if (args.length == 0) {
            ChatMode newMode = chatModeService.toggleMode(player.getUniqueId(), ChatMode.OFFICER);

            if (newMode == ChatMode.OFFICER) {
                Messages.send(player, MessageKey.CHAT_OFFICER_ENABLED);
            } else {
                Messages.send(player, MessageKey.CHAT_OFFICER_DISABLED);
            }
            return true;
        }

        // Has message - send one-time message without toggling
        String message = String.join(" ", args);

        // Temporarily set mode to OFFICER
        ChatMode originalMode = chatModeService.getMode(player.getUniqueId());
        chatModeService.setMode(player.getUniqueId(), ChatMode.OFFICER);

        // Trigger chat event
        player.chat(message);

        // Restore original mode
        chatModeService.setMode(player.getUniqueId(), originalMode);

        return true;
    }
}

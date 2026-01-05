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
 * Component for guild chat command (/gc).
 * No args: Toggle guild chat mode.
 * With args: Send one-time message to guild chat.
 */
public class GuildChatComponent implements GuildCommand {
    private final GuildMemberService memberService;
    private final PermissionService permissionService;
    private final ChatModeService chatModeService;

    @Inject
    public GuildChatComponent(GuildMemberService memberService, PermissionService permissionService,
                             ChatModeService chatModeService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
        this.chatModeService = chatModeService;
    }

    @Override
    public String getName() {
        return "gc";
    }

    @Override
    public String getPermission() {
        return "guilds.chat";
    }

    @Override
    public String getUsage() {
        return "/gc [message]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, MessageKey.ERROR_PLAYER_ONLY);
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            Messages.send(player, MessageKey.CHAT_NO_PERMISSION, "guild");
            return true;
        }

        Guild guild = memberService.getPlayerGuild(player.getUniqueId());
        if (guild == null) {
            Messages.send(player, MessageKey.ERROR_NOT_IN_GUILD);
            return true;
        }

        // Check CHAT_GUILD permission (owners bypass)
        if (!guild.isOwner(player.getUniqueId()) &&
            !permissionService.hasPermission(guild.getId(), player.getUniqueId(), GuildPermission.CHAT_GUILD)) {
            Messages.send(player, MessageKey.CHAT_NO_PERMISSION, "guild");
            return true;
        }

        // No message - toggle mode
        if (args.length == 0) {
            ChatMode newMode = chatModeService.toggleMode(player.getUniqueId(), ChatMode.GUILD);

            if (newMode == ChatMode.GUILD) {
                Messages.send(player, MessageKey.CHAT_GUILD_ENABLED);
            } else {
                Messages.send(player, MessageKey.CHAT_GUILD_DISABLED);
            }
            return true;
        }

        // Has message - send one-time message without toggling
        // The message will be handled by GuildChatListener if mode is set,
        // but for one-time messages we need to temporarily set mode, let player send, then restore
        // Actually, simpler approach: just simulate the chat by directly calling the listener logic
        // But we don't have direct access. Better: temporarily set mode, player will type next message.
        // Wait, args contains the message! We can send it directly here.

        // Build message from args
        String message = String.join(" ", args);

        // Temporarily set mode to GUILD
        ChatMode originalMode = chatModeService.getMode(player.getUniqueId());
        chatModeService.setMode(player.getUniqueId(), ChatMode.GUILD);

        // Trigger a chat event by having the player send the message
        // Actually, we can't trigger AsyncChatEvent from here.
        // Better approach: Send the message directly using the same logic as the listener

        // For now, just inform the player to type their message
        // OR we implement the send logic here directly

        // Let's implement direct send for one-time messages:
        player.chat(message);

        // Restore original mode
        chatModeService.setMode(player.getUniqueId(), originalMode);

        return true;
    }
}
